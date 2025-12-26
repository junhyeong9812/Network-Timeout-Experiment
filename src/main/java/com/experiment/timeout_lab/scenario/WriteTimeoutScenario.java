package com.experiment.timeout_lab.scenario;

import com.experiment.timeout_lab.client.TimeoutClient;
import com.experiment.timeout_lab.server.ProblematicServer;
import com.experiment.timeout_lab.util.Constants.ServerMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Write Timeout 시나리오
 *
 * 이 시나리오는 클라이언트가 데이터를 전송할 때 발생하는 Write Timeout 상황을 실험합니다.
 *
 * 중요: Java Socket API의 Write Timeout 제한사항
 * - Java는 직접적인 Write Timeout을 지원하지 않음
 * - write() 메서드는 TCP 송신 버퍼에 데이터를 쓰는 것이지 실제 전송을 보장하지 않음
 * - TCP 송신 버퍼가 가득 찰 때만 write()가 블로킹됨
 *
 * TCP 버퍼 동작 원리:
 * 1. 클라이언트 write() → TCP 송신 버퍼 → 네트워크 → TCP 수신 버퍼 → 서버 read()
 * 2. 서버가 read()를 하지 않으면 TCP 수신 버퍼가 가득 참
 * 3. TCP Flow Control에 의해 클라이언트의 송신 버퍼도 가득 참
 * 4. 송신 버퍼가 가득 차면 write()가 블로킹됨
 *
 * 테스트 모드:
 * - SLOW_READ: 서버가 10초에 1바이트씩 매우 천천히 읽음
 * - PARTIAL_READ: 서버가 10바이트만 읽고 멈춤
 *
 * @author Timeout Lab Team
 */
public class WriteTimeoutScenario extends BaseScenario {

  // ========== 서버 인스턴스 ==========

  // SLOW_READ 모드 서버 (천천히 읽기)
  private ProblematicServer slowReadServer;

  // PARTIAL_READ 모드 서버 (일부만 읽기)
  private ProblematicServer partialReadServer;

  // 각 서버의 포트
  private final int slowReadPort = 8084;
  private final int partialReadPort = 8085;

  // ========== 테스트 설정 ==========

  /**
   * 테스트 데이터 크기 열거형
   *
   * 다양한 크기의 데이터로 테스트하여 버퍼 동작을 관찰
   */
  private enum DataSize {
    SMALL(100, "100 bytes"),           // 작은 데이터 (버퍼에 즉시 들어감)
    MEDIUM(10_000, "10 KB"),           // 중간 크기
    LARGE(100_000, "100 KB"),          // 큰 데이터
    VERY_LARGE(1_000_000, "1 MB");     // 매우 큰 데이터 (버퍼 초과 가능)

    final int bytes;              // 바이트 수
    final String description;     // 표시용 설명

    DataSize(int bytes, String description) {
      this.bytes = bytes;
      this.description = description;
    }
  }

  /**
   * 테스트 모드 열거형
   */
  private enum TestMode {
    SLOW_READ("서버가 매우 천천히 읽음"),      // 10초에 1바이트
    PARTIAL_READ("서버가 일부만 읽고 멈춤");   // 10바이트만 읽음

    private final String description;

    TestMode(String description) {
      this.description = description;
    }
  }

  // 현재 테스트 중인 설정
  private TestMode currentMode;
  private DataSize currentDataSize;

  // ========== 결과 저장 ==========

  private final List<WriteTestResult> testResults = new ArrayList<>();

  /**
   * WriteTimeoutScenario 생성자
   */
  public WriteTimeoutScenario() {
    super("Write Timeout Scenario",
        "서버가 데이터를 읽지 않거나 천천히 읽을 때 Write 동작 테스트");
  }

  /**
   * 시나리오 준비 - 두 개의 서버 시작
   */
  @Override
  protected void setup() throws Exception {
    logger.info("서버들 시작 중...");

    // SLOW_READ 서버 시작 (10초에 1바이트씩 읽음)
    slowReadServer = new ProblematicServer(slowReadPort, ServerMode.SLOW_READ);
    slowReadServer.start();

    // PARTIAL_READ 서버 시작 (10바이트만 읽고 멈춤)
    partialReadServer = new ProblematicServer(partialReadPort, ServerMode.PARTIAL_READ);
    partialReadServer.start();

    Thread.sleep(1000);

    logger.info("서버 준비 완료");
    logger.info("  • SLOW_READ 서버: Port " + slowReadPort);
    logger.info("  • PARTIAL_READ 서버: Port " + partialReadPort);
  }

  /**
   * 단일 시나리오 실행
   *
   * 각 반복마다 다른 모드와 데이터 크기로 테스트
   *
   * @param iteration 현재 반복 번호
   * @return 테스트 성공 여부
   */
  @Override
  protected boolean runScenario(int iteration) throws Exception {
    // ===== 테스트 설정 결정 =====

    // 짝수/홀수로 모드 결정
    currentMode = (iteration % 2 == 0) ? TestMode.SLOW_READ : TestMode.PARTIAL_READ;

    // 데이터 크기 순환 선택
    DataSize[] sizes = DataSize.values();
    currentDataSize = sizes[iteration % sizes.length];

    // 모드에 따른 포트 선택
    int port = (currentMode == TestMode.SLOW_READ) ? slowReadPort : partialReadPort;

    // ===== 클라이언트 생성 및 설정 =====

    TimeoutClient client = new TimeoutClient("localhost", port);
    client.setConnectTimeout(5000);    // 연결은 빠르게
    client.setReadTimeout(30000);      // 읽기는 충분히 길게 (Write 테스트이므로)

    try {
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

      // ===== 3단계: 데이터 전송 (Write 동작) =====

      // 전송 시작 시간 기록
      long startTime = System.currentTimeMillis();

      // 데이터 전송 시도
      // 작은 데이터: 즉시 버퍼에 들어감 (빠름)
      // 큰 데이터 + SLOW_READ: 버퍼가 차서 블로킹 가능 (느림)
      boolean sent = client.sendData(data);

      // 전송 소요 시간 계산
      long writeTime = System.currentTimeMillis() - startTime;

      // ===== 결과 저장 =====

      WriteTestResult result = new WriteTestResult(
          currentMode,                  // 테스트 모드
          currentDataSize,              // 데이터 크기
          writeTime,                    // 전송 시간
          sent,                         // 전송 성공 여부
          client.getLastException()     // 발생한 예외
      );
      testResults.add(result);

      // ===== 결과 분석 =====

      if (sent) {
        logger.info("✅ 데이터 전송 완료 (소요시간: " + writeTime + "ms)");

        // 전송 속도 계산 (KB/s)
        double throughput = (currentDataSize.bytes / 1024.0) / (writeTime / 1000.0);
        logger.info("📊 전송 속도: " + String.format("%.2f KB/s", throughput));

        // SLOW_READ 모드에서 전송이 오래 걸린 경우
        if (currentMode == TestMode.SLOW_READ && writeTime > 10000) {
          logger.warn("⚠️ 전송이 매우 느림 (서버가 천천히 읽는 중)");
          // 이는 정상적인 동작 - TCP 버퍼가 차서 블로킹됨
        }

        return true;
      } else {
        logger.error("❌ 데이터 전송 실패");
        return false;
      }

    } finally {
      client.disconnect();
    }
  }

  /**
   * 테스트용 데이터 생성
   *
   * 지정된 크기의 문자열을 생성합니다.
   * 패턴: "0123456789ABCDEF" 반복
   *
   * @param size 생성할 데이터 크기 (바이트)
   * @return 생성된 문자열
   */
  private String generateData(int size) {
    StringBuilder sb = new StringBuilder(size);
    String pattern = "0123456789ABCDEF";  // 16자 패턴

    // 패턴을 반복하여 원하는 크기의 문자열 생성
    for (int i = 0; i < size; i++) {
      sb.append(pattern.charAt(i % pattern.length()));
    }

    return sb.toString();
  }

  /**
   * 시나리오 정리 - 서버들 종료
   */
  @Override
  protected void teardown() {
    if (slowReadServer != null && slowReadServer.isRunning()) {
      slowReadServer.stop();
    }
    if (partialReadServer != null && partialReadServer.isRunning()) {
      partialReadServer.stop();
    }
  }

  /**
   * 추가 결과 출력 - Write 동작 특화 통계
   */
  @Override
  protected void printAdditionalResults() {
    System.out.println("\n🔍 Write 동작 상세 결과:");

    // ===== SLOW_READ 모드 결과 =====

    System.out.println("\n📌 SLOW_READ 모드 (서버가 천천히 읽음):");
    System.out.println("┌──────────────┬──────────────┬──────────────┬──────────────┐");
    System.out.println("│  데이터 크기   │   전송 시간    │   전송 속도    │     상태      │");
    System.out.println("├──────────────┼──────────────┼──────────────┼──────────────┤");

    printModeResults(TestMode.SLOW_READ);

    System.out.println("└──────────────┴──────────────┴──────────────┴──────────────┘");

    // ===== PARTIAL_READ 모드 결과 =====

    System.out.println("\n📌 PARTIAL_READ 모드 (서버가 일부만 읽음):");
    System.out.println("┌──────────────┬──────────────┬──────────────┬──────────────┐");
    System.out.println("│  데이터 크기   │   전송 시간    │     버퍼      │     상태      │");
    System.out.println("├──────────────┼──────────────┼──────────────┼──────────────┤");

    printModeResults(TestMode.PARTIAL_READ);

    System.out.println("└──────────────┴──────────────┴──────────────┴──────────────┘");

    // 종합 분석
    analyzeResults();
  }

  /**
   * 특정 모드의 결과를 테이블 형식으로 출력
   */
  private void printModeResults(TestMode mode) {
    for (DataSize size : DataSize.values()) {
      // 해당 모드와 데이터 크기에 대한 결과 필터링
      List<WriteTestResult> results = testResults.stream()
          .filter(r -> r.mode == mode && r.dataSize == size)
          .toList();

      if (!results.isEmpty()) {
        // 평균 전송 시간 계산
        double avgTime = results.stream()
            .mapToLong(r -> r.writeTime)
            .average()
            .orElse(0);

        // 모든 전송이 성공했는지 확인
        boolean allSuccess = results.stream()
            .allMatch(r -> r.sent);

        if (mode == TestMode.SLOW_READ) {
          // SLOW_READ 모드: 전송 속도와 상태 표시

          // 전송 속도 계산 (KB/s)
          double throughput = (size.bytes / 1024.0) / (avgTime / 1000.0);

          // 상태 판단 (10초 이상이면 매우 느림)
          String status = avgTime > 10000 ? "매우 느림" :
              avgTime > 5000 ? "느림" : "정상";

          System.out.printf("│ %12s │ %12.0fms │ %10.2f KB/s │ %12s │%n",
              size.description,
              avgTime,
              throughput,
              status
          );
        } else {
          // PARTIAL_READ 모드: 버퍼 사용 여부와 상태 표시

          // 10바이트보다 크면 버퍼 사용
          String bufferStatus = size.bytes > 10 ? "버퍼 사용" : "즉시 전송";
          String status = allSuccess ? "전송 완료" : "일부 실패";

          System.out.printf("│ %12s │ %12.0fms │ %12s │ %12s │%n",
              size.description,
              avgTime,
              bufferStatus,
              status
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

    // ===== SLOW_READ 모드 분석 =====

    List<WriteTestResult> slowReadResults = testResults.stream()
        .filter(r -> r.mode == TestMode.SLOW_READ)
        .toList();

    if (!slowReadResults.isEmpty()) {
      // 평균 전송 시간 계산
      double avgSlowTime = slowReadResults.stream()
          .mapToLong(r -> r.writeTime)
          .average()
          .orElse(0);

      System.out.println("  • SLOW_READ 모드 평균 전송 시간: " +
          String.format("%.0fms", avgSlowTime));

      System.out.println("  • 데이터 크기가 클수록 SLOW_READ의 영향이 큽니다");
    }

    // ===== PARTIAL_READ 모드 분석 =====

    List<WriteTestResult> partialReadResults = testResults.stream()
        .filter(r -> r.mode == TestMode.PARTIAL_READ)
        .toList();

    if (!partialReadResults.isEmpty()) {
      // 작은 데이터의 성공률 확인
      long smallDataSuccess = partialReadResults.stream()
          .filter(r -> r.dataSize == DataSize.SMALL && r.sent)
          .count();

      // 큰 데이터의 성공률 확인
      long largeDataSuccess = partialReadResults.stream()
          .filter(r -> r.dataSize == DataSize.VERY_LARGE && r.sent)
          .count();

      System.out.println("  • PARTIAL_READ에서 작은 데이터 성공률: " +
          (smallDataSuccess > 0 ? "100%" : "0%"));
      System.out.println("  • PARTIAL_READ에서 큰 데이터는 TCP 버퍼에 의존합니다");
    }

    // ===== 핵심 발견 사항 =====

    System.out.println("\n📝 핵심 발견:");
    System.out.println("  • Java는 직접적인 Write Timeout을 지원하지 않습니다");
    System.out.println("  • TCP 버퍼가 가득 찰 때만 write()가 블로킹됩니다");
    System.out.println("  • 서버가 천천히 읽으면 전송 속도가 느려집니다");
    System.out.println("  • 대용량 데이터 전송 시 서버 처리 속도가 중요합니다");

    // ===== 권장사항 =====

    System.out.println("\n💡 권장사항:");
    System.out.println("  • 대용량 데이터 전송 시 비동기 I/O 사용 고려");
    System.out.println("  • 스트리밍 방식으로 데이터를 청크 단위로 전송");
    System.out.println("  • 진행 상황을 모니터링할 수 있는 메커니즘 구현");
  }

  /**
   * 테스트 결과 저장 클래스
   */
  private static class WriteTestResult {
    final TestMode mode;           // 테스트 모드
    final DataSize dataSize;       // 데이터 크기
    final long writeTime;          // 전송 소요 시간
    final boolean sent;            // 전송 성공 여부
    final Exception exception;     // 발생한 예외

    WriteTestResult(TestMode mode, DataSize dataSize, long writeTime,
        boolean sent, Exception exception) {
      this.mode = mode;
      this.dataSize = dataSize;
      this.writeTime = writeTime;
      this.sent = sent;
      this.exception = exception;
    }
  }

  /**
   * 단독 실행용 main 메서드
   */
  public static void main(String[] args) {
    WriteTimeoutScenario scenario = new WriteTimeoutScenario();

    // 2가지 모드 × 4가지 데이터 크기 × 2회 = 총 16회
    scenario.setIterations(16);

    // 데이터 전송도 워밍업이 도움이 됨
    scenario.setWarmupIterations(2);

    scenario.execute();
  }
}