package com.experiment.timeout_lab.benchmark;

import com.experiment.timeout_lab.scenario.*;
import com.experiment.timeout_lab.util.Logger;
import com.experiment.timeout_lab.util.NetworkUtil;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 벤치마크 실행기
 *
 * 모든 시나리오를 체계적으로 실행하고 결과를 수집합니다.
 * CSV 파일로 결과를 저장하여 추후 분석에 활용할 수 있습니다.
 */
public class BenchmarkRunner {

  private static final Logger logger = new Logger(BenchmarkRunner.class);
  private static final String RESULTS_DIR = "results/benchmarks/";

  private final List<BaseScenario> scenarios;
  private final List<BenchmarkResult> results;
  private final MetricsCollector metricsCollector;

  public BenchmarkRunner() {
    this.scenarios = new ArrayList<>();
    this.results = new ArrayList<>();
    this.metricsCollector = new MetricsCollector();

    // 결과 디렉토리 생성
    createResultsDirectory();
  }

  /**
   * 시나리오 추가
   */
  public void addScenario(BaseScenario scenario) {
    scenarios.add(scenario);
  }

  /**
   * 모든 시나리오 추가 (기본 세트)
   */
  public void addAllScenarios() {
    // Connect Timeout
    ConnectTimeoutScenario connectScenario = new ConnectTimeoutScenario();
    connectScenario.setIterations(20);
    connectScenario.setWarmupIterations(5);
    addScenario(connectScenario);

    // Read Timeout
    ReadTimeoutScenario readScenario = new ReadTimeoutScenario();
    readScenario.setIterations(20);
    readScenario.setWarmupIterations(5);
    addScenario(readScenario);

    // Write Timeout
    WriteTimeoutScenario writeScenario = new WriteTimeoutScenario();
    writeScenario.setIterations(20);
    writeScenario.setWarmupIterations(5);
    addScenario(writeScenario);

    // Thread Exhaustion
    ThreadExhaustionScenario exhaustionScenario = new ThreadExhaustionScenario();
    addScenario(exhaustionScenario);
  }

  /**
   * 벤치마크 실행
   */
  public void run() {
    logger.info("=".repeat(80));
    logger.info("🚀 벤치마크 실행 시작");
    logger.info("총 시나리오 수: " + scenarios.size());
    logger.info("=".repeat(80));

    long totalStartTime = System.currentTimeMillis();

    // 시스템 정보 출력
    printSystemInfo();

    // 메트릭 수집 시작
    metricsCollector.startMonitoring();

    try {
      // 각 시나리오 실행
      for (int i = 0; i < scenarios.size(); i++) {
        BaseScenario scenario = scenarios.get(i);

        logger.info("\n");
        logger.info("📊 시나리오 " + (i + 1) + "/" + scenarios.size() +
            " 실행 중: " + scenario.getScenarioName());

        // 시나리오 실행 및 시간 측정
        long startTime = System.currentTimeMillis();
        scenario.execute();
        long executionTime = System.currentTimeMillis() - startTime;

        // 결과 수집
        BenchmarkResult result = new BenchmarkResult(
            scenario.getScenarioName(),
            scenario.getSuccessCount(),
            scenario.getFailureCount(),
            scenario.getTimeoutCount(),
            scenario.getAvgResponseTime(),
            executionTime
        );

        results.add(result);

        // 시나리오 간 대기
        Thread.sleep(2000);
      }

    } catch (Exception e) {
      logger.error("벤치마크 실행 중 오류", e);
    } finally {
      // 메트릭 수집 종료
      metricsCollector.stopMonitoring();
    }

    long totalTime = System.currentTimeMillis() - totalStartTime;

    // 최종 결과 출력
    printSummary(totalTime);

    // 결과 저장
    saveResults();

    // 리포트 생성
    generateReport();
  }

  /**
   * 비동기 벤치마크 실행
   */
  public CompletableFuture<Void> runAsync() {
    return CompletableFuture.runAsync(this::run);
  }

  /**
   * 시스템 정보 출력
   */
  private void printSystemInfo() {
    System.out.println("\n📋 시스템 정보:");
    System.out.println("  • OS: " + System.getProperty("os.name") + " " +
        System.getProperty("os.version"));
    System.out.println("  • Java: " + System.getProperty("java.version"));
    System.out.println("  • CPU Cores: " + Runtime.getRuntime().availableProcessors());
    System.out.println("  • Max Memory: " +
        NetworkUtil.formatBytes(Runtime.getRuntime().maxMemory()));
    System.out.println("  • 현재 시간: " + LocalDateTime.now());
  }

  /**
   * 실행 요약 출력
   */
  private void printSummary(long totalTime) {
    System.out.println("\n");
    System.out.println("=".repeat(80));
    System.out.println("📊 벤치마크 실행 완료");
    System.out.println("=".repeat(80));

    System.out.println("\n📈 전체 결과 요약:");
    System.out.println("├─ 실행된 시나리오: " + results.size());
    System.out.println("├─ 총 실행 시간: " + NetworkUtil.formatDuration(totalTime));
    System.out.println("└─ 평균 시나리오 실행 시간: " +
        NetworkUtil.formatDuration(totalTime / results.size()));

    // 각 시나리오 결과
    System.out.println("\n📋 시나리오별 결과:");
    System.out.println("┌────────────────────────┬──────────┬──────────┬──────────┬──────────────┐");
    System.out.println("│      시나리오 이름       │   성공    │   실패    │ 타임아웃  │  평균 응답시간  │");
    System.out.println("├────────────────────────┼──────────┼──────────┼──────────┼──────────────┤");

    for (BenchmarkResult result : results) {
      System.out.printf("│ %-22s │ %8d │ %8d │ %8d │ %12.2fms │%n",
          result.scenarioName.length() > 22 ?
              result.scenarioName.substring(0, 22) : result.scenarioName,
          result.successCount,
          result.failureCount,
          result.timeoutCount,
          result.avgResponseTime
      );
    }

    System.out.println("└────────────────────────┴──────────┴──────────┴──────────┴──────────────┘");

    // 메트릭 요약
    metricsCollector.printSummary();
  }

  /**
   * 결과를 CSV 파일로 저장
   */
  private void saveResults() {
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    String filename = RESULTS_DIR + "benchmark_" + timestamp + ".csv";

    try (FileWriter writer = new FileWriter(filename)) {
      // CSV 헤더
      writer.append("Timestamp,Scenario,Success,Failure,Timeout,AvgResponseTime,TotalTime\n");

      // 데이터 행
      for (BenchmarkResult result : results) {
        writer.append(timestamp).append(",");
        writer.append(result.scenarioName).append(",");
        writer.append(String.valueOf(result.successCount)).append(",");
        writer.append(String.valueOf(result.failureCount)).append(",");
        writer.append(String.valueOf(result.timeoutCount)).append(",");
        writer.append(String.format("%.2f", result.avgResponseTime)).append(",");
        writer.append(String.valueOf(result.totalTime)).append("\n");
      }

      logger.info("✅ 결과 저장 완료: " + filename);

    } catch (IOException e) {
      logger.error("결과 저장 실패", e);
    }
  }

  /**
   * HTML 리포트 생성
   */
  private void generateReport() {
    Report report = new Report(results, metricsCollector.getMetrics());
    report.generateHtmlReport();
  }

  /**
   * 결과 디렉토리 생성
   */
  private void createResultsDirectory() {
    java.io.File dir = new java.io.File(RESULTS_DIR);
    if (!dir.exists()) {
      dir.mkdirs();
    }
  }

  /**
   * 벤치마크 결과 클래스
   */
  public static class BenchmarkResult {
    final String scenarioName;
    final int successCount;
    final int failureCount;
    final int timeoutCount;
    final double avgResponseTime;
    final long totalTime;

    public BenchmarkResult(String scenarioName, int successCount, int failureCount,
        int timeoutCount, double avgResponseTime, long totalTime) {
      this.scenarioName = scenarioName;
      this.successCount = successCount;
      this.failureCount = failureCount;
      this.timeoutCount = timeoutCount;
      this.avgResponseTime = avgResponseTime;
      this.totalTime = totalTime;
    }
  }

  /**
   * 단독 실행용 main 메서드
   */
  public static void main(String[] args) {
    BenchmarkRunner runner = new BenchmarkRunner();
    runner.addAllScenarios();
    runner.run();
  }
}