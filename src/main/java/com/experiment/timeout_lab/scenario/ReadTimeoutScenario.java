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
 * 이 시나리오는 연결된 소켓에서 데이터를 읽을 때 발생하는 Read Timeout을 실험합니다.
 *
 * Read Timeout이 발생하는 상황:
 * 1. 클라이언트가 서버에 연결 성공 (TCP 연결 확립)
 * 2. 클라이언트가 요청 전송
 * 3. 서버가 응답하지 않거나 매우 느리게 응답
 * 4. 클라이언트의 read() 메서드가 설정된 시간만큼 대기
 * 5. Read Timeout 발생 (SocketTimeoutException)
 *
 * 두 가지 테스트 모드:
 * - NO_RESPONSE: 서버가 전혀 응답하지 않음
 * - SLOW_RESPONSE: 서버가 매우 천천히 응답 (1초에 1바이트)
 *
 * @author Timeout Lab Team
 */
public class ReadTimeoutScenario extends BaseScenario {

  // ========== 서버 인스턴스 ==========

  // NO_RESPONSE 모드로 동작하는 서버
  private ProblematicServer noResponseServer;

  // SLOW_RESPONSE 모드로 동작하는 서버
  private ProblematicServer slowResponseServer;

  // 각 서버가 사용할 포트 (충돌 방지를 위해 다른 포트 사용)
  private final int noResponsePort = 8082;
  private final int slowResponsePort = 8083;

  // ========== 테스트 설정 ==========

  // 테스트할 Read Timeout 값들 (밀리초)
  // 1초, 3초, 5초, 10초로 점진적 증가
  private final int[] timeoutValues = {1000, 3000, 5000, 10000};

  /**
   * 테스트 모드 열거형
   *
   * Read Timeout을 유발하는 두 가지 서버 동작 모드
   */
  private enum TestMode {
    NO_RESPONSE("서버 무응답"),        // 연결 후 아무 응답 없음
    SLOW_RESPONSE("서버 느린 응답");   // 매우 천천히 응답

    private final String description;

    TestMode(String description) {
      this.description = description;
    }
  }

  // 현재 테스트 중인 모드
  private TestMode currentMode;

  // 현재 테스트 중인 타임아웃 값
  private int currentTimeout;

  // ========== 결과 저장 ==========

  // 각 테스트의 상세 결과를 저장하는 리스트
  private final List<ReadTestResult> testResults = new ArrayList<>();

  /**
   * ReadTimeoutScenario 생성자
   */
  public ReadTimeoutScenario() {
    super("Read Timeout Scenario",
        "서버가 응답하지 않거나 느리게 응답할 때 Read Timeout 테스트");
  }

  /**
   * 시나리오 준비 - 두 개의 서버 시작
   *
   * NO_RESPONSE와 SLOW_RESPONSE 모드로 각각 서버를 시작합니다.
   */
  @Override
  protected void setup() throws Exception {
    logger.info("서버들 시작 중...");

    // NO_RESPONSE 서버 시작
    // 이 서버는 연결은 받지만 데이터를 보내지 않음
    noResponseServer = new ProblematicServer(noResponsePort, ServerMode.NO_RESPONSE);
    noResponseServer.start();

    // SLOW_RESPONSE 서버 시작
    // 이 서버는 1초에 1바이트씩 매우 천천히 응답
    slowResponseServer = new ProblematicServer(slowResponsePort, ServerMode.SLOW_RESPONSE);
    slowResponseServer.start();

    // 서버들이 완전히 시작될 때까지 대기
    Thread.sleep(1000);

    logger.info("서버 준비 완료");
    logger.info("  • NO_RESPONSE 서버: Port " + noResponsePort);
    logger.info("  • SLOW_RESPONSE 서버: Port " + slowResponsePort);
  }

  /**
   * 단일 시나리오 실행
   *
   * 짝수 반복: NO_RESPONSE 모드 테스트
   * 홀수 반복: SLOW_RESPONSE 모드 테스트
   *
   * @param iteration 현재 반복 번호
   * @return 테스트 성공 여부
   */
  @Override
  protected boolean runScenario(int iteration) throws Exception {
    // ===== 테스트 설정 결정 =====

    // 짝수/홀수로 테스트 모드 결정
    currentMode = (iteration % 2 == 0) ? TestMode.NO_RESPONSE : TestMode.SLOW_RESPONSE;

    // 타임아웃 값 선택 (배열 순환)
    currentTimeout = timeoutValues[iteration % timeoutValues.length];

    // 모드에 따라 연결할 서버 포트 선택
    int port = (currentMode == TestMode.NO_RESPONSE) ? noResponsePort : slowResponsePort;

    // ===== 클라이언트 생성 및 설정 =====

    TimeoutClient client = new TimeoutClient("localhost", port);
    client.setConnectTimeout(5000);     // Connect는 충분히 길게 (연결은 성공해야 함)
    client.setReadTimeout(currentTimeout);  // Read Timeout 설정

    try {
      logger.info("\n🔄 테스트 " + (iteration + 1) +
          ": " + currentMode.description +
          ", Read Timeout = " + currentTimeout + "ms");

      // ===== 1단계: 서버 연결 =====

      if (!client.connect()) {
        // 연결 실패는 예상하지 못한 상황
        logger.error("연결 실패 (예상: 성공)");
        return false;
      }
      logger.info("✅ 연결 성공");

      // ===== 2단계: 요청 전송 =====

      // HTTP 형식의 간단한 요청 전송
      client.sendData("GET /test HTTP/1.1\r\n\r\n");
      logger.info("📤 요청 전송 완료");

      // ===== 3단계: 응답 대기 (Read Timeout 발생 예상) =====

      logger.info("📥 응답 대기 중...");

      // 응답 읽기 시작 시간 기록
      long startTime = System.currentTimeMillis();

      // 응답 읽기 시도 - 여기서 Read Timeout 발생 가능
      String response = client.receiveData();

      // 실제 대기 시간 계산
      long actualTime = System.currentTimeMillis() - startTime;

      // ===== 결과 저장 =====

      ReadTestResult result = new ReadTestResult(
          currentMode,                  // 테스트 모드
          currentTimeout,               // 설정한 타임아웃
          actualTime,                   // 실제 대기 시간
          response != null,             // 응답 수신 여부
          client.getLastException()     // 발생한 예외
      );
      testResults.add(result);

      // ===== 결과 분석 =====

      if (response == null) {
        // 응답을 받지 못함 (타임아웃 또는 오류)

        Exception lastError = client.getLastException();

        if (lastError instanceof SocketTimeoutException) {
          // Read Timeout 발생 (예상된 동작)
          timeoutCount.incrementAndGet();

          logger.info("✅ 예상대로 Read Timeout 발생 (실제 대기: " +
              actualTime + "ms)");

          // 타임아웃 정확도 검증
          long tolerance = 100;  // 100ms 오차 허용

          if (Math.abs(actualTime - currentTimeout) <= tolerance) {
            logger.info("✅ 타임아웃이 정확히 작동함");
            return true;
          } else {
            logger.warn("⚠️ 타임아웃 오차: 예상 " + currentTimeout +
                "ms, 실제 " + actualTime + "ms");
            return true;  // 타임아웃은 발생했으므로 성공
          }
        } else {
          // 다른 종류의 오류 발생
          logger.error("❌ 예상치 못한 오류: " + lastError.getMessage());
          return false;
        }
      } else {
        // 응답을 받음

        // SLOW_RESPONSE 모드에서 타임아웃이 충분히 길면 응답을 받을 수 있음
        if (currentMode == TestMode.SLOW_RESPONSE && currentTimeout >= 10000) {
          // 느린 응답이지만 타임아웃 내에 도착 (정상)
          logger.info("✅ 응답 수신 (느린 응답이지만 타임아웃 내 도착): " +
              response.substring(0, Math.min(response.length(), 50)));
          return true;
        } else {
          // 예상치 못한 응답
          logger.warn("⚠️ 예상치 못한 응답 수신");
          return false;
        }
      }

    } finally {
      // 클라이언트 정리
      client.disconnect();
    }
  }

  /**
   * 시나리오 정리 - 서버들 종료
   */
  @Override
  protected void teardown() {
    // NO_RESPONSE 서버 종료
    if (noResponseServer != null && noResponseServer.isRunning()) {
      noResponseServer.stop();
    }

    // SLOW_RESPONSE 서버 종료
    if (slowResponseServer != null && slowResponseServer.isRunning()) {
      slowResponseServer.stop();
    }
  }

  /**
   * 추가 결과 출력 - Read Timeout 특화 통계
   */
  @Override
  protected void printAdditionalResults() {
    System.out.println("\n🔍 Read Timeout 상세 결과:");

    // ===== NO_RESPONSE 모드 결과 =====

    System.out.println("\n📌 NO_RESPONSE 모드 (서버 무응답):");
    System.out.println("┌─────────────┬──────────────┬──────────┬──────────────┐");
    System.out.println("│ Timeout 설정 │  실제 대기시간  │   결과    │     오차      │");
    System.out.println("├─────────────┼──────────────┼──────────┼──────────────┤");

    printModeResults(TestMode.NO_RESPONSE);

    System.out.println("└─────────────┴──────────────┴──────────┴──────────────┘");

    // ===== SLOW_RESPONSE 모드 결과 =====

    System.out.println("\n📌 SLOW_RESPONSE 모드 (느린 응답):");
    System.out.println("┌─────────────┬──────────────┬──────────┬──────────────┐");
    System.out.println("│ Timeout 설정 │  실제 대기시간  │   결과    │     비고      │");
    System.out.println("├─────────────┼──────────────┼──────────┼──────────────┤");

    printModeResults(TestMode.SLOW_RESPONSE);

    System.out.println("└─────────────┴──────────────┴──────────┴──────────────┘");

    // 종합 분석
    analyzeResults();
  }

  /**
   * 특정 모드의 결과를 테이블 형식으로 출력
   *
   * @param mode 출력할 테스트 모드
   */
  private void printModeResults(TestMode mode) {
    // 각 타임아웃 값별로 결과 집계
    for (int timeoutValue : timeoutValues) {
      // 해당 모드와 타임아웃 값에 해당하는 결과들 필터링
      List<ReadTestResult> results = testResults.stream()
          .filter(r -> r.mode == mode && r.configuredTimeout == timeoutValue)
          .toList();

      if (!results.isEmpty()) {
        // 평균 실제 대기 시간 계산
        double avgActual = results.stream()
            .mapToLong(r -> r.actualTime)
            .average()
            .orElse(0);

        // 모든 테스트가 타임아웃되었는지 확인
        boolean allTimeout = results.stream()
            .allMatch(r -> !r.receivedResponse);

        if (mode == TestMode.NO_RESPONSE) {
          // NO_RESPONSE 모드: 오차 계산 및 출력
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
          // SLOW_RESPONSE 모드: 응답 가능 여부 판단
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

  /**
   * 전체 결과 분석 및 통계 출력
   */
  private void analyzeResults() {
    System.out.println("\n💡 분석:");

    // ===== NO_RESPONSE 모드 분석 =====

    // NO_RESPONSE 모드에서 타임아웃 발생 횟수 계산
    long noResponseTimeouts = testResults.stream()
        .filter(r -> r.mode == TestMode.NO_RESPONSE && !r.receivedResponse)
        .count();

    // NO_RESPONSE 모드 전체 테스트 횟수
    long noResponseTotal = testResults.stream()
        .filter(r -> r.mode == TestMode.NO_RESPONSE)
        .count();

    // 타임아웃 발생률 계산 및 출력
    System.out.println("  • NO_RESPONSE 모드 타임아웃 발생률: " +
        String.format("%.1f%%", (noResponseTimeouts * 100.0 / noResponseTotal)));

    // ===== SLOW_RESPONSE 모드 분석 =====

    // SLOW_RESPONSE 모드에서 타임아웃 발생 횟수 계산
    long slowResponseTimeouts = testResults.stream()
        .filter(r -> r.mode == TestMode.SLOW_RESPONSE && !r.receivedResponse)
        .count();

    // SLOW_RESPONSE 모드 전체 테스트 횟수
    long slowResponseTotal = testResults.stream()
        .filter(r -> r.mode == TestMode.SLOW_RESPONSE)
        .count();

    // 타임아웃 발생률 계산 및 출력
    System.out.println("  • SLOW_RESPONSE 모드 타임아웃 발생률: " +
        String.format("%.1f%%", (slowResponseTimeouts * 100.0 / slowResponseTotal)));

    // ===== 타임아웃 정확도 분석 =====

    // 타임아웃이 발생한 케이스들의 평균 오차 계산
    double avgError = testResults.stream()
        .filter(r -> !r.receivedResponse && r.exception instanceof SocketTimeoutException)
        .mapToDouble(r -> Math.abs(r.actualTime - r.configuredTimeout))
        .average()
        .orElse(0);

    System.out.println("  • 평균 Read Timeout 오차: " +
        String.format("%.2fms", avgError));

    // 정확도 평가
    if (avgError < 50) {
      System.out.println("  • Read Timeout 정확도: 🟢 매우 정확");
    } else if (avgError < 100) {
      System.out.println("  • Read Timeout 정확도: 🟡 양호");
    } else {
      System.out.println("  • Read Timeout 정확도: 🔴 부정확");
    }

    // ===== 핵심 발견 사항 =====

    System.out.println("\n📝 핵심 발견:");
    System.out.println("  • Read Timeout은 데이터 수신 대기 시간을 제어합니다");
    System.out.println("  • 서버가 느리게 응답하는 경우, 충분한 타임아웃 설정이 필요합니다");
    System.out.println("  • 무응답 서버의 경우, 짧은 타임아웃으로 빠른 실패 처리가 가능합니다");
  }

  /**
   * 테스트 결과 저장 클래스
   */
  private static class ReadTestResult {
    final TestMode mode;              // 테스트 모드
    final int configuredTimeout;      // 설정한 타임아웃
    final long actualTime;            // 실제 대기 시간
    final boolean receivedResponse;   // 응답 수신 여부
    final Exception exception;        // 발생한 예외

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

    // 2가지 모드 × 4가지 타임아웃 값 × 2회 = 총 16회
    scenario.setIterations(16);

    // Read 작업은 워밍업이 도움이 됨
    scenario.setWarmupIterations(2);

    scenario.execute();
  }
}