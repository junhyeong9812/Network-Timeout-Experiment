package com.experiment.timeout_lab.scenario;

import com.experiment.timeout_lab.client.TimeoutClient;
import com.experiment.timeout_lab.server.ProblematicServer;
import com.experiment.timeout_lab.util.Constants.ServerMode;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read Timeout 시나리오
 *
 * 연결은 성공하지만 서버가 응답하지 않거나 매우 느리게 응답할 때
 * 발생하는 Read Timeout을 실험합니다.
 */
public class ReadTimeoutScenario extends BaseScenario {

  private ProblematicServer noResponseServer;
  private ProblematicServer slowResponseServer;
  private final int noResponsePort = 8082;
  private final int slowResponsePort = 8083;

  // 테스트할 타임아웃 값들 (밀리초)
  private final int[] timeoutValues = {1000, 3000, 5000, 10000};

  // 테스트 모드
  private enum TestMode {
    NO_RESPONSE("서버 무응답"),
    SLOW_RESPONSE("서버 느린 응답");

    private final String description;
    TestMode(String description) {
      this.description = description;
    }
  }

  private TestMode currentMode;
  private int currentTimeout;

  // 결과 저장
  private final List<ReadTestResult> testResults = new ArrayList<>();

  public ReadTimeoutScenario() {
    super("Read Timeout Scenario",
        "서버가 응답하지 않거나 느리게 응답할 때 Read Timeout 테스트");
  }

  @Override
  protected void setup() throws Exception {
    logger.info("서버들 시작 중...");

    // NO_RESPONSE 서버 시작
    noResponseServer = new ProblematicServer(noResponsePort, ServerMode.NO_RESPONSE);
    noResponseServer.start();

    // SLOW_RESPONSE 서버 시작
    slowResponseServer = new ProblematicServer(slowResponsePort, ServerMode.SLOW_RESPONSE);
    slowResponseServer.start();

    Thread.sleep(1000);
    logger.info("서버 준비 완료");
    logger.info("  • NO_RESPONSE 서버: Port " + noResponsePort);
    logger.info("  • SLOW_RESPONSE 서버: Port " + slowResponsePort);
  }

  @Override
  protected boolean runScenario(int iteration) throws Exception {
    // 짝수: NO_RESPONSE 테스트, 홀수: SLOW_RESPONSE 테스트
    currentMode = (iteration % 2 == 0) ? TestMode.NO_RESPONSE : TestMode.SLOW_RESPONSE;
    currentTimeout = timeoutValues[iteration % timeoutValues.length];

    int port = (currentMode == TestMode.NO_RESPONSE) ? noResponsePort : slowResponsePort;

    TimeoutClient client = new TimeoutClient("localhost", port);
    client.setConnectTimeout(5000);  // Connect는 충분히 길게
    client.setReadTimeout(currentTimeout);

    try {
      logger.info("\n🔄 테스트 " + (iteration + 1) +
          ": " + currentMode.description +
          ", Read Timeout = " + currentTimeout + "ms");

      // 1. 연결
      if (!client.connect()) {
        logger.error("연결 실패 (예상: 성공)");
        return false;
      }
      logger.info("✅ 연결 성공");

      // 2. 데이터 전송
      client.sendData("GET /test HTTP/1.1\r\n\r\n");
      logger.info("📤 요청 전송 완료");

      // 3. 응답 대기 (Read Timeout 발생 예상)
      logger.info("📥 응답 대기 중...");
      long startTime = System.currentTimeMillis();
      String response = client.receiveData();
      long actualTime = System.currentTimeMillis() - startTime;

      // 결과 저장
      ReadTestResult result = new ReadTestResult(
          currentMode, currentTimeout, actualTime,
          response != null, client.getLastException()
      );
      testResults.add(result);

      // 결과 분석
      if (response == null) {
        Exception lastError = client.getLastException();
        if (lastError instanceof SocketTimeoutException) {
          timeoutCount.incrementAndGet();
          logger.info("✅ 예상대로 Read Timeout 발생 (실제 대기: " +
              actualTime + "ms)");

          // 타임아웃 정확도 검증
          long tolerance = 100;
          if (Math.abs(actualTime - currentTimeout) <= tolerance) {
            logger.info("✅ 타임아웃이 정확히 작동함");
            return true;
          } else {
            logger.warn("⚠️ 타임아웃 오차: 예상 " + currentTimeout +
                "ms, 실제 " + actualTime + "ms");
            return true;
          }
        } else {
          logger.error("❌ 예상치 못한 오류: " + lastError.getMessage());
          return false;
        }
      } else {
        // SLOW_RESPONSE 모드에서는 타임아웃이 길면 응답을 받을 수 있음
        if (currentMode == TestMode.SLOW_RESPONSE && currentTimeout >= 10000) {
          logger.info("✅ 응답 수신 (느린 응답이지만 타임아웃 내 도착): " +
              response.substring(0, Math.min(response.length(), 50)));
          return true;
        } else {
          logger.warn("⚠️ 예상치 못한 응답 수신");
          return false;
        }
      }

    } finally {
      client.disconnect();
    }
  }

  @Override
  protected void teardown() {
    if (noResponseServer != null && noResponseServer.isRunning()) {
      noResponseServer.stop();
    }
    if (slowResponseServer != null && slowResponseServer.isRunning()) {
      slowResponseServer.stop();
    }
  }

  @Override
  protected void printAdditionalResults() {
    System.out.println("\n🔍 Read Timeout 상세 결과:");

    // NO_RESPONSE 결과
    System.out.println("\n📌 NO_RESPONSE 모드 (서버 무응답):");
    System.out.println("┌─────────────┬──────────────┬──────────┬──────────────┐");
    System.out.println("│ Timeout 설정 │  실제 대기시간  │   결과    │     오차      │");
    System.out.println("├─────────────┼──────────────┼──────────┼──────────────┤");

    printModeResults(TestMode.NO_RESPONSE);

    System.out.println("└─────────────┴──────────────┴──────────┴──────────────┘");

    // SLOW_RESPONSE 결과
    System.out.println("\n📌 SLOW_RESPONSE 모드 (느린 응답):");
    System.out.println("┌─────────────┬──────────────┬──────────┬──────────────┐");
    System.out.println("│ Timeout 설정 │  실제 대기시간  │   결과    │     비고      │");
    System.out.println("├─────────────┼──────────────┼──────────┼──────────────┤");

    printModeResults(TestMode.SLOW_RESPONSE);

    System.out.println("└─────────────┴──────────────┴──────────┴──────────────┘");

    // 분석
    analyzeResults();
  }

  private void printModeResults(TestMode mode) {
    for (int timeoutValue : timeoutValues) {
      List<ReadTestResult> results = testResults.stream()
          .filter(r -> r.mode == mode && r.configuredTimeout == timeoutValue)
          .toList();

      if (!results.isEmpty()) {
        double avgActual = results.stream()
            .mapToLong(r -> r.actualTime)
            .average()
            .orElse(0);

        boolean allTimeout = results.stream()
            .allMatch(r -> !r.receivedResponse);

        if (mode == TestMode.NO_RESPONSE) {
          double avgError = Math.abs(avgActual - timeoutValue);
          double errorPercent = (avgError / timeoutValue) * 100;

          System.out.printf("│ %11dms │ %12.0fms │ %8s │ %6.0fms (%3.1f%%) │%n",
              timeoutValue,
              avgActual,
              allTimeout ? "TIMEOUT" : "MIXED",
              avgError,
              errorPercent
          );
        } else {
          // SLOW_RESPONSE 모드
          String result = allTimeout ? "TIMEOUT" : "RECEIVED";
          String note = timeoutValue >= 10000 ? "응답 가능" : "타임아웃 예상";

          System.out.printf("│ %11dms │ %12.0fms │ %8s │ %12s │%n",
              timeoutValue,
              avgActual,
              result,
              note
          );
        }
      }
    }
  }

  private void analyzeResults() {
    System.out.println("\n💡 분석:");

    // NO_RESPONSE 분석
    long noResponseTimeouts = testResults.stream()
        .filter(r -> r.mode == TestMode.NO_RESPONSE && !r.receivedResponse)
        .count();

    long noResponseTotal = testResults.stream()
        .filter(r -> r.mode == TestMode.NO_RESPONSE)
        .count();

    System.out.println("  • NO_RESPONSE 모드 타임아웃 발생률: " +
        String.format("%.1f%%", (noResponseTimeouts * 100.0 / noResponseTotal)));

    // SLOW_RESPONSE 분석
    long slowResponseTimeouts = testResults.stream()
        .filter(r -> r.mode == TestMode.SLOW_RESPONSE && !r.receivedResponse)
        .count();

    long slowResponseTotal = testResults.stream()
        .filter(r -> r.mode == TestMode.SLOW_RESPONSE)
        .count();

    System.out.println("  • SLOW_RESPONSE 모드 타임아웃 발생률: " +
        String.format("%.1f%%", (slowResponseTimeouts * 100.0 / slowResponseTotal)));

    // 타임아웃 정확도
    double avgError = testResults.stream()
        .filter(r -> !r.receivedResponse && r.exception instanceof SocketTimeoutException)
        .mapToDouble(r -> Math.abs(r.actualTime - r.configuredTimeout))
        .average()
        .orElse(0);

    System.out.println("  • 평균 Read Timeout 오차: " +
        String.format("%.2fms", avgError));

    if (avgError < 50) {
      System.out.println("  • Read Timeout 정확도: 🟢 매우 정확");
    } else if (avgError < 100) {
      System.out.println("  • Read Timeout 정확도: 🟡 양호");
    } else {
      System.out.println("  • Read Timeout 정확도: 🔴 부정확");
    }

    System.out.println("\n📝 핵심 발견:");
    System.out.println("  • Read Timeout은 데이터 수신 대기 시간을 제어합니다");
    System.out.println("  • 서버가 느리게 응답하는 경우, 충분한 타임아웃 설정이 필요합니다");
    System.out.println("  • 무응답 서버의 경우, 짧은 타임아웃으로 빠른 실패 처리가 가능합니다");
  }

  /**
   * 테스트 결과 저장 클래스
   */
  private static class ReadTestResult {
    final TestMode mode;
    final int configuredTimeout;
    final long actualTime;
    final boolean receivedResponse;
    final Exception exception;

    ReadTestResult(TestMode mode, int configuredTimeout, long actualTime,
        boolean receivedResponse, Exception exception) {
      this.mode = mode;
      this.configuredTimeout = configuredTimeout;
      this.actualTime = actualTime;
      this.receivedResponse = receivedResponse;
      this.exception = exception;
    }
  }

  /**
   * 단독 실행용 main 메서드
   */
  public static void main(String[] args) {
    ReadTimeoutScenario scenario = new ReadTimeoutScenario();
    scenario.setIterations(16); // 각 모드와 타임아웃 조합
    scenario.setWarmupIterations(2);
    scenario.execute();
  }
}