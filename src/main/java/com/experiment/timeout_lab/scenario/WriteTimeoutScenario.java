package com.experiment.timeout_lab.scenario;

import com.experiment.timeout_lab.client.TimeoutClient;
import com.experiment.timeout_lab.server.ProblematicServer;
import com.experiment.timeout_lab.util.Constants.ServerMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Write Timeout 시나리오
 *
 * 클라이언트가 데이터를 전송할 때 서버가 읽지 않거나 매우 천천히 읽는 경우
 * 발생하는 Write Timeout을 실험합니다.
 *
 * 주의: Java Socket API는 직접적인 Write Timeout을 지원하지 않으므로,
 * TCP 버퍼가 가득 차는 상황을 시뮬레이션합니다.
 */
public class WriteTimeoutScenario extends BaseScenario {

  private ProblematicServer slowReadServer;
  private ProblematicServer partialReadServer;
  private final int slowReadPort = 8084;
  private final int partialReadPort = 8085;

  // 테스트 데이터 크기
  private enum DataSize {
    SMALL(100, "100 bytes"),           // 100 바이트
    MEDIUM(10_000, "10 KB"),           // 10 KB
    LARGE(100_000, "100 KB"),          // 100 KB
    VERY_LARGE(1_000_000, "1 MB");     // 1 MB

    final int bytes;
    final String description;

    DataSize(int bytes, String description) {
      this.bytes = bytes;
      this.description = description;
    }
  }

  private enum TestMode {
    SLOW_READ("서버가 매우 천천히 읽음"),
    PARTIAL_READ("서버가 일부만 읽고 멈춤");

    private final String description;
    TestMode(String description) {
      this.description = description;
    }
  }

  private TestMode currentMode;
  private DataSize currentDataSize;

  // 결과 저장
  private final List<WriteTestResult> testResults = new ArrayList<>();

  public WriteTimeoutScenario() {
    super("Write Timeout Scenario",
        "서버가 데이터를 읽지 않거나 천천히 읽을 때 Write 동작 테스트");
  }

  @Override
  protected void setup() throws Exception {
    logger.info("서버들 시작 중...");

    // SLOW_READ 서버 시작
    slowReadServer = new ProblematicServer(slowReadPort, ServerMode.SLOW_READ);
    slowReadServer.start();

    // PARTIAL_READ 서버 시작
    partialReadServer = new ProblematicServer(partialReadPort, ServerMode.PARTIAL_READ);
    partialReadServer.start();

    Thread.sleep(1000);
    logger.info("서버 준비 완료");
    logger.info("  • SLOW_READ 서버: Port " + slowReadPort);
    logger.info("  • PARTIAL_READ 서버: Port " + partialReadPort);
  }

  @Override
  protected boolean runScenario(int iteration) throws Exception {
    // 테스트 모드와 데이터 크기 결정
    currentMode = (iteration % 2 == 0) ? TestMode.SLOW_READ : TestMode.PARTIAL_READ;
    DataSize[] sizes = DataSize.values();
    currentDataSize = sizes[iteration % sizes.length];

    int port = (currentMode == TestMode.SLOW_READ) ? slowReadPort : partialReadPort;

    TimeoutClient client = new TimeoutClient("localhost", port);
    client.setConnectTimeout(5000);
    client.setReadTimeout(30000);  // 읽기는 충분히 길게

    try {
      logger.info("\n🔄 테스트 " + (iteration + 1) +
          ": " + currentMode.description +
          ", 데이터 크기 = " + currentDataSize.description);

      // 1. 연결
      if (!client.connect()) {
        logger.error("연결 실패");
        return false;
      }
      logger.info("✅ 연결 성공");

      // 2. 데이터 생성
      String data = generateData(currentDataSize.bytes);
      logger.info("📤 데이터 전송 시작 (" + currentDataSize.description + ")");

      // 3. 데이터 전송 (Write 동작)
      long startTime = System.currentTimeMillis();
      boolean sent = client.sendData(data);
      long writeTime = System.currentTimeMillis() - startTime;

      // 결과 저장
      WriteTestResult result = new WriteTestResult(
          currentMode, currentDataSize, writeTime, sent,
          client.getLastException()
      );
      testResults.add(result);

      // 결과 분석
      if (sent) {
        logger.info("✅ 데이터 전송 완료 (소요시간: " + writeTime + "ms)");

        // 전송 속도 계산
        double throughput = (currentDataSize.bytes / 1024.0) / (writeTime / 1000.0);
        logger.info("📊 전송 속도: " + String.format("%.2f KB/s", throughput));

        // SLOW_READ 모드에서 매우 오래 걸리는 경우
        if (currentMode == TestMode.SLOW_READ && writeTime > 10000) {
          logger.warn("⚠️ 전송이 매우 느림 (서버가 천천히 읽는 중)");
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
   */
  private String generateData(int size) {
    StringBuilder sb = new StringBuilder(size);
    String pattern = "0123456789ABCDEF";

    for (int i = 0; i < size; i++) {
      sb.append(pattern.charAt(i % pattern.length()));
    }

    return sb.toString();
  }

  @Override
  protected void teardown() {
    if (slowReadServer != null && slowReadServer.isRunning()) {
      slowReadServer.stop();
    }
    if (partialReadServer != null && partialReadServer.isRunning()) {
      partialReadServer.stop();
    }
  }

  @Override
  protected void printAdditionalResults() {
    System.out.println("\n🔍 Write 동작 상세 결과:");

    // SLOW_READ 결과
    System.out.println("\n📌 SLOW_READ 모드 (서버가 천천히 읽음):");
    System.out.println("┌──────────────┬──────────────┬──────────────┬──────────────┐");
    System.out.println("│  데이터 크기   │   전송 시간    │   전송 속도    │     상태      │");
    System.out.println("├──────────────┼──────────────┼──────────────┼──────────────┤");

    printModeResults(TestMode.SLOW_READ);

    System.out.println("└──────────────┴──────────────┴──────────────┴──────────────┘");

    // PARTIAL_READ 결과
    System.out.println("\n📌 PARTIAL_READ 모드 (서버가 일부만 읽음):");
    System.out.println("┌──────────────┬──────────────┬──────────────┬──────────────┐");
    System.out.println("│  데이터 크기   │   전송 시간    │     버퍼      │     상태      │");
    System.out.println("├──────────────┼──────────────┼──────────────┼──────────────┤");

    printModeResults(TestMode.PARTIAL_READ);

    System.out.println("└──────────────┴──────────────┴──────────────┴──────────────┘");

    // 분석
    analyzeResults();
  }

  private void printModeResults(TestMode mode) {
    for (DataSize size : DataSize.values()) {
      List<WriteTestResult> results = testResults.stream()
          .filter(r -> r.mode == mode && r.dataSize == size)
          .toList();

      if (!results.isEmpty()) {
        double avgTime = results.stream()
            .mapToLong(r -> r.writeTime)
            .average()
            .orElse(0);

        boolean allSuccess = results.stream()
            .allMatch(r -> r.sent);

        if (mode == TestMode.SLOW_READ) {
          double throughput = (size.bytes / 1024.0) / (avgTime / 1000.0);
          String status = avgTime > 10000 ? "매우 느림" :
              avgTime > 5000 ? "느림" : "정상";

          System.out.printf("│ %12s │ %12.0fms │ %10.2f KB/s │ %12s │%n",
              size.description,
              avgTime,
              throughput,
              status
          );
        } else {
          // PARTIAL_READ 모드
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

  private void analyzeResults() {
    System.out.println("\n💡 분석:");

    // SLOW_READ 분석
    List<WriteTestResult> slowReadResults = testResults.stream()
        .filter(r -> r.mode == TestMode.SLOW_READ)
        .toList();

    if (!slowReadResults.isEmpty()) {
      double avgSlowTime = slowReadResults.stream()
          .mapToLong(r -> r.writeTime)
          .average()
          .orElse(0);

      System.out.println("  • SLOW_READ 모드 평균 전송 시간: " +
          String.format("%.0fms", avgSlowTime));

      // 데이터 크기별 영향 분석
      System.out.println("  • 데이터 크기가 클수록 SLOW_READ의 영향이 큽니다");
    }

    // PARTIAL_READ 분석
    List<WriteTestResult> partialReadResults = testResults.stream()
        .filter(r -> r.mode == TestMode.PARTIAL_READ)
        .toList();

    if (!partialReadResults.isEmpty()) {
      long smallDataSuccess = partialReadResults.stream()
          .filter(r -> r.dataSize == DataSize.SMALL && r.sent)
          .count();

      long largeDataSuccess = partialReadResults.stream()
          .filter(r -> r.dataSize == DataSize.VERY_LARGE && r.sent)
          .count();

      System.out.println("  • PARTIAL_READ에서 작은 데이터 성공률: " +
          (smallDataSuccess > 0 ? "100%" : "0%"));
      System.out.println("  • PARTIAL_READ에서 큰 데이터는 TCP 버퍼에 의존합니다");
    }

    System.out.println("\n📝 핵심 발견:");
    System.out.println("  • Java는 직접적인 Write Timeout을 지원하지 않습니다");
    System.out.println("  • TCP 버퍼가 가득 찰 때만 write()가 블로킹됩니다");
    System.out.println("  • 서버가 천천히 읽으면 전송 속도가 느려집니다");
    System.out.println("  • 대용량 데이터 전송 시 서버 처리 속도가 중요합니다");

    // 권장사항
    System.out.println("\n💡 권장사항:");
    System.out.println("  • 대용량 데이터 전송 시 비동기 I/O 사용 고려");
    System.out.println("  • 스트리밍 방식으로 데이터를 청크 단위로 전송");
    System.out.println("  • 진행 상황을 모니터링할 수 있는 메커니즘 구현");
  }

  /**
   * 테스트 결과 저장 클래스
   */
  private static class WriteTestResult {
    final TestMode mode;
    final DataSize dataSize;
    final long writeTime;
    final boolean sent;
    final Exception exception;

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
    scenario.setIterations(16); // 각 모드와 데이터 크기 조합
    scenario.setWarmupIterations(2);
    scenario.execute();
  }
}