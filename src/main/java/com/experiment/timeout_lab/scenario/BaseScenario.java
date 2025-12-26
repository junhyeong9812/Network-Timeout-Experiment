package com.experiment.timeout_lab.scenario;

import com.experiment.timeout_lab.util.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 모든 타임아웃 시나리오의 기본 추상 클래스
 *
 * 이 클래스는 Template Method 패턴을 사용하여 모든 시나리오의 공통 실행 흐름을 정의합니다.
 * 각 구체적인 시나리오는 이 클래스를 상속받아 setup(), runScenario(), teardown() 메서드만 구현하면 됩니다.
 *
 * 주요 기능:
 * 1. 시나리오 실행 프레임워크 제공 (워밍업, 본 실행, 정리)
 * 2. 성능 측정 및 통계 수집
 * 3. 결과 리포팅
 *
 * @author Timeout Lab Team
 */
public abstract class BaseScenario {

  // 모든 시나리오가 공유하는 로거 인스턴스
  protected static final Logger logger = new Logger(BaseScenario.class);

  // 시나리오 메타데이터
  protected final String scenarioName;    // 시나리오 이름 (예: "Connect Timeout Scenario")
  protected final String description;     // 시나리오 설명 (예: "서버가 accept()를 하지 않을 때...")

  // ========== 실행 통계 관련 변수들 ==========

  // 총 실행 횟수 (워밍업 제외)
  protected int totalRuns = 0;

  // 성공/실패/타임아웃 카운트 (스레드 안전을 위해 AtomicInteger 사용)
  protected final AtomicInteger successCount = new AtomicInteger(0);
  protected final AtomicInteger failureCount = new AtomicInteger(0);
  protected final AtomicInteger timeoutCount = new AtomicInteger(0);

  // ========== 시간 측정 관련 변수들 ==========

  // 각 실행의 응답 시간을 저장하는 리스트
  protected final List<Long> responseTimes = new ArrayList<>();

  // 전체 실행 시간 (워밍업 제외, 밀리초)
  protected long totalExecutionTime = 0;

  // 응답 시간 통계
  protected long minResponseTime = Long.MAX_VALUE;  // 최소 응답 시간
  protected long maxResponseTime = 0;               // 최대 응답 시간
  protected double avgResponseTime = 0;             // 평균 응답 시간

  // ========== 실행 설정 ==========

  // 본 실행 반복 횟수
  protected int iterations = 10;

  // 워밍업 반복 횟수 (JVM 최적화를 위해 본 실행 전 미리 실행)
  protected int warmupIterations = 3;

  // 상세 로깅 여부
  protected boolean verbose = true;

  /**
   * BaseScenario 생성자
   *
   * @param scenarioName 시나리오 이름
   * @param description 시나리오 설명
   */
  public BaseScenario(String scenarioName, String description) {
    this.scenarioName = scenarioName;
    this.description = description;
  }

  /**
   * 시나리오 실행 - Template Method 패턴의 템플릿 메서드
   *
   * 실행 순서:
   * 1. 시나리오 시작 로깅
   * 2. setup() - 필요한 서버나 리소스 준비
   * 3. 워밍업 실행 (JVM 최적화)
   * 4. 본 실행
   * 5. 통계 계산
   * 6. 결과 출력
   * 7. teardown() - 리소스 정리
   */
  public void execute() {
    // ===== 1. 시나리오 시작 =====
    logger.separator();
    logger.info("🚀 시나리오 시작: " + scenarioName);
    logger.info("📝 설명: " + description);
    logger.info("🔧 설정: 반복 " + iterations + "회, 워밍업 " + warmupIterations + "회");
    logger.separator();

    try {
      // ===== 2. 준비 단계 =====
      logger.info("준비 중...");
      setup();  // 추상 메서드 - 각 시나리오가 구현 (서버 시작 등)

      // ===== 3. 워밍업 실행 =====
      // JVM이 코드를 최적화(JIT 컴파일)할 수 있도록 미리 몇 번 실행
      if (warmupIterations > 0) {
        logger.info("워밍업 실행 (" + warmupIterations + "회)...");
        for (int i = 0; i < warmupIterations; i++) {
          // true 파라미터는 워밍업임을 표시 - 통계에 포함되지 않음
          runSingleIteration(i, true);
        }
        logger.info("워밍업 완료\n");
      }

      // ===== 4. 본 실행 =====
      logger.info("본 실행 시작 (" + iterations + "회)...");

      // 전체 실행 시간 측정 시작
      long startTime = System.currentTimeMillis();

      // 지정된 횟수만큼 시나리오 반복 실행
      for (int i = 0; i < iterations; i++) {
        // verbose 모드일 때 진행률 표시
        if (verbose) {
          logger.progress("진행", i + 1, iterations);
        }
        // false 파라미터는 본 실행임을 표시 - 통계에 포함됨
        runSingleIteration(i, false);
      }

      // 전체 실행 시간 계산
      totalExecutionTime = System.currentTimeMillis() - startTime;

      // ===== 5. 통계 계산 =====
      calculateStatistics();

      // ===== 6. 결과 출력 =====
      printResults();

    } catch (Exception e) {
      // 시나리오 실행 중 예외 발생 시 로깅
      logger.error("시나리오 실행 중 오류 발생", e);
    } finally {
      // ===== 7. 정리 단계 =====
      // finally 블록으로 예외 발생 여부와 관계없이 항상 정리 수행
      logger.info("정리 중...");
      teardown();  // 추상 메서드 - 각 시나리오가 구현 (서버 종료 등)
      logger.info("시나리오 종료: " + scenarioName);
      logger.separator();
    }
  }

  /**
   * 단일 반복 실행 - 한 번의 테스트 케이스 실행
   *
   * @param iteration 현재 반복 번호 (0부터 시작)
   * @param isWarmup 워밍업 여부 (true면 통계에서 제외)
   */
  private void runSingleIteration(int iteration, boolean isWarmup) {
    try {
      // 이번 실행의 시작 시간 기록
      long startTime = System.currentTimeMillis();

      // 실제 시나리오 로직 실행 (각 구체적 시나리오가 구현)
      boolean success = runScenario(iteration);

      // 응답 시간 계산 (종료 시간 - 시작 시간)
      long responseTime = System.currentTimeMillis() - startTime;

      // 워밍업이 아닌 경우에만 통계 업데이트
      if (!isWarmup) {
        totalRuns++;  // 총 실행 횟수 증가
        responseTimes.add(responseTime);  // 응답 시간 기록

        // 성공/실패 카운트 업데이트 (AtomicInteger로 스레드 안전)
        if (success) {
          successCount.incrementAndGet();
        } else {
          failureCount.incrementAndGet();
        }

        // 최소/최대 응답 시간 업데이트
        minResponseTime = Math.min(minResponseTime, responseTime);
        maxResponseTime = Math.max(maxResponseTime, responseTime);
      }

      // verbose 모드이고 본 실행일 때 각 실행 결과 로깅
      if (verbose && !isWarmup) {
        logger.debug("Iteration " + (iteration + 1) + ": " +
            (success ? "SUCCESS" : "FAILURE") + " (" + responseTime + "ms)");
      }

    } catch (Exception e) {
      // 실행 중 예외 발생 시 처리
      if (!isWarmup) {
        failureCount.incrementAndGet();  // 실패로 카운트
        totalRuns++;
      }
      logger.error("Iteration " + iteration + " 실행 중 오류", e);
    }
  }

  /**
   * 통계 계산 - 수집된 데이터로부터 평균값 등 계산
   */
  protected void calculateStatistics() {
    // 응답 시간 데이터가 있는 경우에만 평균 계산
    if (!responseTimes.isEmpty()) {
      // Java 8 Stream API를 사용한 합계 계산
      double sum = responseTimes.stream()
          .mapToLong(Long::longValue)  // Long을 long으로 변환
          .sum();                       // 모든 값의 합

      // 평균 = 합계 / 개수
      avgResponseTime = sum / responseTimes.size();
    }
  }

  /**
   * 결과 출력 - 실행 결과를 보기 좋게 포맷팅하여 출력
   */
  protected void printResults() {
    // ===== 결과 헤더 =====
    System.out.println("\n" + "=".repeat(60));
    System.out.println("📊 시나리오 실행 결과: " + scenarioName);
    System.out.println("=".repeat(60));

    // ===== 실행 통계 =====
    System.out.println("📈 실행 통계:");
    System.out.println("  • 총 실행 횟수: " + totalRuns);

    // 성공률 계산 및 출력
    System.out.println("  • 성공: " + successCount.get() +
        " (" + String.format("%.1f%%", (successCount.get() * 100.0 / totalRuns)) + ")");

    // 실패율 계산 및 출력
    System.out.println("  • 실패: " + failureCount.get() +
        " (" + String.format("%.1f%%", (failureCount.get() * 100.0 / totalRuns)) + ")");

    // 타임아웃이 발생한 경우에만 출력
    if (timeoutCount.get() > 0) {
      System.out.println("  • 타임아웃: " + timeoutCount.get() +
          " (" + String.format("%.1f%%", (timeoutCount.get() * 100.0 / totalRuns)) + ")");
    }

    // ===== 응답 시간 통계 =====
    System.out.println("\n⏱️ 응답 시간:");
    System.out.println("  • 최소: " + minResponseTime + "ms");
    System.out.println("  • 최대: " + maxResponseTime + "ms");
    System.out.println("  • 평균: " + String.format("%.2f", avgResponseTime) + "ms");
    System.out.println("  • 총 실행 시간: " + totalExecutionTime + "ms");

    // ===== 추가 통계 =====
    // 각 구체적 시나리오가 필요시 오버라이드하여 추가 정보 출력
    printAdditionalResults();

    System.out.println("=".repeat(60));
  }

  /**
   * 시나리오별 추가 결과 출력 (Hook 메서드)
   *
   * 각 구체적 시나리오가 필요시 오버라이드하여
   * 해당 시나리오 특화 통계나 분석을 출력할 수 있습니다.
   *
   * 예: ConnectTimeoutScenario는 타임아웃 값별 통계 출력
   */
  protected void printAdditionalResults() {
    // 기본 구현은 비어있음 - 서브클래스에서 필요시 구현
  }

  // ========== 추상 메서드 (서브클래스가 반드시 구현해야 함) ==========

  /**
   * 시나리오 준비 - 테스트에 필요한 환경 설정
   *
   * 예시:
   * - 테스트용 서버 시작
   * - 네트워크 연결 준비
   * - 필요한 파일이나 데이터 준비
   *
   * @throws Exception 준비 과정에서 발생할 수 있는 예외
   */
  protected abstract void setup() throws Exception;

  /**
   * 시나리오 실행 - 실제 테스트 로직
   *
   * 이 메서드는 한 번의 테스트 케이스를 실행합니다.
   * 예를 들어, 클라이언트를 생성하고 서버에 연결을 시도한 후
   * 타임아웃이 발생하는지 확인하는 로직을 구현합니다.
   *
   * @param iteration 현재 반복 번호 (테스트 케이스 구분용)
   * @return 테스트 성공 여부 (true: 성공, false: 실패)
   * @throws Exception 실행 중 발생할 수 있는 예외
   */
  protected abstract boolean runScenario(int iteration) throws Exception;

  /**
   * 시나리오 정리 - 사용한 리소스 해제
   *
   * 예시:
   * - 서버 종료
   * - 네트워크 연결 종료
   * - 임시 파일 삭제
   * - 스레드풀 종료
   */
  protected abstract void teardown();

  // ========== Getter & Setter 메서드들 ==========

  /**
   * 반복 횟수 설정
   * @param iterations 실행할 횟수
   */
  public void setIterations(int iterations) {
    this.iterations = iterations;
  }

  /**
   * 워밍업 반복 횟수 설정
   * @param warmupIterations 워밍업 횟수
   */
  public void setWarmupIterations(int warmupIterations) {
    this.warmupIterations = warmupIterations;
  }

  /**
   * 상세 로깅 모드 설정
   * @param verbose true면 각 실행마다 로그 출력
   */
  public void setVerbose(boolean verbose) {
    this.verbose = verbose;
  }

  /**
   * 시나리오 이름 반환
   * @return 시나리오 이름
   */
  public String getScenarioName() {
    return scenarioName;
  }

  /**
   * 시나리오 설명 반환
   * @return 시나리오 설명
   */
  public String getDescription() {
    return description;
  }

  /**
   * 성공 횟수 반환
   * @return 성공한 실행 횟수
   */
  public int getSuccessCount() {
    return successCount.get();
  }

  /**
   * 실패 횟수 반환
   * @return 실패한 실행 횟수
   */
  public int getFailureCount() {
    return failureCount.get();
  }

  /**
   * 타임아웃 횟수 반환
   * @return 타임아웃이 발생한 횟수
   */
  public int getTimeoutCount() {
    return timeoutCount.get();
  }

  /**
   * 평균 응답 시간 반환
   * @return 평균 응답 시간 (밀리초)
   */
  public double getAvgResponseTime() {
    return avgResponseTime;
  }
}