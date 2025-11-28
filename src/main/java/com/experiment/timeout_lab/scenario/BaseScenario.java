package com.experiment.timeout_lab.scenario;

import com.experiment.timeout_lab.util.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 모든 타임아웃 시나리오의 기본 추상 클래스
 *
 * 각 시나리오는 이 클래스를 상속받아 구현하며,
 * 공통 기능인 실행, 측정, 리포팅을 제공합니다.
 * */
public abstract class BaseScenario {

    protected static final Logger logger = new Logger(BaseScenario.class);

    protected final String scenarioName;
    protected final String description;

    // 실행 통계
    protected int totalRuns = 0;
    protected final AtomicInteger successCount = new AtomicInteger(0);
    protected final AtomicInteger failureCount = new AtomicInteger(0);
    protected final AtomicInteger timeoutCount = new AtomicInteger(0);

    // 시간 측정
    protected final List<Long> responseTimes = new ArrayList<>();
    protected long totalExecutionTime = 0;
    protected long minResponseTime = Long.MAX_VALUE;
    protected long maxResponseTime = 0;
    protected double avgResponseTime = 0;

    // 설정
    protected int iterations = 10;
    protected int warmupIterations = 3;
    protected boolean verbose = true;

    public BaseScenario(String scenarioName, String description) {
        this.scenarioName = scenarioName;
        this.description = description;
    }

    /**
     * 시나리오 실행
     * */
    public void execute() {
        logger.separator();
        logger.info("🚀 시나리오 시작: " + scenarioName);
        logger.info("📝 설명: " + description);
        logger.info("🔧 설정: 반복 " + iterations + "회, 워밍업 " + warmupIterations + "회");
        logger.separator();

        try {
            // 준비 단계
            logger.info("준비 중...");
            setup();

            // 워밍업
            if (warmupIterations > 0) {
                logger.info("워밍업 실행 (" + warmupIterations + "회)...");
                for (int i = 0; i < warmupIterations; i++) {
                    runSingleIteration(i, true);
                }
                logger.info("워밍업 완료\n");
            }

            // 실제 실행
            logger.info("본 실행 시작 (" + iterations + "회)...");
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < iterations; i++) {
                if (verbose) {
                    logger.progress("진행", i + 1, iterations);
                }
                runSingleIteration(i, false);
            }
            
            totalExecutionTime = System.currentTimeMillis() - startTime;
            
            // 통계 계산
            calculateStatistics();
            
            // 결과 출력
            printResults();

        } catch (Exception e) {
            logger.error("시나리오 실행 중 오류 발생", e);
        } finally {
            // 정리
            logger.info("정리 중...");
//            teardown();
            logger.info("시나리오 종료:" + scenarioName);
            logger.separator();
        }
    }

    /**
     * 단일 반복 실행
     * */
    private void runSingleIteration(int iteration, boolean isWarmup) {
        try {
            long startTime = System.currentTimeMillis();

            boolean success = runScenario(iteration);

            long responseTime = System.currentTimeMillis() - startTime;

            if (!isWarmup) {
                totalRuns++;
                responseTimes.add(responseTime);

                if (success) {
                    successCount.incrementAndGet();
                } else {
                    failureCount.incrementAndGet();
                }

                // 최소/최대 시간 업데이트
                minResponseTime = Math.min(minResponseTime, responseTime);
                maxResponseTime = Math.max(maxResponseTime, responseTime);
            }

            if (verbose && !isWarmup) {
                logger.debug("Iteration " + (iteration + 1) + ": " +
                        (success ? "SUCCESS" : "FAILURE") + " (" + responseTime + "ms)");
            }

        } catch (Exception e) {
            if (!isWarmup) {
                failureCount.incrementAndGet();
                totalRuns++;
            }
            logger.error("Iteration " + iteration + " 실행 중 오류", e);
        }
    }

    /**
     * 통계 계산
     * */
    protected void calculateStatistics() {
        if (!responseTimes.isEmpty()) {
            double sum = responseTimes.stream().mapToLong(Long::longValue).sum();
            avgResponseTime = sum / responseTimes.size();
        }
    }

    /**
     * 결과 출력
     * */
    protected void printResults() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📊 시나리오 실행 결과: " + scenarioName);
        System.out.println("=".repeat(60));

        System.out.println("📈 실행 통계:");
        System.out.println("  • 총 실행 횟수: " + totalRuns);
        System.out.println("  • 성공: " + successCount.get() +
                " (" + String.format("%.1f%%", (successCount.get() * 100.0 / totalRuns)) + ")");
        System.out.println("  • 실패: " + failureCount.get() +
                " (" + String.format("%.1f%%", (failureCount.get() * 100.0 / totalRuns)) + ")");

        if (timeoutCount.get() > 0) {
            System.out.println("  • 타임아웃: " + timeoutCount.get() +
                    " (" + String.format("%.1f%%", (timeoutCount.get() * 100.0 / totalRuns)) + ")");
        }

        System.out.println("\n⏱️ 응답 시간:");
        System.out.println("  • 최소: " + minResponseTime + "ms");
        System.out.println("  • 최대: " + maxResponseTime + "ms");
        System.out.println("  • 평균: " + String.format("%.2f", avgResponseTime) + "ms");
        System.out.println("  • 총 실행 시간: " + totalExecutionTime + "ms");

        // 추가 통계 (서브클래스에서 구현)
        printAdditionalResults();

        System.out.println("=".repeat(60));
    }
    
    /**
     * 시나리오별 추가 결과 출력 (선택적)
     * */
    protected void printAdditionalResults() {
        // 서브클래스에서 필요 시 구현
    }

    /**
     * 시나리오 준비 (서브클래스에서 구현)
     * */
    protected abstract void setup() throws Exception;

    /**
     * 시나리오 실행 (서브클래스에서 구현)
     * @return 성공 여부
     * */
    protected abstract boolean runScenario(int iterations) throws Exception;

    /**
     * 시나리오 정리 (서브클래스에서 구현)
     * */
    protected abstract void teardown();

    // Getter & Setter
    public void setIterations(int iterations) {
        this.iterations = iterations;
    }

    public void setWarmupIterations(int warmupIterations) {
        this.warmupIterations = warmupIterations;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public String getDescription() {
        return description;
    }

    public int getSuccessCount() {
        return successCount.get();
    }

    public int getFailureCount() {
        return failureCount.get();
    }

    public int getTimeoutCount() {
        return timeoutCount.get();
    }

    public double getAvgResponseTime() {
        return avgResponseTime;
    }
}
