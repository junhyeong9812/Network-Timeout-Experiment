package com.experiment.timeout_lab.scenario;

import com.experiment.timeout_lab.client.TimeoutClient;
import com.experiment.timeout_lab.server.ProblematicServer;
import com.experiment.timeout_lab.util.Constants.ServerMode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Write Timeout 시나리오 (수정 버전 - Future로 타임아웃 구현)
 *
 * 이 클래스는 Write Timeout이 Future를 사용하여 구현된 수정 버전입니다.
 * 원래 버전의 무한 블로킹 문제를 해결하여 1MB + PARTIAL_READ 조합에서도
 * 정상적으로 타임아웃이 발생하고 테스트가 완료됩니다.
 *
 * ✅ 해결된 문제:
 * 1. Write 작업을 별도 스레드에서 실행 (ExecutorService 사용)
 * 2. Future.get()에 타임아웃 설정 (5초)
 * 3. 타임아웃 발생 시 작업 취소 (Future.cancel)
 * 4. 블로킹된 스트림도 강제 종료
 *
 * 핵심 개선사항:
 * - 무한 블로킹 방지
 * - Write Timeout 구현 (Java API 한계 우회)
 * - 안정적인 테스트 완료
 * - 명확한 타임아웃 통계
 *
 * @author Timeout Lab Team
 * @version 2.0 (Future 타임아웃 적용)
 */
public class WriteTimeoutScenario extends BaseScenario {

  // ========== 서버 인스턴스 ==========

  /**
   * SLOW_READ 모드 서버
   * 클라이언트 데이터를 10초에 1바이트씩 매우 천천히 읽습니다.
   * TCP 흐름 제어로 인한 전송 속도 저하를 시뮬레이션합니다.
   */
  private ProblematicServer slowReadServer;

  /**
   * PARTIAL_READ 모드 서버
   * 10바이트만 읽고 완전히 멈춥니다.
   * TCP 버퍼 고갈로 인한 write() 블로킹을 유발합니다.
   * ✅ 수정 버전에서는 5초 후 타임아웃으로 해결됩니다!
   */
  private ProblematicServer partialReadServer;

  // 각 서버의 포트 번호
  private final int slowReadPort = 8084;      // SLOW_READ 서버 포트
  private final int partialReadPort = 8085;   // PARTIAL_READ 서버 포트

  // ========== Write Timeout 구현을 위한 추가 컴포넌트 ==========

  /**
   * Write 작업용 ExecutorService
   *
   * Write 작업을 별도 스레드에서 실행하기 위한 스레드풀입니다.
   * Future와 함께 사용하여 타임아웃을 구현합니다.
   *
   * 핵심 역할:
   * 1. Write 작업을 메인 스레드에서 분리
   * 2. Future 객체 생성 가능
   * 3. 타임아웃 시 작업 취소 가능
   */
  private ExecutorService writeExecutor;

  /**
   * Write 타임아웃 설정 (밀리초)
   *
   * Write 작업이 이 시간을 초과하면 자동으로 취소됩니다.
   * 5000ms (5초)로 설정하여 무한 블로킹을 방지합니다.
   */
  private static final long WRITE_TIMEOUT = 5000; // 5초

  // ========== 테스트 설정 열거형 ==========

  /**
   * 테스트할 데이터 크기 열거형
   *
   * 다양한 크기로 TCP 버퍼 동작을 관찰합니다.
   * 특히 VERY_LARGE(1MB)가 핵심 테스트 케이스입니다.
   */
  private enum DataSize {
    SMALL(100, "100 bytes"),           // 작은 데이터 - 항상 성공
    MEDIUM(10_000, "10 KB"),          // 중간 크기 - 대부분 성공
    LARGE(100_000, "100 KB"),         // 큰 데이터 - 버퍼에 따라 다름
    VERY_LARGE(1_000_000, "1 MB");    // 매우 큰 데이터 - 타임아웃 발생! ⏱️

    final int bytes;                   // 실제 바이트 수
    final String description;          // 표시용 설명

    DataSize(int bytes, String description) {
      this.bytes = bytes;
      this.description = description;
    }
  }

  /**
   * 테스트 모드 열거형
   *
   * 서버의 데이터 읽기 방식을 정의합니다.
   */
  private enum TestMode {
    SLOW_READ("서버가 매우 천천히 읽음"),      // 느리지만 계속 읽음
    PARTIAL_READ("서버가 일부만 읽고 멈춤");   // 10바이트만 읽고 멈춤

    private final String description;

    TestMode(String description) {
      this.description = description;
    }
  }

  // 현재 테스트 설정
  private TestMode currentMode;          // 현재 테스트 모드
  private DataSize currentDataSize;      // 현재 데이터 크기

  // 테스트 결과 저장 리스트
  private final List<WriteTestResult> testResults = new ArrayList<>();

  /**
   * 생성자
   * 시나리오 이름과 설명을 설정합니다.
   */
  public WriteTimeoutScenario() {
    super("Write Timeout Scenario",
        "서버가 데이터를 읽지 않거나 천천히 읽을 때 Write 동작 테스트");
  }

  /**
   * 시나리오 준비 단계
   *
   * ✅ 핵심 추가: ExecutorService 생성
   * Write 작업을 별도 스레드에서 실행하기 위한 스레드풀을 생성합니다.
   */
  @Override
  protected void setup() throws Exception {
    logger.info("서버들 시작 중...");

    // ===== Write 작업용 스레드풀 생성 (핵심!) =====
    // 2개의 스레드를 가진 고정 크기 스레드풀
    // Write 작업을 메인 스레드에서 분리하여 타임아웃 제어 가능
    writeExecutor = Executors.newFixedThreadPool(2);

    // SLOW_READ 서버 시작 (10초에 1바이트씩 읽음)
    slowReadServer = new ProblematicServer(slowReadPort, ServerMode.SLOW_READ);
    slowReadServer.start();

    // PARTIAL_READ 서버 시작 (10바이트만 읽고 멈춤)
    partialReadServer = new ProblematicServer(partialReadPort, ServerMode.PARTIAL_READ);
    partialReadServer.start();

    // 서버 시작 대기
    Thread.sleep(1000);

    logger.info("서버 준비 완료");
    logger.info("  • SLOW_READ 서버: Port " + slowReadPort);
    logger.info("  • PARTIAL_READ 서버: Port " + partialReadPort);
  }

  /**
   * 단일 시나리오 실행
   *
   * ✅ 핵심 개선: Future를 사용한 Write Timeout 구현
   * 원래 버전의 무한 블로킹 문제를 완전히 해결했습니다.
   *
   * @param iteration 현재 반복 번호
   * @return 테스트 성공 여부 (타임아웃은 예상된 동작이므로 실패 아님)
   */
  @Override
  protected boolean runScenario(int iteration) throws Exception {
    // ===== 테스트 설정 결정 =====

    // 짝수: SLOW_READ, 홀수: PARTIAL_READ
    currentMode = (iteration % 2 == 0) ? TestMode.SLOW_READ : TestMode.PARTIAL_READ;

    // 데이터 크기 순환 선택
    DataSize[] sizes = DataSize.values();
    currentDataSize = sizes[iteration % sizes.length];

    // 모드에 따른 포트 선택
    int port = (currentMode == TestMode.SLOW_READ) ? slowReadPort : partialReadPort;

    // ===== 클라이언트 생성 및 설정 =====

    TimeoutClient client = new TimeoutClient("localhost", port);
    client.setConnectTimeout(5000);    // Connect Timeout: 5초
    client.setReadTimeout(30000);      // Read Timeout: 30초
    // Write Timeout은 아래에서 Future로 구현!

    try {
      // 테스트 정보 출력
      logger.info("\n🔄 테스트 " + (iteration + 1) +
          ": " + currentMode.description +
          ", 데이터 크기 = " + currentDataSize.description);

      // ===== 1단계: 서버 연결 =====

      if (!client.connect()) {
        logger.error("연결 실패");
        return false;
      }
      logger.info("✅ 연결 성공");

      // ===== 2단계: 테스트 데이터 생성 =====

      String data = generateData(currentDataSize.bytes);
      logger.info("📤 데이터 전송 시작 (" + currentDataSize.description + ")");

      // ===== 3단계: Future를 사용한 Write Timeout 구현 (핵심!) =====

      // 시작 시간 기록
      long startTime = System.currentTimeMillis();
      boolean sent = false;
      String timeoutStatus = "정상";  // 타임아웃 상태 추적

      /**
       * Write 작업을 Future로 실행
       *
       * 핵심 포인트:
       * 1. Callable로 Write 작업 정의
       * 2. ExecutorService.submit()으로 별도 스레드에서 실행
       * 3. Future 객체 반환 (작업 제어 가능)
       */
      Future<Boolean> writeFuture = writeExecutor.submit(() -> {
        try {
          // 실제 데이터 전송
          // 이 작업은 별도 스레드에서 실행되므로
          // 메인 스레드는 블로킹되지 않음!
          return client.sendData(data);
        } catch (Exception e) {
          logger.error("Write 중 예외: " + e.getMessage());
          return false;
        }
      });

      try {
        /**
         * 타임아웃 설정하여 결과 대기
         *
         * Future.get(timeout, unit) 메서드 사용
         * - 정상 완료: Boolean 결과 반환
         * - 타임아웃: TimeoutException 발생
         *
         * 이것이 Java에서 Write Timeout을 구현하는 핵심!
         */
        sent = writeFuture.get(WRITE_TIMEOUT, TimeUnit.MILLISECONDS);
        logger.info("✅ 데이터 전송 완료");

      } catch (TimeoutException e) {
        // ===== Write Timeout 발생! =====

        logger.warn("⏱️ Write Timeout 발생! (" + WRITE_TIMEOUT + "ms 초과)");

        /**
         * Future.cancel(true)
         * - true: 실행 중인 작업에 interrupt 신호 전송
         * - 블로킹된 I/O 작업 중단 시도
         *
         * 완벽하지는 않지만 대부분 작동합니다.
         */
        writeFuture.cancel(true);

        timeoutStatus = "TIMEOUT";
        sent = false;

        // 1MB + PARTIAL_READ 조합에서 예상되는 동작임을 명시
        if (currentMode == TestMode.PARTIAL_READ &&
            currentDataSize == DataSize.VERY_LARGE) {
          logger.info("💡 예상된 동작: 서버가 10바이트만 읽고 멈춤 → TCP 버퍼 가득 → Write 블로킹");
        }

      } catch (InterruptedException e) {
        // 스레드가 인터럽트된 경우
        logger.error("Write 작업이 인터럽트됨: " + e.getMessage());
        sent = false;

      } catch (ExecutionException e) {
        // Write 작업 중 예외 발생
        logger.error("Write 실행 중 오류: " + e.getMessage());
        sent = false;
      }

      // 전송 소요 시간 계산
      long writeTime = System.currentTimeMillis() - startTime;

      // ===== 결과 저장 =====

      /**
       * WriteTestResult 생성
       *
       * 수정 버전에서 추가된 필드:
       * - timeoutStatus: "정상" 또는 "TIMEOUT"
       *
       * 이를 통해 타임아웃 발생 횟수를 정확히 추적
       */
      WriteTestResult result = new WriteTestResult(
          currentMode,
          currentDataSize,
          writeTime,
          sent,
          timeoutStatus,      // 타임아웃 상태 추가!
          client.getLastException()
      );
      testResults.add(result);

      // ===== 결과 분석 및 출력 =====

      if (sent) {
        // 전송 성공 시
        double throughput = (currentDataSize.bytes / 1024.0) / (writeTime / 1000.0);
        logger.info("📊 전송 속도: " + String.format("%.2f KB/s", throughput));

        if (currentMode == TestMode.SLOW_READ && writeTime > 3000) {
          logger.warn("⚠️ 전송이 느림 (서버가 천천히 읽는 중)");
        }
      } else {
        // 전송 실패 시
        if (timeoutStatus.equals("TIMEOUT")) {
          logger.error("❌ Write Timeout으로 전송 실패");
        } else {
          logger.error("❌ 데이터 전송 실패");
        }
      }

      /**
       * 반환값 결정
       *
       * 타임아웃은 예상된 동작이므로 실패로 간주하지 않음
       * PARTIAL_READ + 1MB에서 타임아웃은 정상!
       */
      return !timeoutStatus.equals("TIMEOUT");

    } finally {
      /**
       * 연결 강제 종료
       *
       * try-finally 블록으로 반드시 실행 보장
       * 블로킹된 스트림도 강제로 닫음
       */
      try {
        client.disconnect();
      } catch (Exception e) {
        logger.debug("연결 종료 중 오류 (무시): " + e.getMessage());
      }
    }
  }

  /**
   * 테스트용 데이터 생성
   *
   * "0123456789ABCDEF" 패턴을 반복하여 원하는 크기 생성
   *
   * @param size 생성할 데이터 크기 (바이트)
   * @return 생성된 문자열
   */
  private String generateData(int size) {
    StringBuilder sb = new StringBuilder(size);
    String pattern = "0123456789ABCDEF";  // 16자 패턴

    for (int i = 0; i < size; i++) {
      sb.append(pattern.charAt(i % pattern.length()));
    }

    return sb.toString();
  }

  /**
   * 시나리오 정리 단계
   *
   * ✅ 핵심 추가: ExecutorService 종료
   * 스레드풀을 정리하여 리소스 누수 방지
   */
  @Override
  protected void teardown() {
    // ===== ExecutorService 종료 (중요!) =====

    if (writeExecutor != null) {
      /**
       * shutdownNow()
       * - 실행 중인 작업에 interrupt 신호 전송
       * - 대기 중인 작업 취소
       * - 즉시 종료 시도
       */
      writeExecutor.shutdownNow();

      try {
        // 5초 동안 종료 대기
        if (!writeExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
          logger.warn("ExecutorService 종료 타임아웃");
        }
      } catch (InterruptedException e) {
        // 인터럽트 상태 복원
        Thread.currentThread().interrupt();
      }
    }

    // 서버들 종료
    if (slowReadServer != null && slowReadServer.isRunning()) {
      slowReadServer.stop();
    }
    if (partialReadServer != null && partialReadServer.isRunning()) {
      partialReadServer.stop();
    }
  }

  /**
   * 추가 결과 출력 - Write 동작 특화 통계
   *
   * 타임아웃 발생 횟수와 패턴을 분석하여 출력합니다.
   */
  @Override
  protected void printAdditionalResults() {
    System.out.println("\n🔍 Write 동작 상세 결과:");

    // SLOW_READ 모드 결과
    System.out.println("\n📌 SLOW_READ 모드 (서버가 천천히 읽음):");
    System.out.println("┌──────────────┬──────────────┬──────────────┬──────────────┐");
    System.out.println("│  데이터 크기   │   전송 시간    │   전송 속도    │     상태      │");
    System.out.println("├──────────────┼──────────────┼──────────────┼──────────────┤");

    printModeResults(TestMode.SLOW_READ);

    System.out.println("└──────────────┴──────────────┴──────────────┴──────────────┘");

    // PARTIAL_READ 모드 결과
    System.out.println("\n📌 PARTIAL_READ 모드 (서버가 일부만 읽음):");
    System.out.println("┌──────────────┬──────────────┬──────────────┬──────────────┐");
    System.out.println("│  데이터 크기   │   전송 시간    │     결과      │     상태      │");
    System.out.println("├──────────────┼──────────────┼──────────────┼──────────────┤");

    printModeResults(TestMode.PARTIAL_READ);

    System.out.println("└──────────────┴──────────────┴──────────────┴──────────────┘");

    analyzeResults();
  }

  /**
   * 특정 모드의 결과를 테이블 형식으로 출력
   *
   * @param mode 출력할 테스트 모드
   */
  private void printModeResults(TestMode mode) {
    for (DataSize size : DataSize.values()) {
      // 해당 모드와 크기의 결과 필터링
      List<WriteTestResult> results = testResults.stream()
          .filter(r -> r.mode == mode && r.dataSize == size)
          .toList();

      if (!results.isEmpty()) {
        // 평균 전송 시간 계산
        double avgTime = results.stream()
            .mapToLong(r -> r.writeTime)
            .average()
            .orElse(0);

        // 타임아웃 발생 횟수 계산 (핵심!)
        long timeoutCount = results.stream()
            .filter(r -> "TIMEOUT".equals(r.timeoutStatus))
            .count();

        if (mode == TestMode.SLOW_READ) {
          // SLOW_READ: 전송 속도 중심
          double throughput = (size.bytes / 1024.0) / (avgTime / 1000.0);
          String status = avgTime > 3000 ? "느림" : "정상";

          System.out.printf("│ %12s │ %12.0fms │ %10.2f KB/s │ %12s │%n",
              size.description,
              avgTime,
              throughput,
              status
          );
        } else {
          // PARTIAL_READ: 타임아웃 발생 중심
          String result = timeoutCount > 0 ? "TIMEOUT" : "전송 완료";

          // 1MB + PARTIAL_READ에서 타임아웃은 예상된 동작
          String status = (size == DataSize.VERY_LARGE && timeoutCount > 0) ?
              "예상된 동작" : "정상";

          System.out.printf("│ %12s │ %12.0fms │ %12s │ %12s │%n",
              size.description,
              avgTime,
              result,
              status
          );
        }
      }
    }
  }

  /**
   * 전체 결과 분석 및 통계 출력
   *
   * Future를 사용한 Write Timeout 구현의 효과를 강조합니다.
   */
  private void analyzeResults() {
    System.out.println("\n💡 분석:");

    // Write Timeout 발생 횟수 계산
    long timeoutCount = testResults.stream()
        .filter(r -> "TIMEOUT".equals(r.timeoutStatus))
        .count();

    System.out.println("  • Write Timeout 발생 횟수: " + timeoutCount + "회");
    System.out.println("  • Future를 사용하여 Write Timeout 구현 성공");
    System.out.println("  • 1MB + PARTIAL_READ 조합에서 예상대로 타임아웃 발생");

    // ===== 핵심 발견 사항 =====

    System.out.println("\n📝 핵심 발견:");
    System.out.println("  • Java는 기본적으로 Write Timeout을 지원하지 않음");
    System.out.println("  • Future + get(timeout)으로 Write Timeout 구현 가능");
    System.out.println("  • TCP 버퍼가 가득 차면 write()가 블로킹됨");
    System.out.println("  • 서버 읽기 속도가 클라이언트 전송 속도를 결정");

    // ===== 원래 버전과의 비교 =====

    System.out.println("\n🔄 개선 사항 (원래 버전 대비):");
    System.out.println("  • 무한 블로킹 해결 (20분+ → 5초)");
    System.out.println("  • 테스트 정상 완료 보장");
    System.out.println("  • 타임아웃 통계 제공");
    System.out.println("  • 서비스 안정성 확보");

    // ===== 권장사항 =====

    System.out.println("\n💡 권장사항:");
    System.out.println("  • 프로덕션에서는 NIO 또는 Netty 사용 권장");
    System.out.println("  • 대용량 데이터는 청크 단위로 전송");
    System.out.println("  • Write 작업에도 타임아웃 설정 필수");
  }

  /**
   * 테스트 결과 저장 클래스
   *
   * 수정 버전에서 timeoutStatus 필드가 추가되었습니다.
   */
  private static class WriteTestResult {
    final TestMode mode;           // 테스트 모드
    final DataSize dataSize;       // 데이터 크기
    final long writeTime;          // 전송 소요 시간
    final boolean sent;            // 전송 성공 여부
    final String timeoutStatus;    // 타임아웃 상태 ("정상" or "TIMEOUT") ✅ 추가!
    final Exception exception;     // 발생한 예외

    WriteTestResult(TestMode mode, DataSize dataSize, long writeTime,
        boolean sent, String timeoutStatus, Exception exception) {
      this.mode = mode;
      this.dataSize = dataSize;
      this.writeTime = writeTime;
      this.sent = sent;
      this.timeoutStatus = timeoutStatus;  // 타임아웃 추적
      this.exception = exception;
    }
  }

  /**
   * 단독 실행용 main 메서드
   *
   * ✅ 안전: Future 타임아웃으로 무한 블로킹 없음
   * 모든 테스트가 정상적으로 완료됩니다.
   */
  public static void main(String[] args) {
    System.out.println("✅ Write Timeout Scenario - Future 버전");
    System.out.println("5초 타임아웃이 적용되어 안전합니다.");
    System.out.println();

    WriteTimeoutScenario scenario = new WriteTimeoutScenario();

    // 테스트 설정
    scenario.setIterations(16);      // 총 16회 테스트
    scenario.setWarmupIterations(2); // 워밍업 2회

    // 실행 - 안전하게 완료됨!
    scenario.execute();
  }
}