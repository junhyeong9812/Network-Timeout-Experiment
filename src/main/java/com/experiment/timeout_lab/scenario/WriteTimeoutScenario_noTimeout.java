package com.experiment.timeout_lab.scenario;

import com.experiment.timeout_lab.client.TimeoutClient;
import com.experiment.timeout_lab.server.ProblematicServer;
import com.experiment.timeout_lab.util.Constants.ServerMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Write Timeout 시나리오 (원본 버전 - 타임아웃 미구현)
 *
 * 이 클래스는 Write Timeout이 구현되지 않은 원래 버전입니다.
 * 1MB + PARTIAL_READ 조합에서 무한 블로킹 문제가 발생하여
 * 테스트가 20분 이상 멈추는 치명적인 문제가 있었습니다.
 *
 * ⚠️ 경고: 이 코드는 교육 목적으로만 유지됩니다.
 * 실제 사용 시 무한 블로킹으로 인한 서비스 장애가 발생할 수 있습니다.
 *
 * 문제 상황:
 * 1. 서버가 10바이트만 읽고 멈춤 (PARTIAL_READ)
 * 2. 클라이언트가 1MB 전송 시도
 * 3. TCP 송신 버퍼가 가득 참 (999,990 바이트 대기)
 * 4. write() 메서드가 무한 블로킹
 * 5. 테스트 프로세스 전체가 멈춤
 *
 * @author Timeout Lab Team
 * @deprecated Write Timeout 미구현으로 인한 무한 블로킹 위험
 */
@Deprecated
public class WriteTimeoutScenario_noTimeout extends BaseScenario {

  // ========== 서버 인스턴스 ==========

  /**
   * SLOW_READ 모드 서버
   * 이 서버는 클라이언트 데이터를 10초에 1바이트씩 매우 천천히 읽습니다.
   * TCP 흐름 제어로 인해 전송 속도가 느려지는 현상을 시뮬레이션합니다.
   */
  private ProblematicServer slowReadServer;

  /**
   * PARTIAL_READ 모드 서버
   * 이 서버는 10바이트만 읽고 완전히 멈춥니다.
   * TCP 버퍼가 가득 차서 write()가 블로킹되는 상황을 유발합니다.
   * ⚠️ 이것이 바로 무한 블로킹의 원인입니다!
   */
  private ProblematicServer partialReadServer;

  // 각 서버가 사용할 포트 번호
  private final int slowReadPort = 8084;      // SLOW_READ 서버 포트
  private final int partialReadPort = 8085;   // PARTIAL_READ 서버 포트

  // ========== 테스트 설정 열거형 ==========

  /**
   * 테스트할 데이터 크기 열거형
   *
   * 다양한 크기의 데이터로 TCP 버퍼 동작을 관찰합니다.
   * 작은 데이터는 버퍼에 즉시 들어가지만,
   * 큰 데이터는 버퍼가 가득 차면 블로킹될 수 있습니다.
   */
  private enum DataSize {
    SMALL(100, "100 bytes"),           // 작은 데이터 - 버퍼에 즉시 들어감
    MEDIUM(10_000, "10 KB"),          // 중간 크기 - 대부분 문제없음
    LARGE(100_000, "100 KB"),         // 큰 데이터 - 버퍼 크기에 따라 다름
    VERY_LARGE(1_000_000, "1 MB");    // 매우 큰 데이터 - 블로킹 위험! ⚠️

    final int bytes;                   // 실제 바이트 수
    final String description;          // 사용자에게 표시할 설명

    DataSize(int bytes, String description) {
      this.bytes = bytes;
      this.description = description;
    }
  }

  /**
   * 테스트 모드 열거형
   *
   * 서버가 데이터를 읽는 방식을 정의합니다.
   */
  private enum TestMode {
    SLOW_READ("서버가 매우 천천히 읽음"),      // 10초에 1바이트 - 느리지만 진행됨
    PARTIAL_READ("서버가 일부만 읽고 멈춤");   // 10바이트만 읽음 - 블로킹 유발! ⚠️

    private final String description;

    TestMode(String description) {
      this.description = description;
    }
  }

  // 현재 실행 중인 테스트 설정
  private TestMode currentMode;          // 현재 테스트 모드 (SLOW_READ or PARTIAL_READ)
  private DataSize currentDataSize;      // 현재 테스트 데이터 크기

  // 테스트 결과를 저장할 리스트
  private final List<WriteTestResult> testResults = new ArrayList<>();

  /**
   * 생성자
   * BaseScenario를 상속받아 시나리오 이름과 설명을 설정합니다.
   */
  public WriteTimeoutScenario_noTimeout() {
    super("Write Timeout Scenario (No Timeout)",
        "타임아웃 미구현 버전 - 무한 블로킹 위험");
  }

  /**
   * 시나리오 준비 단계
   * 두 개의 문제 서버를 시작합니다.
   */
  @Override
  protected void setup() throws Exception {
    logger.info("서버들 시작 중...");

    // SLOW_READ 서버 시작
    // 이 서버는 데이터를 10초에 1바이트씩 읽습니다
    slowReadServer = new ProblematicServer(slowReadPort, ServerMode.SLOW_READ);
    slowReadServer.start();

    // PARTIAL_READ 서버 시작
    // 이 서버는 10바이트만 읽고 더 이상 읽지 않습니다
    // ⚠️ 주의: 1MB 데이터 전송 시 나머지 999,990 바이트가 버퍼에 갇힙니다!
    partialReadServer = new ProblematicServer(partialReadPort, ServerMode.PARTIAL_READ);
    partialReadServer.start();

    // 서버가 완전히 시작될 때까지 대기
    Thread.sleep(1000);

    logger.info("서버 준비 완료");
    logger.info("  • SLOW_READ 서버: Port " + slowReadPort);
    logger.info("  • PARTIAL_READ 서버: Port " + partialReadPort);
  }

  /**
   * 단일 시나리오 실행
   *
   * ⚠️ 핵심 문제: 이 메서드는 Write Timeout이 없어서
   * PARTIAL_READ + 1MB 조합에서 무한 블로킹됩니다!
   *
   * @param iteration 현재 반복 번호 (0부터 시작)
   * @return 테스트 성공 여부
   */
  @Override
  protected boolean runScenario(int iteration) throws Exception {
    // ===== 테스트 설정 결정 =====

    // 짝수번째는 SLOW_READ, 홀수번째는 PARTIAL_READ 모드로 테스트
    currentMode = (iteration % 2 == 0) ? TestMode.SLOW_READ : TestMode.PARTIAL_READ;

    // 데이터 크기를 순환하면서 선택 (100B → 10KB → 100KB → 1MB → 반복)
    DataSize[] sizes = DataSize.values();
    currentDataSize = sizes[iteration % sizes.length];

    // 테스트 모드에 따라 연결할 서버 포트 선택
    int port = (currentMode == TestMode.SLOW_READ) ? slowReadPort : partialReadPort;

    // ===== 클라이언트 생성 및 설정 =====

    TimeoutClient client = new TimeoutClient("localhost", port);
    client.setConnectTimeout(5000);    // 연결 타임아웃: 5초
    client.setReadTimeout(30000);      // 읽기 타임아웃: 30초
    // ⚠️ 주목: Write Timeout은 설정할 방법이 없음!
    // Java Socket API의 한계로 setSendTimeout() 같은 메서드가 없습니다

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

      // 지정된 크기만큼 데이터 생성 (0123456789ABCDEF 패턴 반복)
      String data = generateData(currentDataSize.bytes);
      logger.info("📤 데이터 전송 시작 (" + currentDataSize.description + ")");

      // ===== 3단계: 데이터 전송 (Write 동작) =====
      // ⚠️ 문제의 핵심 부분!

      // 전송 시작 시간 기록
      long startTime = System.currentTimeMillis();

      // 데이터 전송 시도
      // ⚠️ 위험: 이 부분에서 무한 블로킹 가능!
      // PARTIAL_READ + 1MB 조합에서 write()가 영원히 블로킹됩니다
      boolean sent = client.sendData(data);

      // 전송 소요 시간 계산
      // ⚠️ 문제: 블로킹되면 이 라인에 도달하지 못함!
      long writeTime = System.currentTimeMillis() - startTime;

      // ===== 결과 저장 =====

      WriteTestResult result = new WriteTestResult(
          currentMode,                  // 테스트 모드
          currentDataSize,              // 데이터 크기
          writeTime,                    // 전송 시간 (블로킹되면 측정 불가)
          sent,                         // 전송 성공 여부
          client.getLastException()     // 발생한 예외
      );
      testResults.add(result);

      // ===== 결과 분석 및 출력 =====

      if (sent) {
        logger.info("✅ 데이터 전송 완료 (소요시간: " + writeTime + "ms)");

        // 전송 속도 계산 (KB/s)
        double throughput = (currentDataSize.bytes / 1024.0) / (writeTime / 1000.0);
        logger.info("📊 전송 속도: " + String.format("%.2f KB/s", throughput));

        // SLOW_READ 모드에서 전송이 오래 걸린 경우 경고
        if (currentMode == TestMode.SLOW_READ && writeTime > 10000) {
          logger.warn("⚠️ 전송이 매우 느림 (서버가 천천히 읽는 중)");
        }

        return true;
      } else {
        logger.error("❌ 데이터 전송 실패");
        return false;
      }

    } finally {
      // 연결 종료
      // ⚠️ 문제: write()가 블로킹되면 여기도 도달하지 못함!
      client.disconnect();
    }
  }

  /**
   * 테스트용 데이터 생성
   *
   * 지정된 크기의 문자열을 생성합니다.
   * "0123456789ABCDEF" 패턴을 반복하여 원하는 크기를 만듭니다.
   *
   * @param size 생성할 데이터 크기 (바이트)
   * @return 생성된 문자열
   */
  private String generateData(int size) {
    StringBuilder sb = new StringBuilder(size);
    String pattern = "0123456789ABCDEF";  // 16자 패턴

    // 패턴을 반복하여 원하는 크기만큼 채움
    for (int i = 0; i < size; i++) {
      sb.append(pattern.charAt(i % pattern.length()));
    }

    return sb.toString();
  }

  /**
   * 시나리오 정리 단계
   * 서버들을 종료합니다.
   *
   * ⚠️ 문제: write()가 블로킹되면 이 메서드도 실행되지 않을 수 있음!
   */
  @Override
  protected void teardown() {
    // SLOW_READ 서버 종료
    if (slowReadServer != null && slowReadServer.isRunning()) {
      slowReadServer.stop();
    }

    // PARTIAL_READ 서버 종료
    if (partialReadServer != null && partialReadServer.isRunning()) {
      partialReadServer.stop();
    }
  }

  /**
   * 추가 결과 출력 - Write 동작 특화 통계
   *
   * 테스트 완료 후 결과를 테이블 형식으로 출력합니다.
   * ⚠️ 문제: 블로킹되면 이 메서드도 실행되지 않음!
   */
  @Override
  protected void printAdditionalResults() {
    System.out.println("\n🔍 Write 동작 상세 결과:");

    // ===== SLOW_READ 모드 결과 출력 =====

    System.out.println("\n📌 SLOW_READ 모드 (서버가 천천히 읽음):");
    System.out.println("┌──────────────┬──────────────┬──────────────┬──────────────┐");
    System.out.println("│  데이터 크기   │   전송 시간    │   전송 속도    │     상태      │");
    System.out.println("├──────────────┼──────────────┼──────────────┼──────────────┤");

    printModeResults(TestMode.SLOW_READ);

    System.out.println("└──────────────┴──────────────┴──────────────┴──────────────┘");

    // ===== PARTIAL_READ 모드 결과 출력 =====

    System.out.println("\n📌 PARTIAL_READ 모드 (서버가 일부만 읽음):");
    System.out.println("┌──────────────┬──────────────┬──────────────┬──────────────┐");
    System.out.println("│  데이터 크기   │   전송 시간    │     버퍼      │     상태      │");
    System.out.println("├──────────────┼──────────────┼──────────────┼──────────────┤");

    printModeResults(TestMode.PARTIAL_READ);

    System.out.println("└──────────────┴──────────────┴──────────────┴──────────────┘");

    // 종합 분석 출력
    analyzeResults();
  }

  /**
   * 특정 모드의 결과를 테이블 형식으로 출력
   *
   * @param mode 출력할 테스트 모드
   */
  private void printModeResults(TestMode mode) {
    // 각 데이터 크기별로 결과 출력
    for (DataSize size : DataSize.values()) {
      // 해당 모드와 크기의 결과만 필터링
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
          // SLOW_READ 모드: 전송 속도 표시

          // 전송 속도 계산 (KB/s)
          double throughput = (size.bytes / 1024.0) / (avgTime / 1000.0);

          // 10초 이상 걸리면 "매우 느림"으로 표시
          String status = avgTime > 10000 ? "매우 느림" :
              avgTime > 5000 ? "느림" : "정상";

          System.out.printf("│ %12s │ %12.0fms │ %10.2f KB/s │ %12s │%n",
              size.description,
              avgTime,
              throughput,
              status
          );
        } else {
          // PARTIAL_READ 모드: 버퍼 사용 여부 표시

          // 10바이트보다 크면 버퍼에 데이터가 남음
          String bufferStatus = size.bytes > 10 ? "버퍼 사용" : "즉시 전송";

          // ⚠️ 1MB의 경우 "블로킹 위험"으로 표시해야 함!
          String status = size == DataSize.VERY_LARGE ?
              "⚠️ 블로킹 위험!" :
              allSuccess ? "전송 완료" : "실패";

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
   *
   * ⚠️ 중요: 이 메서드는 Write Timeout의 필요성을 강조해야 합니다!
   */
  private void analyzeResults() {
    System.out.println("\n💡 분석:");

    // SLOW_READ 모드 분석
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
    }

    // PARTIAL_READ 모드 분석
    List<WriteTestResult> partialReadResults = testResults.stream()
        .filter(r -> r.mode == TestMode.PARTIAL_READ)
        .toList();

    if (!partialReadResults.isEmpty()) {
      System.out.println("  • PARTIAL_READ 모드에서 대용량 데이터는 위험합니다!");
    }

    // ===== 핵심 문제점 강조 =====

    System.out.println("\n⚠️ 경고:");
    System.out.println("  • Java는 Write Timeout을 지원하지 않습니다");
    System.out.println("  • TCP 버퍼가 가득 차면 write()가 무한 블로킹됩니다");
    System.out.println("  • 1MB + PARTIAL_READ 조합은 서비스를 마비시킬 수 있습니다");
    System.out.println("  • 실제로 20분 이상 블로킹되어 수동 종료가 필요했습니다");

    System.out.println("\n🚨 실제 발생한 문제:");
    System.out.println("  • [22:22:46] 1MB 데이터 전송 시작");
    System.out.println("  • [22:22:46] 서버가 10바이트만 읽고 멈춤");
    System.out.println("  • [22:42:46] 20분 경과... 여전히 블로킹 중");
    System.out.println("  • [Ctrl+C] 수동 종료 필요");

    System.out.println("\n💡 해결 방법:");
    System.out.println("  • Future를 사용한 Write Timeout 구현");
    System.out.println("  • NIO의 비동기 I/O 사용");
    System.out.println("  • Netty 같은 프레임워크 활용");
  }

  /**
   * 테스트 결과 저장 클래스
   *
   * 각 테스트의 결과를 저장하는 불변 객체입니다.
   */
  private static class WriteTestResult {
    final TestMode mode;           // 테스트 모드 (SLOW_READ or PARTIAL_READ)
    final DataSize dataSize;       // 데이터 크기
    final long writeTime;          // 전송 소요 시간 (밀리초)
    final boolean sent;            // 전송 성공 여부
    final Exception exception;     // 발생한 예외 (있는 경우)

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
   *
   * ⚠️ 주의: 이 코드를 실행하면 무한 블로킹될 수 있습니다!
   * 테스트 중 멈추면 Ctrl+C로 강제 종료해야 합니다.
   */
  public static void main(String[] args) {
    System.out.println("⚠️ 경고: 이 테스트는 무한 블로킹될 수 있습니다!");
    System.out.println("문제 발생 시 Ctrl+C로 강제 종료하세요.");
    System.out.println();

    WriteTimeoutScenario_noTimeout scenario = new WriteTimeoutScenario_noTimeout();

    // 테스트 설정
    // 2가지 모드 × 4가지 데이터 크기 × 2회 = 총 16회
    scenario.setIterations(16);
    scenario.setWarmupIterations(2);

    // 실행
    // ⚠️ 위험: 1MB + PARTIAL_READ에서 멈출 수 있음!
    scenario.execute();
  }
}