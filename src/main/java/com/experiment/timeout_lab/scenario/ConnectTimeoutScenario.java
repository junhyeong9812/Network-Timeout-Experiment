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
 * 이 시나리오는 TCP 3-way handshake 과정에서 발생하는 Connect Timeout을 실험합니다.
 *
 * TCP 연결 과정:
 * 1. Client → Server: SYN (연결 요청)
 * 2. Server → Client: SYN-ACK (연결 수락)  ← 이 응답이 오지 않으면 Connect Timeout!
 * 3. Client → Server: ACK (확인)
 *
 * 테스트 방법:
 * - 서버가 accept()를 하지 않는 NO_ACCEPT 모드로 실행
 * - 클라이언트가 connect()를 시도하면 SYN을 보내지만 SYN-ACK가 오지 않음
 * - 설정된 Connect Timeout 시간만큼 기다린 후 SocketTimeoutException 발생
 *
 * @author Timeout Lab Team
 */
public class ConnectTimeoutScenario extends BaseScenario {

  // ========== 서버 관련 필드 ==========

  // 문제 상황을 시뮬레이션하는 서버 인스턴스
  private ProblematicServer server;

  // 테스트용 서버 포트 (다른 시나리오와 충돌 방지를 위해 8081 사용)
  private final int serverPort = 8081;

  // ========== 테스트 설정 ==========

  // 테스트할 타임아웃 값들 (밀리초 단위)
  // 1초, 3초, 5초, 10초, 30초로 점진적으로 증가
  private final int[] timeoutValues = {1000, 3000, 5000, 10000, 30000};

  // 현재 테스트 중인 타임아웃 값
  private int currentTimeout = 5000;

  // ========== 결과 저장 ==========

  // 각 테스트의 상세 결과를 저장하는 리스트
  private final List<TimeoutTestResult> testResults = new ArrayList<>();

  /**
   * ConnectTimeoutScenario 생성자
   *
   * 부모 클래스에 시나리오 이름과 설명을 전달합니다.
   */
  public ConnectTimeoutScenario() {
    super("Connect Timeout Scenario",  // 시나리오 이름
        "서버가 accept()를 하지 않을 때 Connect Timeout 테스트");  // 설명
  }

  /**
   * 시나리오 준비 - 서버 시작
   *
   * NO_ACCEPT 모드로 서버를 시작합니다.
   * 이 모드에서는 서버 소켓은 열려있지만 accept()를 하지 않아
   * 클라이언트의 연결 요청을 처리하지 않습니다.
   */
  @Override
  protected void setup() throws Exception {
    logger.info("서버 시작 중... (NO_ACCEPT 모드)");

    // NO_ACCEPT 모드로 서버 생성
    // 이 서버는 포트는 열지만 accept()를 하지 않음
    server = new ProblematicServer(serverPort, ServerMode.NO_ACCEPT);
    server.start();

    // 서버가 완전히 시작될 때까지 1초 대기
    Thread.sleep(1000);

    logger.info("서버 준비 완료 (Port: " + serverPort + ")");
  }

  /**
   * 단일 시나리오 실행
   *
   * 각 반복마다 다른 타임아웃 값으로 테스트를 수행합니다.
   * 예상 동작: 모든 연결 시도가 Connect Timeout으로 실패해야 합니다.
   *
   * @param iteration 현재 반복 번호 (0부터 시작)
   * @return 테스트 성공 여부 (타임아웃이 발생하면 성공)
   */
  @Override
  protected boolean runScenario(int iteration) throws Exception {
    // 이번 테스트에서 사용할 타임아웃 값 선택
    // 배열을 순환하면서 각 타임아웃 값을 테스트
    currentTimeout = timeoutValues[iteration % timeoutValues.length];

    // 클라이언트 생성 및 타임아웃 설정
    TimeoutClient client = new TimeoutClient("localhost", serverPort);
    client.setConnectTimeout(currentTimeout);  // Connect Timeout 설정

    try {
      // 테스트 시작 로깅
      logger.info("\n🔄 테스트 " + (iteration + 1) +
          ": Connect Timeout = " + currentTimeout + "ms");

      // ===== 핵심 테스트 로직 =====

      // 연결 시작 시간 기록
      long startTime = System.currentTimeMillis();

      // 연결 시도 - 이때 Connect Timeout이 발생해야 함
      boolean connected = client.connect();

      // 실제 대기 시간 계산
      long actualTime = System.currentTimeMillis() - startTime;

      // ===== 결과 저장 =====

      // 이번 테스트 결과를 객체로 저장
      TimeoutTestResult result = new TimeoutTestResult(
          currentTimeout,           // 설정한 타임아웃 값
          actualTime,               // 실제 대기 시간
          connected,                // 연결 성공 여부 (false 예상)
          client.getLastException() // 발생한 예외
      );
      testResults.add(result);

      // ===== 결과 분석 =====

      if (!connected) {
        // 연결 실패 (예상된 동작)

        Exception lastError = client.getLastException();

        // SocketTimeoutException이 발생했는지 확인
        if (lastError instanceof SocketTimeoutException) {
          // 예상대로 Connect Timeout 발생
          timeoutCount.incrementAndGet();  // 타임아웃 카운트 증가

          logger.info("✅ 예상대로 Connect Timeout 발생 (실제 대기: " +
              actualTime + "ms)");

          // 타임아웃 정확도 검증
          long tolerance = 100;  // 100ms 오차 허용

          // 실제 대기 시간과 설정값의 차이 계산
          if (Math.abs(actualTime - currentTimeout) <= tolerance) {
            logger.info("✅ 타임아웃이 정확히 작동함");
            return true;  // 테스트 성공
          } else {
            // 오차가 허용 범위를 초과
            logger.warn("⚠️ 타임아웃 오차 발생: 예상 " + currentTimeout +
                "ms, 실제 " + actualTime + "ms");
            return true;  // 타임아웃은 발생했으므로 성공으로 처리
          }
        } else {
          // SocketTimeoutException이 아닌 다른 예외 발생
          logger.error("❌ 예상치 못한 오류: " + lastError.getMessage());
          return false;  // 테스트 실패
        }
      } else {
        // 연결 성공 (예상하지 못한 동작)
        logger.error("❌ 연결이 성공함 (예상: 실패)");
        client.disconnect();
        return false;  // 테스트 실패
      }

    } finally {
      // 테스트 종료 후 클라이언트 정리
      client.disconnect();
    }
  }

  /**
   * 시나리오 정리 - 서버 종료
   *
   * 모든 테스트가 끝난 후 서버를 종료합니다.
   */
  @Override
  protected void teardown() {
    if (server != null && server.isRunning()) {
      logger.info("서버 종료 중...");
      server.stop();
    }
  }

  /**
   * 추가 결과 출력 - Connect Timeout 특화 통계
   *
   * BaseScenario의 기본 통계 외에 타임아웃 값별 상세 분석을 출력합니다.
   */
  @Override
  protected void printAdditionalResults() {
    System.out.println("\n🔍 타임아웃별 상세 결과:");

    // 테이블 헤더 출력
    System.out.println("┌─────────────┬──────────────┬──────────┬──────────────┐");
    System.out.println("│ Timeout 설정 │  실제 대기시간  │   결과    │     오차      │");
    System.out.println("├─────────────┼──────────────┼──────────┼──────────────┤");

    // 각 타임아웃 값별로 결과를 그룹화하여 출력
    for (int timeoutValue : timeoutValues) {
      // 현재 타임아웃 값에 해당하는 결과들만 필터링
      List<TimeoutTestResult> results = testResults.stream()
          .filter(r -> r.configuredTimeout == timeoutValue)
          .toList();

      if (!results.isEmpty()) {
        // 평균 실제 대기 시간 계산
        double avgActual = results.stream()
            .mapToLong(r -> r.actualTime)
            .average()
            .orElse(0);

        // 평균 오차 계산
        double avgError = Math.abs(avgActual - timeoutValue);

        // 오차 백분율 계산
        double errorPercent = (avgError / timeoutValue) * 100;

        // 테이블 행 출력
        System.out.printf("│ %11dms │ %12.0fms │ %8s │ %6.0fms (%3.1f%%) │%n",
            timeoutValue,                                           // 설정값
            avgActual,                                              // 평균 실제값
            results.stream().allMatch(r -> !r.connected) ? "TIMEOUT" : "MIXED",  // 결과
            avgError,                                               // 오차
            errorPercent                                            // 오차율
        );
      }
    }

    // 테이블 하단
    System.out.println("└─────────────┴──────────────┴──────────┴──────────────┘");

    // ===== 분석 결과 출력 =====
    System.out.println("\n💡 분석:");

    // 모든 Connect Timeout이 정상 작동했는지 확인
    System.out.println("  • 모든 Connect Timeout이 정상 작동: " +
        (timeoutCount.get() == totalRuns ? "✅ YES" : "❌ NO"));

    if (timeoutCount.get() > 0) {
      // 평균 타임아웃 오차 계산
      double avgError = testResults.stream()
          .mapToDouble(r -> Math.abs(r.actualTime - r.configuredTimeout))
          .average()
          .orElse(0);

      System.out.println("  • 평균 타임아웃 오차: " +
          String.format("%.2fms", avgError));

      // 타임아웃 정확도 평가
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
   *
   * 각 테스트의 상세 정보를 저장하여 나중에 분석할 수 있도록 합니다.
   */
  private static class TimeoutTestResult {
    final int configuredTimeout;    // 설정한 타임아웃 값
    final long actualTime;          // 실제 대기 시간
    final boolean connected;        // 연결 성공 여부
    final Exception exception;      // 발생한 예외

    /**
     * TimeoutTestResult 생성자
     */
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
   *
   * 이 시나리오만 개별적으로 테스트할 때 사용합니다.
   */
  public static void main(String[] args) {
    ConnectTimeoutScenario scenario = new ConnectTimeoutScenario();

    // 5가지 타임아웃 값을 각각 3번씩 테스트 (총 15회)
    scenario.setIterations(15);

    // Connect는 JVM 최적화의 영향을 덜 받으므로 워밍업 불필요
    scenario.setWarmupIterations(0);

    // 시나리오 실행
    scenario.execute();
  }
}