package com.experiment.timeout_lab.scenario;

import com.experiment.timeout_lab.client.TimeoutClient;
import com.experiment.timeout_lab.server.ProblematicServer;

import com.experiment.timeout_lab.util.Constants.ServerMode;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

/**
 * Connect Timeout 시나리오
 *
 * TCP 3-way handshake 과정에서 발생하는 타임아웃을 실험합니다.
 * 서버가 accept()를 하지 않는 상황에서 클라이언트의 연결 시도가
 * 타임아웃되는 것을 관찰합니다.
 * */
public class ConnectTimeoutScenario extends BaseScenario {

    private ProblematicServer server;
    private final int serverPort = 8081;

    // 테스트할 타임아웃 값들 (밀리초)
    private final int[] timeoutValues = {1000, 3000, 5000, 10000, 30000};
    private int currentTimeout = 5000;

    // 타임아웃별 결과 저장
    private final List<TimeoutTestResult> testResults = new ArrayList<>();

    public ConnectTimeoutScenario() {
        super("Connect Timeout Scenario",
                "서버가 accept()를 하지 않을 때 Connect Timeout 테스트");
    }

    @Override
    protected void setup() throws Exception {
      logger.info("서버 시작 중... (NO_ACCEPT 모드)");
      server = new ProblematicServer(serverPort, ServerMode.NO_ACCEPT);
      server.start();

      // 서버가 완전히 시작될 때까지 대기
      Thread.sleep(1000);
      logger.info("서버 준비 완료 (Port: " + serverPort + ")");
    }

  @Override
  protected boolean runScenario(int iteration) throws Exception {
    // 각 반복마다 다른 타임아웃 값 테스트
    currentTimeout = timeoutValues[iteration % timeoutValues.length];

    TimeoutClient client = new TimeoutClient("localhost", serverPort);
    client.setConnectTimeout(currentTimeout);

    try {
      logger.info("\n🔄 테스트 " + (iteration + 1) +
          ": Connect Timeout = " + currentTimeout + "ms");

      long startTime = System.currentTimeMillis();
      boolean connected = client.connect();
      long actualTime = System.currentTimeMillis() - startTime;

      // 결과 저장
      TimeoutTestResult result = new TimeoutTestResult(
          currentTimeout, actualTime, connected,
          client.getLastException()
      );
      testResults.add(result);

      if (!connected) {
        Exception lastError = client.getLastException();
        if (lastError instanceof SocketTimeoutException) {
          timeoutCount.incrementAndGet();
          logger.info("✅ 예상대로 Connect Timeout 발생 (실제 대기: " +
              actualTime + "ms)");

          // 타임아웃이 정확히 작동했는지 검증
          long tolerance = 100; // 100ms 오차 허용
          if (Math.abs(actualTime - currentTimeout) <= tolerance) {
            logger.info("✅ 타임아웃이 정확히 작동함");
            return true;
          } else {
            logger.warn("⚠️ 타임아웃 오차 발생: 예상 " + currentTimeout +
                "ms, 실제 " + actualTime + "ms");
            return true; // 타임아웃은 발생했으므로 성공으로 처리
          }
        } else {
          logger.error("❌ 예상치 못한 오류: " + lastError.getMessage());
          return false;
        }
      } else {
        logger.error("❌ 연결이 성공함 (예상: 실패)");
        client.disconnect();
        return false;
      }

    } finally {
      client.disconnect();
    }
  }

    @Override
    protected void teardown() {
      if (server != null && server.isRunning()) {
        logger.info("서버 종료 중...");
        server.stop();
      }
    }

    @Override
    protected void printAdditionalResults() {
      System.out.println("\n🔍 타임아웃별 상세 결과:");
      System.out.println("┌─────────────┬──────────────┬──────────┬──────────────┐");
      System.out.println("│ Timeout 설정 │  실제 대기시간  │   결과    │     오차      │");
      System.out.println("├─────────────┼──────────────┼──────────┼──────────────┤");

      // 타임아웃 값별로 그룹화
      for (int timeoutValue : timeoutValues) {
        List<TimeoutTestResult> results = testResults.stream()
            .filter(r -> r.configuredTimeout == timeoutValue)
            .toList();

        if (!results.isEmpty()) {
          double avgActual = results.stream()
              .mapToLong(r -> r.actualTime)
              .average()
              .orElse(0);

          double avgError = Math.abs(avgActual - timeoutValue);
          double errorPercent = (avgError / timeoutValue) * 100;

          System.out.printf("│ %11dms │ %12.0fms │ %8s │ %6.0fms (%3.1f%%) │%n",
              timeoutValue,
              avgActual,
              results.stream().allMatch(r -> !r.connected) ? "TIMEOUT" : "MIXED",
              avgError,
              errorPercent
          );
        }
      }

      System.out.println("└─────────────┴──────────────┴──────────┴──────────────┘");

      // 분석 결과
      System.out.println("\n💡 분석:");
      System.out.println("  • 모든 Connect Timeout이 정상 작동: " +
          (timeoutCount.get() == totalRuns ? "✅ YES" : "❌ NO"));

      if (timeoutCount.get() > 0) {
        double avgError = testResults.stream()
            .mapToDouble(r -> Math.abs(r.actualTime - r.configuredTimeout))
            .average()
            .orElse(0);

        System.out.println("  • 평균 타임아웃 오차: " +
            String.format("%.2fms", avgError));

        if (avgError < 50) {
          System.out.println("  • 타임아웃 정확도: 🟢 매우 정확");
        } else if (avgError < 100) {
          System.out.println("  • 타임아웃 정확도: 🟡 양호");
        } else {
          System.out.println("  • 타임아웃 정확도: 🔴 부정확");
        }
      }
    }

    /**
     * 테스트 결과를 저장하는 내부 클래스
     * */
    private static class TimeoutTestResult {
        final int configuredTimeout;
        final long actualTime;
        final boolean connected;
        final Exception exception;

        TimeoutTestResult(int configuredTimeout, long actualTime,
                          boolean connected, Exception exception) {
            this.configuredTimeout = configuredTimeout;
            this.actualTime = actualTime;
            this.connected = connected;
            this.exception = exception;
        }
    }

    /**
     * 단독 실행용 main 메서드
     * */
    public static void main(String[] args) {
        ConnectTimeoutScenario scenario = new ConnectTimeoutScenario();
        scenario.setIterations(15); // 각 타임아웃 값당 3회 씩
        scenario.setWarmupIterations(0); // Connect는 워밍업 불필요.
        scenario.execute();
    }
}
