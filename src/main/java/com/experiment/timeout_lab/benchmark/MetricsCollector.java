package com.experiment.timeout_lab.benchmark;

import com.experiment.timeout_lab.util.Logger;
import com.experiment.timeout_lab.util.NetworkUtil;

import java.lang.management.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 메트릭 수집기
 *
 * 시스템 리소스 사용량 (CPU, 메모리, 스레드)을 모니터링합니다.
 * 벤치마크 실행 중 성능 지표를 수집하여 분석에 활용합니다.
 */
public class MetricsCollector {

  private static final Logger logger = new Logger(MetricsCollector.class);

  // JMX MBeans
  private final OperatingSystemMXBean osMBean;
  private final MemoryMXBean memoryMBean;
  private final ThreadMXBean threadMBean;
  private final RuntimeMXBean runtimeMBean;

  // Sun/Oracle JVM 전용 MXBean (CPU 로드 측정용)
  private final com.sun.management.OperatingSystemMXBean sunOsMBean;
  private final boolean isSunJVM;

  // 모니터링 제어
  private final AtomicBoolean monitoring = new AtomicBoolean(false);
  private ScheduledExecutorService executor;

  // 수집된 메트릭
  private final Metrics metrics;

  public MetricsCollector() {
    this.osMBean = ManagementFactory.getOperatingSystemMXBean();
    this.memoryMBean = ManagementFactory.getMemoryMXBean();
    this.threadMBean = ManagementFactory.getThreadMXBean();
    this.runtimeMBean = ManagementFactory.getRuntimeMXBean();
    this.metrics = new Metrics();

    // Sun/Oracle JVM 체크 및 캐스팅
    if (osMBean instanceof com.sun.management.OperatingSystemMXBean) {
      this.sunOsMBean = (com.sun.management.OperatingSystemMXBean) osMBean;
      this.isSunJVM = true;
      logger.debug("Sun/Oracle JVM 감지 - CPU 로드 측정 가능");
    } else {
      this.sunOsMBean = null;
      this.isSunJVM = false;
      logger.warn("Non-Oracle JVM 감지 - CPU 로드 측정 제한적");
    }
  }

  /**
   * 모니터링 시작
   */
  public void startMonitoring() {
    if (monitoring.compareAndSet(false, true)) {
      executor = Executors.newScheduledThreadPool(1);

      // 1초마다 메트릭 수집
      executor.scheduleAtFixedRate(this::collectMetrics, 0, 1, TimeUnit.SECONDS);

      logger.info("📊 메트릭 수집 시작");
    }
  }

  /**
   * 모니터링 종료
   */
  public void stopMonitoring() {
    if (monitoring.compareAndSet(true, false)) {
      if (executor != null) {
        executor.shutdown();
        try {
          if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow();
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }

      logger.info("📊 메트릭 수집 종료");
    }
  }

  /**
   * 메트릭 수집
   */
  private void collectMetrics() {
    try {
      // CPU 사용률 (여러 방법 시도)
      double cpuLoad = getCpuLoad();
      if (cpuLoad >= 0) {
        metrics.updateCpuUsage(cpuLoad);
      }

      // 메모리 사용량
      MemoryUsage heapUsage = memoryMBean.getHeapMemoryUsage();
      long usedMemory = heapUsage.getUsed();
      long maxMemory = heapUsage.getMax();
      double memoryUsage = (usedMemory * 100.0) / maxMemory;
      metrics.updateMemoryUsage(memoryUsage, usedMemory, maxMemory);

      // 스레드 수
      int threadCount = threadMBean.getThreadCount();
      int peakThreadCount = threadMBean.getPeakThreadCount();
      metrics.updateThreadCount(threadCount, peakThreadCount);

      // GC 정보
      long totalGcCount = 0;
      long totalGcTime = 0;
      for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
        long count = gcBean.getCollectionCount();
        long time = gcBean.getCollectionTime();
        if (count > 0) {
          totalGcCount += count;
          totalGcTime += time;
        }
      }
      metrics.updateGcInfo(totalGcCount, totalGcTime);

    } catch (Exception e) {
      logger.error("메트릭 수집 중 오류", e);
    }
  }

  /**
   * CPU 로드 측정 (여러 방법 시도)
   */
  private double getCpuLoad() {
    double cpuLoad = -1;

    // 방법 1: Sun/Oracle JVM의 getProcessCpuLoad() 사용
    if (isSunJVM && sunOsMBean != null) {
      try {
        cpuLoad = sunOsMBean.getProcessCpuLoad() * 100;
        if (cpuLoad >= 0) {
          return cpuLoad;
        }
      } catch (Exception e) {
        logger.debug("getProcessCpuLoad() 실패: " + e.getMessage());
      }

      // 방법 2: getCpuLoad() 시도 (JVM 전체 CPU)
      try {
        cpuLoad = sunOsMBean.getCpuLoad() * 100;
        if (cpuLoad >= 0) {
          return cpuLoad;
        }
      } catch (Exception e) {
        logger.debug("getCpuLoad() 실패: " + e.getMessage());
      }
    }

    // 방법 3: 시스템 로드 평균 사용 (대체 방법)
    double loadAverage = osMBean.getSystemLoadAverage();
    if (loadAverage >= 0) {
      int processors = osMBean.getAvailableProcessors();
      // 로드 평균을 CPU 사용률로 근사치 계산
      cpuLoad = (loadAverage / processors) * 100;
      cpuLoad = Math.min(cpuLoad, 100); // 100% 초과 방지
      return cpuLoad;
    }

    // 방법 4: 스레드 CPU 시간 기반 계산 (최후의 수단)
    if (threadMBean.isThreadCpuTimeSupported()) {
      try {
        long totalCpuTime = 0;
        for (long threadId : threadMBean.getAllThreadIds()) {
          long cpuTime = threadMBean.getThreadCpuTime(threadId);
          if (cpuTime > 0) {
            totalCpuTime += cpuTime;
          }
        }
        // 이전 측정값과 비교하여 델타 계산 필요 (간단히 0 반환)
        return 0;
      } catch (Exception e) {
        logger.debug("스레드 CPU 시간 계산 실패: " + e.getMessage());
      }
    }

    return -1; // 측정 불가
  }

  /**
   * 현재 메트릭 스냅샷 반환
   */
  public MetricSnapshot getSnapshot() {
    return new MetricSnapshot(
        metrics.getAvgCpuUsage(),
        metrics.getMaxCpuUsage(),
        metrics.getAvgMemoryUsage(),
        metrics.getMaxMemoryUsage(),
        metrics.getMaxThreadCount(),
        metrics.getTotalGcCount(),
        metrics.getTotalGcTime()
    );
  }

  /**
   * 메트릭 요약 출력
   */
  public void printSummary() {
    System.out.println("\n📊 시스템 메트릭 요약:");
    System.out.println("├─ CPU 사용률:");

    if (metrics.getAvgCpuUsage() > 0) {
      System.out.println("│  ├─ 평균: " + String.format("%.1f%%", metrics.getAvgCpuUsage()));
      System.out.println("│  └─ 최대: " + String.format("%.1f%%", metrics.getMaxCpuUsage()));
    } else {
      System.out.println("│  └─ (측정 불가 - JVM 제약)");
    }

    System.out.println("├─ 메모리 사용:");
    System.out.println("│  ├─ 평균: " + String.format("%.1f%%", metrics.getAvgMemoryUsage()));
    System.out.println("│  ├─ 최대: " + String.format("%.1f%%", metrics.getMaxMemoryUsage()));
    System.out.println("│  └─ 최대 사용량: " + NetworkUtil.formatBytes(metrics.getMaxMemoryBytes()));
    System.out.println("├─ 스레드:");
    System.out.println("│  ├─ 평균: " + metrics.getAvgThreadCount());
    System.out.println("│  └─ 최대: " + metrics.getMaxThreadCount());
    System.out.println("└─ GC:");
    System.out.println("   ├─ 횟수: " + metrics.getTotalGcCount());
    System.out.println("   └─ 총 시간: " + metrics.getTotalGcTime() + "ms");

    // JVM 정보 추가
    System.out.println("\n📋 JVM 정보:");
    System.out.println("├─ JVM: " + System.getProperty("java.vm.name"));
    System.out.println("├─ 버전: " + System.getProperty("java.version"));
    System.out.println("└─ CPU 코어: " + osMBean.getAvailableProcessors());
  }

  /**
   * 메트릭 반환
   */
  public Metrics getMetrics() {
    return metrics;
  }

  /**
   * 메트릭 저장 클래스
   */
  public static class Metrics {
    private final AtomicLong cpuSamples = new AtomicLong(0);
    private double cpuSum = 0;
    private double maxCpu = 0;

    private final AtomicLong memorySamples = new AtomicLong(0);
    private double memorySum = 0;
    private double maxMemory = 0;
    private long maxMemoryBytes = 0;

    private final AtomicLong threadSamples = new AtomicLong(0);
    private long threadSum = 0;
    private int maxThreads = 0;

    private long totalGcCount = 0;
    private long totalGcTime = 0;

    public synchronized void updateCpuUsage(double usage) {
      cpuSamples.incrementAndGet();
      cpuSum += usage;
      maxCpu = Math.max(maxCpu, usage);
    }

    public synchronized void updateMemoryUsage(double usage, long used, long max) {
      memorySamples.incrementAndGet();
      memorySum += usage;
      maxMemory = Math.max(maxMemory, usage);
      maxMemoryBytes = Math.max(maxMemoryBytes, used);
    }

    public synchronized void updateThreadCount(int count, int peak) {
      threadSamples.incrementAndGet();
      threadSum += count;
      maxThreads = Math.max(maxThreads, peak);
    }

    public synchronized void updateGcInfo(long count, long time) {
      // 누적값 업데이트
      totalGcCount = Math.max(totalGcCount, count);
      totalGcTime = Math.max(totalGcTime, time);
    }

    public double getAvgCpuUsage() {
      long samples = cpuSamples.get();
      return samples > 0 ? cpuSum / samples : 0;
    }

    public double getMaxCpuUsage() {
      return maxCpu;
    }

    public double getAvgMemoryUsage() {
      long samples = memorySamples.get();
      return samples > 0 ? memorySum / samples : 0;
    }

    public double getMaxMemoryUsage() {
      return maxMemory;
    }

    public long getMaxMemoryBytes() {
      return maxMemoryBytes;
    }

    public int getAvgThreadCount() {
      long samples = threadSamples.get();
      return samples > 0 ? (int)(threadSum / samples) : 0;
    }

    public int getMaxThreadCount() {
      return maxThreads;
    }

    public long getTotalGcCount() {
      return totalGcCount;
    }

    public long getTotalGcTime() {
      return totalGcTime;
    }
  }

  /**
   * 메트릭 스냅샷
   */
  public static class MetricSnapshot {
    public final double avgCpuUsage;
    public final double maxCpuUsage;
    public final double avgMemoryUsage;
    public final double maxMemoryUsage;
    public final int maxThreadCount;
    public final long totalGcCount;
    public final long totalGcTime;

    public MetricSnapshot(double avgCpuUsage, double maxCpuUsage,
        double avgMemoryUsage, double maxMemoryUsage,
        int maxThreadCount, long totalGcCount, long totalGcTime) {
      this.avgCpuUsage = avgCpuUsage;
      this.maxCpuUsage = maxCpuUsage;
      this.avgMemoryUsage = avgMemoryUsage;
      this.maxMemoryUsage = maxMemoryUsage;
      this.maxThreadCount = maxThreadCount;
      this.totalGcCount = totalGcCount;
      this.totalGcTime = totalGcTime;
    }
  }

  /**
   * 테스트용 main 메서드
   */
  public static void main(String[] args) throws InterruptedException {
    MetricsCollector collector = new MetricsCollector();

    System.out.println("메트릭 수집 테스트 시작...");
    collector.startMonitoring();

    // 5초간 모니터링
    Thread.sleep(5000);

    collector.stopMonitoring();
    collector.printSummary();
  }
}