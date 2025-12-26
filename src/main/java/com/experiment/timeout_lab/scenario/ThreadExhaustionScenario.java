package com.experiment.timeout_lab.scenario;

import com.experiment.timeout_lab.client.TimeoutClient;
import com.experiment.timeout_lab.server.ProblematicServer;
import com.experiment.timeout_lab.util.Constants.ServerMode;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread Pool Exhaustion 시나리오 (스레드풀 고갈 실험)
 *
 * 핵심 실험 내용:
 * - 타임아웃 미설정 시 스레드풀의 모든 스레드가 블로킹되는 현상 재현
 * - 타임아웃 설정으로 스레드풀 고갈을 방지할 수 있음을 입증
 *
 * 실제 서비스 장애 시나리오:
 * 1. 외부 서비스가 느려지거나 응답하지 않음
 * 2. 타임아웃이 없으면 스레드들이 무한 대기
 * 3. 모든 스레드가 블로킹되면 새 요청을 처리할 수 없음
 * 4. 서비스 전체가 멈춤 (Cascading Failure)
 */
public class ThreadExhaustionScenario extends BaseScenario {

  private ProblematicServer noAcceptServer;
  private final int serverPort = 8086;

  // 스레드풀 설정
  private final int THREAD_POOL_SIZE = 10;    // 작은 풀로 고갈 현상을 쉽게 관찰
  private final int TOTAL_REQUESTS = 50;      // 풀 크기의 5배 요청

  private enum TestConfig {
    NO_TIMEOUT("타임아웃 미설정", 0),
    WITH_TIMEOUT("타임아웃 3초", 3000);

    final String description;
    final int timeout;

    TestConfig(String description, int timeout) {
      this.description = description;
      this.timeout = timeout;
    }
  }

  private final List<ThreadPoolTestResult> testResults = new ArrayList<>();
  private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

  public ThreadExhaustionScenario() {
    super("Thread Pool Exhaustion Scenario",
        "타임아웃 미설정 시 스레드풀 고갈 현상 실험");
    setIterations(2);  // 타임아웃 있음/없음 2가지만 테스트
    setWarmupIterations(0);
  }

  @Override
  protected void setup() throws Exception {
    logger.info("NO_ACCEPT 서버 시작 중...");
    noAcceptServer = new ProblematicServer(serverPort, ServerMode.NO_ACCEPT);
    noAcceptServer.start();
    Thread.sleep(1000);
    logger.info("서버 준비 완료 (Port: " + serverPort + ")");
  }

  @Override
  protected boolean runScenario(int iteration) throws Exception {
    // 짝수: 타임아웃 없음, 홀수: 타임아웃 있음
    TestConfig config = (iteration % 2 == 0) ?
        TestConfig.NO_TIMEOUT : TestConfig.WITH_TIMEOUT;

    logger.info("\n" + "=".repeat(60));
    logger.info("🔄 테스트 " + (iteration + 1) + ": " + config.description);
    logger.info("  • 스레드풀 크기: " + THREAD_POOL_SIZE);
    logger.info("  • 총 요청 수: " + TOTAL_REQUESTS);
    logger.info("  • Connect Timeout: " +
        (config.timeout == 0 ? "없음 ⚠️" : config.timeout + "ms"));
    logger.info("=".repeat(60));

    // 커스텀 스레드풀 생성
    ExecutorService executor = new ThreadPoolExecutor(
        THREAD_POOL_SIZE,
        THREAD_POOL_SIZE,
        0L,
        TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(TOTAL_REQUESTS),
        new ThreadFactory() {
          private final AtomicInteger counter = new AtomicInteger(0);
          @Override
          public Thread newThread(Runnable r) {
            return new Thread(r,
                "Worker-" + config.name() + "-" + counter.incrementAndGet());
          }
        }
    );

    // 모니터링 스레드
    ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();

    try {
      ThreadPoolTestResult result = new ThreadPoolTestResult(config);

      // 상태 추적 변수
      AtomicInteger activeThreads = new AtomicInteger(0);
      AtomicInteger blockedThreads = new AtomicInteger(0);
      AtomicInteger completedTasks = new AtomicInteger(0);

      // 1초마다 스레드풀 상태 모니터링
      ScheduledFuture<?> monitorTask = monitor.scheduleAtFixedRate(() -> {
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;
        int active = tpe.getActiveCount();
        int queued = tpe.getQueue().size();
        long completed = tpe.getCompletedTaskCount();

        activeThreads.set(active);
        completedTasks.set((int) completed);

        int blocked = countBlockedThreads();
        blockedThreads.set(blocked);

        logger.debug(String.format(
            "📊 상태 - 활성: %d/%d, 대기큐: %d, 완료: %d, 블로킹: %d",
            active, THREAD_POOL_SIZE, queued, completed, blocked
        ));
      }, 0, 1, TimeUnit.SECONDS);

      // 요청 생성 및 제출
      List<Future<ClientResult>> futures = new ArrayList<>();
      CountDownLatch startLatch = new CountDownLatch(1);

      logger.info("📤 " + TOTAL_REQUESTS + "개 요청 제출 중...");

      for (int i = 0; i < TOTAL_REQUESTS; i++) {
        final int requestId = i;
        Future<ClientResult> future = executor.submit(() ->
            executeClientRequest(requestId, config, startLatch)
        );
        futures.add(future);
      }

      // 모든 요청 동시 시작
      logger.info("🚀 모든 요청 동시 실행!");
      startLatch.countDown();

      // 결과 수집
      if (config.timeout == 0) {
        // 타임아웃 없음: 30초 후 강제 종료
        logger.info("⏳ 30초 대기 중... (타임아웃 미설정으로 무한 대기 예상)");

        boolean completed = executor.awaitTermination(30, TimeUnit.SECONDS);

        if (!completed) {
          logger.warn("⚠️ 30초 경과 - 스레드풀이 고갈된 상태!");
          logger.warn("  • 활성 스레드: " + activeThreads.get() +
              "/" + THREAD_POOL_SIZE + " (모두 블로킹)");
          logger.warn("  • 블로킹된 스레드: " + blockedThreads.get());
          logger.warn("  • 완료된 작업: " + completedTasks.get() +
              "/" + TOTAL_REQUESTS + " (처리 불가)");

          result.threadPoolExhausted = true;
          result.completedRequests = completedTasks.get();
          result.blockedThreads = blockedThreads.get();

          // 강제 종료
          executor.shutdownNow();
          logger.info("🔴 스레드풀 강제 종료");
        }
      } else {
        // 타임아웃 있음: 정상 완료 대기
        logger.info("⏳ 요청 완료 대기 중...");

        executor.shutdown();
        boolean completed = executor.awaitTermination(60, TimeUnit.SECONDS);

        if (completed) {
          logger.info("✅ 모든 요청 처리 완료!");

          // 결과 집계
          int successCount = 0;
          int timeoutCount = 0;

          for (Future<ClientResult> future : futures) {
            try {
              ClientResult clientResult = future.get();
              if (clientResult.success) successCount++;
              if (clientResult.timeout) timeoutCount++;
            } catch (Exception e) {
              // ignore
            }
          }

          result.completedRequests = TOTAL_REQUESTS;
          result.successfulRequests = successCount;
          result.timedOutRequests = timeoutCount;
          result.threadPoolExhausted = false;

          logger.info("📊 결과: " + timeoutCount + "개 타임아웃, " +
              successCount + "개 성공");
        }
      }

      // 모니터링 종료
      monitorTask.cancel(true);

      // 최종 상태 기록
      result.finalActiveThreads = activeThreads.get();
      result.maxBlockedThreads = blockedThreads.get();

      testResults.add(result);

      // 성공/실패 판단
      if (config.timeout == 0) {
        // 타임아웃 없는 경우: 스레드풀 고갈이 발생해야 정상
        return result.threadPoolExhausted;
      } else {
        // 타임아웃 있는 경우: 모든 요청이 완료되어야 정상
        return result.completedRequests == TOTAL_REQUESTS;
      }

    } finally {
      executor.shutdownNow();
      monitor.shutdownNow();
      Thread.sleep(2000);  // 스레드 정리 대기
    }
  }

  /**
   * 개별 클라이언트 요청 실행
   *
   * 각 스레드가 실행하는 실제 작업입니다.
   * connect() 시도 시 타임아웃 유무에 따라 동작이 달라집니다.
   */
  private ClientResult executeClientRequest(int requestId, TestConfig config,
      CountDownLatch startLatch) {
    ClientResult result = new ClientResult(requestId);

    try {
      // 모든 스레드가 동시 시작하도록 대기
      startLatch.await();

      TimeoutClient client = new TimeoutClient("localhost", serverPort);

      // 타임아웃 설정 (0이면 무한 대기)
      if (config.timeout > 0) {
        client.setConnectTimeout(config.timeout);
      }

      logger.debug("요청 #" + requestId + " 시작");

      long startTime = System.currentTimeMillis();

      // 연결 시도 - 여기서 블로킹 발생!
      boolean connected = client.connect();

      long duration = System.currentTimeMillis() - startTime;

      result.duration = duration;
      result.success = connected;

      if (!connected && client.getLastException() instanceof java.net.SocketTimeoutException) {
        result.timeout = true;
        logger.debug("요청 #" + requestId + " - 타임아웃 (" + duration + "ms)");
      } else if (!connected) {
        logger.debug("요청 #" + requestId + " - 실패");
      } else {
        logger.debug("요청 #" + requestId + " - 성공");
        client.disconnect();
      }

    } catch (Exception e) {
      logger.error("요청 #" + requestId + " 실행 중 오류", e);
      result.success = false;
    }

    return result;
  }

  /**
   * 블로킹된 스레드 수 계산
   *
   * JVM의 모든 스레드를 검사하여 우리가 생성한 Worker 스레드 중
   * 블로킹 상태인 스레드를 카운트합니다.
   */
  private int countBlockedThreads() {
    int blocked = 0;

    // 모든 스레드의 스택 트레이스 가져오기
    for (Thread thread : Thread.getAllStackTraces().keySet()) {
      // Worker 스레드만 체크
      if (thread.getName().startsWith("Worker-")) {
        Thread.State state = thread.getState();

        // 블로킹 상태 체크
        if (state == Thread.State.BLOCKED ||      // 락 대기
            state == Thread.State.WAITING ||      // 무한 대기
            state == Thread.State.TIMED_WAITING) { // 시간 제한 대기
          blocked++;
        }
      }
    }
    return blocked;
  }

  @Override
  protected void teardown() {
    if (noAcceptServer != null && noAcceptServer.isRunning()) {
      noAcceptServer.stop();
    }
  }

  @Override
  protected void printAdditionalResults() {
    System.out.println("\n🔍 스레드풀 고갈 테스트 결과:");
    System.out.println("┌──────────────────┬────────────┬────────────┬────────────┬──────────────┐");
    System.out.println("│      설정         │  완료 요청  │  타임아웃   │ 블로킹 스레드 │     상태      │");
    System.out.println("├──────────────────┼────────────┼────────────┼────────────┼──────────────┤");

    for (ThreadPoolTestResult result : testResults) {
      String status = result.threadPoolExhausted ? "❌ 고갈" : "✅ 정상";

      System.out.printf("│ %16s │ %10d │ %10d │ %11d │ %12s │%n",
          result.config.description,
          result.completedRequests,
          result.timedOutRequests,
          result.maxBlockedThreads,
          status
      );
    }

    System.out.println("└──────────────────┴────────────┴────────────┴────────────┴──────────────┘");

    analyzeComparison();
  }

  /**
   * 타임아웃 있음/없음 비교 분석
   *
   * 두 테스트 결과를 비교하여 타임아웃의 중요성을 보여줍니다.
   */
  private void analyzeComparison() {
    System.out.println("\n💡 비교 분석:");

    // 각 설정의 결과 찾기
    ThreadPoolTestResult noTimeoutResult = testResults.stream()
        .filter(r -> r.config == TestConfig.NO_TIMEOUT)
        .findFirst()
        .orElse(null);

    ThreadPoolTestResult withTimeoutResult = testResults.stream()
        .filter(r -> r.config == TestConfig.WITH_TIMEOUT)
        .findFirst()
        .orElse(null);

    if (noTimeoutResult != null && withTimeoutResult != null) {
      System.out.println("\n📊 타임아웃 미설정 시:");
      System.out.println("  • 완료된 요청: " + noTimeoutResult.completedRequests +
          "/" + TOTAL_REQUESTS);
      System.out.println("  • 블로킹된 스레드: " + noTimeoutResult.maxBlockedThreads);
      System.out.println("  • 스레드풀 고갈: " +
          (noTimeoutResult.threadPoolExhausted ? "발생 ⚠️" : "미발생"));

      System.out.println("\n📊 타임아웃 설정 시 (3초):");
      System.out.println("  • 완료된 요청: " + withTimeoutResult.completedRequests +
          "/" + TOTAL_REQUESTS);
      System.out.println("  • 타임아웃 발생: " + withTimeoutResult.timedOutRequests);
      System.out.println("  • 스레드풀 고갈: " +
          (withTimeoutResult.threadPoolExhausted ? "발생" : "미발생 ✅"));

      System.out.println("\n🎯 핵심 발견:");
      System.out.println("  1. 타임아웃 미설정 시 스레드가 무한 대기하여 풀이 고갈됩니다");
      System.out.println("  2. 적절한 타임아웃 설정으로 스레드를 빠르게 반환할 수 있습니다");
      System.out.println("  3. 스레드풀 크기(" + THREAD_POOL_SIZE +
          ")를 초과하는 요청(" + TOTAL_REQUESTS + ")도 타임아웃으로 처리 가능합니다");

      // 처리량 비교
      int noTimeoutThroughput = noTimeoutResult.completedRequests;
      int withTimeoutThroughput = withTimeoutResult.completedRequests;

      if (withTimeoutThroughput > noTimeoutThroughput) {
        double improvement = ((double)(withTimeoutThroughput - noTimeoutThroughput) /
            noTimeoutThroughput) * 100;
        System.out.println("  4. 타임아웃 설정으로 처리량 " +
            String.format("%.0f%%", improvement) + " 향상");
      }
    }

    System.out.println("\n⚠️ 경고:");
    System.out.println("  • 타임아웃 미설정은 서비스 장애의 주요 원인입니다");
    System.out.println("  • 연쇄 장애(Cascading Failure)를 방지하려면 반드시 타임아웃을 설정하세요");
    System.out.println("  • 실제 서비스에서는 Circuit Breaker 패턴도 함께 사용하세요");
  }

  /**
   * 클라이언트 요청 결과 클래스
   */
  private static class ClientResult {
    final int requestId;
    boolean success;
    boolean timeout;
    long duration;

    ClientResult(int requestId) {
      this.requestId = requestId;
    }
  }

  /**
   * 스레드풀 테스트 결과 클래스
   */
  private static class ThreadPoolTestResult {
    final TestConfig config;
    int completedRequests;      // 완료된 요청 수
    int successfulRequests;     // 성공한 요청 수
    int timedOutRequests;       // 타임아웃된 요청 수
    int maxBlockedThreads;      // 최대 블로킹 스레드 수
    int finalActiveThreads;     // 최종 활성 스레드 수
    boolean threadPoolExhausted;// 스레드풀 고갈 여부
    int blockedThreads;         // 블로킹된 스레드 수

    ThreadPoolTestResult(TestConfig config) {
      this.config = config;
    }
  }

  /**
   * 단독 실행용 main 메서드
   */
  public static void main(String[] args) {
    ThreadExhaustionScenario scenario = new ThreadExhaustionScenario();
    scenario.execute();
  }
}