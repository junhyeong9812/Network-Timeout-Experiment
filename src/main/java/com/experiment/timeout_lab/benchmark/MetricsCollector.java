package com.experiment.timeout_lab.benchmark;

import com.experiment.timeout_lab.util.Logger;
import com.experiment.timeout_lab.util.NetworkUtil;

import java.lang.management.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 메트릭 수집기 (System Metrics Collector)
 *
 * 벤치마크 실행 중 시스템 리소스 사용량을 실시간으로 모니터링합니다.
 * JMX (Java Management Extensions)를 활용하여 CPU, 메모리, 스레드, GC 정보를 수집합니다.
 *
 * 주요 기능:
 * 1. CPU 사용률 측정 (프로세스 및 시스템)
 * 2. 힙 메모리 사용량 추적
 * 3. 스레드 수 모니터링
 * 4. 가비지 컬렉션 통계
 *
 * JMX MXBeans:
 * - OperatingSystemMXBean: OS 및 CPU 정보
 * - MemoryMXBean: 메모리 사용 정보
 * - ThreadMXBean: 스레드 정보
 * - GarbageCollectorMXBean: GC 정보
 *
 * @author Timeout Lab Team
 */
public class MetricsCollector {

  // 로깅을 위한 Logger 인스턴스
  private static final Logger logger = new Logger(MetricsCollector.class);

  // ========== JMX MBeans (Java Management Extensions) ==========

  /**
   * OperatingSystemMXBean: 운영체제와 CPU 정보를 제공
   * - getSystemLoadAverage(): 시스템 로드 평균
   * - getAvailableProcessors(): 사용 가능한 프로세서 수
   */
  private final OperatingSystemMXBean osMBean;

  /**
   * MemoryMXBean: JVM 메모리 사용 정보를 제공
   * - getHeapMemoryUsage(): 힙 메모리 사용량
   * - getNonHeapMemoryUsage(): 비힙 메모리 사용량
   */
  private final MemoryMXBean memoryMBean;

  /**
   * ThreadMXBean: 스레드 정보를 제공
   * - getThreadCount(): 현재 활성 스레드 수
   * - getPeakThreadCount(): 최대 스레드 수
   * - getAllThreadIds(): 모든 스레드 ID
   */
  private final ThreadMXBean threadMBean;

  /**
   * RuntimeMXBean: JVM 런타임 정보를 제공
   * - getUptime(): JVM 가동 시간
   * - getStartTime(): JVM 시작 시간
   */
  private final RuntimeMXBean runtimeMBean;

  // ========== Oracle/Sun JVM 전용 기능 ==========

  /**
   * Sun/Oracle JVM 전용 OperatingSystemMXBean
   * 표준 MXBean보다 더 많은 CPU 측정 기능을 제공:
   * - getProcessCpuLoad(): 프로세스 CPU 사용률 (0.0 ~ 1.0)
   * - getCpuLoad(): 시스템 전체 CPU 사용률
   * - getProcessCpuTime(): 프로세스가 사용한 총 CPU 시간
   */
  private final com.sun.management.OperatingSystemMXBean sunOsMBean;

  /**
   * Oracle/Sun JVM 여부 플래그
   * true: Oracle JVM (정확한 CPU 측정 가능)
   * false: 다른 JVM (제한적 CPU 측정)
   */
  private final boolean isSunJVM;

  // ========== 모니터링 제어 변수 ==========

  /**
   * 모니터링 상태를 나타내는 원자적 불린 변수
   * AtomicBoolean을 사용하여 스레드 안전성 보장
   * true: 모니터링 중, false: 모니터링 중지
   */
  private final AtomicBoolean monitoring = new AtomicBoolean(false);

  /**
   * 주기적 메트릭 수집을 위한 스케줄 실행자
   * 1초마다 collectMetrics() 메서드를 실행
   */
  private ScheduledExecutorService executor;

  // ========== 수집된 메트릭 저장소 ==========

  /**
   * 수집된 모든 메트릭을 저장하는 내부 클래스 인스턴스
   * CPU, 메모리, 스레드, GC 정보를 누적하여 저장
   */
  private final Metrics metrics;

  /**
   * MetricsCollector 생성자
   *
   * JMX MBeans를 초기화하고 JVM 타입을 감지합니다.
   */
  public MetricsCollector() {
    // 표준 JMX MBeans 획득
    this.osMBean = ManagementFactory.getOperatingSystemMXBean();
    this.memoryMBean = ManagementFactory.getMemoryMXBean();
    this.threadMBean = ManagementFactory.getThreadMXBean();
    this.runtimeMBean = ManagementFactory.getRuntimeMXBean();

    // 메트릭 저장소 초기화
    this.metrics = new Metrics();

    // ===== Sun/Oracle JVM 감지 및 캐스팅 =====

    // instanceof 연산자로 실제 구현 클래스 확인
    if (osMBean instanceof com.sun.management.OperatingSystemMXBean) {
      // Oracle JVM인 경우 확장 기능 사용 가능
      this.sunOsMBean = (com.sun.management.OperatingSystemMXBean) osMBean;
      this.isSunJVM = true;
      logger.debug("Sun/Oracle JVM 감지 - CPU 로드 측정 가능");
    } else {
      // 다른 JVM (OpenJ9, GraalVM 등)
      this.sunOsMBean = null;
      this.isSunJVM = false;
      logger.warn("Non-Oracle JVM 감지 - CPU 로드 측정 제한적");
    }
  }

  /**
   * 모니터링 시작
   *
   * 백그라운드 스레드에서 1초마다 메트릭을 수집합니다.
   * compareAndSet()을 사용하여 중복 시작을 방지합니다.
   */
  public void startMonitoring() {
    // 원자적으로 false → true 변경 (이미 true면 실패)
    if (monitoring.compareAndSet(false, true)) {
      // 단일 스레드 스케줄러 생성
      executor = Executors.newScheduledThreadPool(1);

      // collectMetrics 메서드를 1초마다 실행
      // scheduleAtFixedRate(작업, 초기지연, 주기, 시간단위)
      executor.scheduleAtFixedRate(
          this::collectMetrics,  // 실행할 메서드 참조
          0,                      // 즉시 시작
          1,                      // 1초 주기
          TimeUnit.SECONDS        // 시간 단위
      );

      logger.info("📊 메트릭 수집 시작");
    }
  }

  /**
   * 모니터링 종료
   *
   * 실행 중인 스케줄러를 안전하게 종료합니다.
   * shutdown() 후 awaitTermination()으로 정상 종료를 기다립니다.
   */
  public void stopMonitoring() {
    // 원자적으로 true → false 변경
    if (monitoring.compareAndSet(true, false)) {
      if (executor != null) {
        // 새 작업 제출 중지 (진행 중인 작업은 계속 실행)
        executor.shutdown();

        try {
          // 5초 동안 종료 대기
          if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            // 5초 후에도 종료되지 않으면 강제 종료
            executor.shutdownNow();
          }
        } catch (InterruptedException e) {
          // 인터럽트 발생 시 현재 스레드의 인터럽트 상태 복원
          Thread.currentThread().interrupt();
        }
      }

      logger.info("📊 메트릭 수집 종료");
    }
  }

  /**
   * 메트릭 수집 (핵심 메서드)
   *
   * 1초마다 호출되어 시스템 리소스 정보를 수집합니다.
   * CPU, 메모리, 스레드, GC 정보를 한 번에 수집합니다.
   */
  private void collectMetrics() {
    try {
      // ===== 1. CPU 사용률 수집 =====

      double cpuLoad = getCpuLoad();  // 여러 방법으로 CPU 측정 시도
      if (cpuLoad >= 0) {  // 유효한 값인 경우만 저장
        metrics.updateCpuUsage(cpuLoad);
      }

      // ===== 2. 메모리 사용량 수집 =====

      // 힙 메모리 정보 획득
      MemoryUsage heapUsage = memoryMBean.getHeapMemoryUsage();
      long usedMemory = heapUsage.getUsed();      // 사용 중인 메모리 (바이트)
      long maxMemory = heapUsage.getMax();        // 최대 할당 가능 메모리

      // 백분율 계산
      double memoryUsage = (usedMemory * 100.0) / maxMemory;

      // 메트릭 업데이트
      metrics.updateMemoryUsage(memoryUsage, usedMemory, maxMemory);

      // ===== 3. 스레드 정보 수집 =====

      int threadCount = threadMBean.getThreadCount();        // 현재 스레드 수
      int peakThreadCount = threadMBean.getPeakThreadCount(); // 최대 스레드 수 (JVM 시작 이후)
      metrics.updateThreadCount(threadCount, peakThreadCount);

      // ===== 4. 가비지 컬렉션 정보 수집 =====

      long totalGcCount = 0;  // 총 GC 횟수
      long totalGcTime = 0;   // 총 GC 소요 시간 (밀리초)

      // 모든 GC 컬렉터 순회 (Young Gen, Old Gen 등)
      for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
        long count = gcBean.getCollectionCount();  // 이 컬렉터의 GC 횟수
        long time = gcBean.getCollectionTime();    // 이 컬렉터의 총 GC 시간

        if (count > 0) {  // 유효한 값인 경우
          totalGcCount += count;
          totalGcTime += time;
        }
      }

      metrics.updateGcInfo(totalGcCount, totalGcTime);

    } catch (Exception e) {
      // 수집 중 오류 발생 시 로깅만 하고 계속 진행
      logger.error("메트릭 수집 중 오류", e);
    }
  }

  /**
   * CPU 로드 측정 (다양한 폴백 메커니즘)
   *
   * JVM 타입과 지원 여부에 따라 다양한 방법으로 CPU 사용률을 측정합니다.
   * 가장 정확한 방법부터 시도하고, 실패 시 대체 방법을 사용합니다.
   *
   * @return CPU 사용률 (0~100%), 측정 불가 시 -1
   */
  private double getCpuLoad() {
    double cpuLoad = -1;  // 기본값 (측정 실패)

    // ===== 방법 1: Sun/Oracle JVM의 getProcessCpuLoad() =====
    // 가장 정확한 방법 - 현재 프로세스만의 CPU 사용률

    if (isSunJVM && sunOsMBean != null) {
      try {
        // 프로세스 CPU 사용률 (0.0 ~ 1.0 범위)
        cpuLoad = sunOsMBean.getProcessCpuLoad() * 100;  // 백분율로 변환

        if (cpuLoad >= 0) {  // 유효한 값 (첫 호출 시 -1 반환 가능)
          return cpuLoad;
        }
      } catch (Exception e) {
        logger.debug("getProcessCpuLoad() 실패: " + e.getMessage());
      }

      // ===== 방법 2: getCpuLoad() - 시스템 전체 CPU =====
      // 덜 정확하지만 유용한 대안

      try {
        // 시스템 전체 CPU 사용률
        cpuLoad = sunOsMBean.getCpuLoad() * 100;

        if (cpuLoad >= 0) {
          return cpuLoad;
        }
      } catch (Exception e) {
        logger.debug("getCpuLoad() 실패: " + e.getMessage());
      }
    }

    // ===== 방법 3: 시스템 로드 평균 (Unix/Linux) =====
    // 모든 JVM에서 지원하지만 정확도가 낮음

    double loadAverage = osMBean.getSystemLoadAverage();

    if (loadAverage >= 0) {  // Windows에서는 -1 반환
      int processors = osMBean.getAvailableProcessors();

      // 로드 평균을 CPU 사용률로 근사 변환
      // 로드 평균 1.0 = 1개 CPU 100% 사용
      cpuLoad = (loadAverage / processors) * 100;
      cpuLoad = Math.min(cpuLoad, 100);  // 100% 초과 방지

      return cpuLoad;
    }

    // ===== 방법 4: 스레드 CPU 시간 기반 (최후의 수단) =====
    // 매우 부정확하지만 모든 환경에서 작동

    if (threadMBean.isThreadCpuTimeSupported()) {
      try {
        long totalCpuTime = 0;

        // 모든 스레드의 CPU 시간 합산
        for (long threadId : threadMBean.getAllThreadIds()) {
          long cpuTime = threadMBean.getThreadCpuTime(threadId);  // 나노초
          if (cpuTime > 0) {
            totalCpuTime += cpuTime;
          }
        }

        // 실제로는 이전 측정값과 비교하여 델타를 계산해야 함
        // 여기서는 단순화를 위해 0 반환
        return 0;

      } catch (Exception e) {
        logger.debug("스레드 CPU 시간 계산 실패: " + e.getMessage());
      }
    }

    return -1;  // 모든 방법 실패
  }

  /**
   * 현재 메트릭 스냅샷 반환
   *
   * 현재까지 수집된 메트릭의 요약 정보를 불변 객체로 반환합니다.
   * 스냅샷은 특정 시점의 메트릭 상태를 캡처합니다.
   *
   * @return MetricSnapshot 불변 스냅샷 객체
   */
  public MetricSnapshot getSnapshot() {
    return new MetricSnapshot(
        metrics.getAvgCpuUsage(),      // 평균 CPU 사용률
        metrics.getMaxCpuUsage(),      // 최대 CPU 사용률
        metrics.getAvgMemoryUsage(),   // 평균 메모리 사용률
        metrics.getMaxMemoryUsage(),   // 최대 메모리 사용률
        metrics.getMaxThreadCount(),   // 최대 스레드 수
        metrics.getTotalGcCount(),     // 총 GC 횟수
        metrics.getTotalGcTime()       // 총 GC 시간
    );
  }

  /**
   * 메트릭 요약을 콘솔에 출력
   *
   * 수집된 모든 메트릭을 보기 좋은 형식으로 출력합니다.
   * 트리 구조로 시각화하여 가독성을 높였습니다.
   */
  public void printSummary() {
    System.out.println("\n📊 시스템 메트릭 요약:");

    // ===== CPU 사용률 출력 =====
    System.out.println("├─ CPU 사용률:");

    if (metrics.getAvgCpuUsage() > 0) {
      System.out.println("│  ├─ 평균: " + String.format("%.1f%%", metrics.getAvgCpuUsage()));
      System.out.println("│  └─ 최대: " + String.format("%.1f%%", metrics.getMaxCpuUsage()));
    } else {
      System.out.println("│  └─ (측정 불가 - JVM 제약)");
    }

    // ===== 메모리 사용량 출력 =====
    System.out.println("├─ 메모리 사용:");
    System.out.println("│  ├─ 평균: " + String.format("%.1f%%", metrics.getAvgMemoryUsage()));
    System.out.println("│  ├─ 최대: " + String.format("%.1f%%", metrics.getMaxMemoryUsage()));
    System.out.println("│  └─ 최대 사용량: " + NetworkUtil.formatBytes(metrics.getMaxMemoryBytes()));

    // ===== 스레드 정보 출력 =====
    System.out.println("├─ 스레드:");
    System.out.println("│  ├─ 평균: " + metrics.getAvgThreadCount());
    System.out.println("│  └─ 최대: " + metrics.getMaxThreadCount());

    // ===== GC 정보 출력 =====
    System.out.println("└─ GC:");
    System.out.println("   ├─ 횟수: " + metrics.getTotalGcCount());
    System.out.println("   └─ 총 시간: " + metrics.getTotalGcTime() + "ms");

    // ===== JVM 정보 추가 출력 =====
    System.out.println("\n📋 JVM 정보:");
    System.out.println("├─ JVM: " + System.getProperty("java.vm.name"));
    System.out.println("├─ 버전: " + System.getProperty("java.version"));
    System.out.println("└─ CPU 코어: " + osMBean.getAvailableProcessors());
  }

  /**
   * 메트릭 객체 반환
   *
   * @return 내부 Metrics 객체 (수정 가능)
   */
  public Metrics getMetrics() {
    return metrics;
  }

  /**
   * 메트릭 저장 클래스
   *
   * 수집된 모든 메트릭 데이터를 저장하고 통계를 계산합니다.
   * synchronized 메서드로 스레드 안전성을 보장합니다.
   */
  public static class Metrics {
    // ========== CPU 메트릭 ==========

    // 샘플 수 (평균 계산용) - AtomicLong으로 스레드 안전
    private final AtomicLong cpuSamples = new AtomicLong(0);
    private double cpuSum = 0;      // CPU 사용률 합계
    private double maxCpu = 0;      // 최대 CPU 사용률

    // ========== 메모리 메트릭 ==========

    private final AtomicLong memorySamples = new AtomicLong(0);
    private double memorySum = 0;       // 메모리 사용률 합계
    private double maxMemory = 0;       // 최대 메모리 사용률 (%)
    private long maxMemoryBytes = 0;    // 최대 메모리 사용량 (바이트)

    // ========== 스레드 메트릭 ==========

    private final AtomicLong threadSamples = new AtomicLong(0);
    private long threadSum = 0;     // 스레드 수 합계
    private int maxThreads = 0;     // 최대 스레드 수

    // ========== GC 메트릭 ==========

    private long totalGcCount = 0;  // 총 GC 횟수
    private long totalGcTime = 0;   // 총 GC 시간 (밀리초)

    /**
     * CPU 사용률 업데이트
     * synchronized로 동시성 제어
     *
     * @param usage CPU 사용률 (0~100%)
     */
    public synchronized void updateCpuUsage(double usage) {
      cpuSamples.incrementAndGet();  // 샘플 수 증가
      cpuSum += usage;                // 합계에 추가
      maxCpu = Math.max(maxCpu, usage);  // 최대값 갱신
    }

    /**
     * 메모리 사용량 업데이트
     *
     * @param usage 메모리 사용률 (0~100%)
     * @param used 사용된 메모리 (바이트)
     * @param max 최대 메모리 (바이트)
     */
    public synchronized void updateMemoryUsage(double usage, long used, long max) {
      memorySamples.incrementAndGet();
      memorySum += usage;
      maxMemory = Math.max(maxMemory, usage);
      maxMemoryBytes = Math.max(maxMemoryBytes, used);
    }

    /**
     * 스레드 수 업데이트
     *
     * @param count 현재 스레드 수
     * @param peak JVM 시작 후 최대 스레드 수
     */
    public synchronized void updateThreadCount(int count, int peak) {
      threadSamples.incrementAndGet();
      threadSum += count;
      maxThreads = Math.max(maxThreads, peak);
    }

    /**
     * GC 정보 업데이트
     *
     * @param count GC 횟수 (누적값)
     * @param time GC 시간 (누적값, 밀리초)
     */
    public synchronized void updateGcInfo(long count, long time) {
      // GC 정보는 이미 누적값이므로 최대값만 유지
      totalGcCount = Math.max(totalGcCount, count);
      totalGcTime = Math.max(totalGcTime, time);
    }

    /**
     * 평균 CPU 사용률 계산
     *
     * @return 평균 CPU 사용률 (%), 샘플 없으면 0
     */
    public double getAvgCpuUsage() {
      long samples = cpuSamples.get();
      return samples > 0 ? cpuSum / samples : 0;
    }

    /**
     * 최대 CPU 사용률 반환
     *
     * @return 최대 CPU 사용률 (%)
     */
    public double getMaxCpuUsage() {
      return maxCpu;
    }

    /**
     * 평균 메모리 사용률 계산
     *
     * @return 평균 메모리 사용률 (%)
     */
    public double getAvgMemoryUsage() {
      long samples = memorySamples.get();
      return samples > 0 ? memorySum / samples : 0;
    }

    /**
     * 최대 메모리 사용률 반환
     *
     * @return 최대 메모리 사용률 (%)
     */
    public double getMaxMemoryUsage() {
      return maxMemory;
    }

    /**
     * 최대 메모리 사용량 반환
     *
     * @return 최대 메모리 사용량 (바이트)
     */
    public long getMaxMemoryBytes() {
      return maxMemoryBytes;
    }

    /**
     * 평균 스레드 수 계산
     *
     * @return 평균 스레드 수
     */
    public int getAvgThreadCount() {
      long samples = threadSamples.get();
      return samples > 0 ? (int)(threadSum / samples) : 0;
    }

    /**
     * 최대 스레드 수 반환
     *
     * @return 최대 스레드 수
     */
    public int getMaxThreadCount() {
      return maxThreads;
    }

    /**
     * 총 GC 횟수 반환
     *
     * @return 총 GC 횟수
     */
    public long getTotalGcCount() {
      return totalGcCount;
    }

    /**
     * 총 GC 시간 반환
     *
     * @return 총 GC 시간 (밀리초)
     */
    public long getTotalGcTime() {
      return totalGcTime;
    }
  }

  /**
   * 메트릭 스냅샷 (불변 클래스)
   *
   * 특정 시점의 메트릭 상태를 캡처한 불변 객체입니다.
   * 모든 필드가 final이고 setter가 없어 스레드 안전합니다.
   *
   * 이 패턴은 "값 객체(Value Object)" 패턴입니다.
   */
  public static class MetricSnapshot {
    public final double avgCpuUsage;       // 평균 CPU 사용률
    public final double maxCpuUsage;       // 최대 CPU 사용률
    public final double avgMemoryUsage;    // 평균 메모리 사용률
    public final double maxMemoryUsage;    // 최대 메모리 사용률
    public final int maxThreadCount;       // 최대 스레드 수
    public final long totalGcCount;        // 총 GC 횟수
    public final long totalGcTime;         // 총 GC 시간

    /**
     * MetricSnapshot 생성자
     *
     * 모든 필드를 초기화하는 생성자입니다.
     * 생성 후에는 수정할 수 없습니다 (불변성).
     */
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
   *
   * MetricsCollector를 독립적으로 테스트할 수 있는 메인 메서드입니다.
   * 5초간 메트릭을 수집하고 결과를 출력합니다.
   *
   * 실행 방법:
   * java com.experiment.timeout_lab.benchmark.MetricsCollector
   *
   * @param args 명령줄 인자 (사용하지 않음)
   * @throws InterruptedException 스레드 대기 중 인터럽트 발생 시
   */
  public static void main(String[] args) throws InterruptedException {
    // 메트릭 수집기 생성
    MetricsCollector collector = new MetricsCollector();

    System.out.println("메트릭 수집 테스트 시작...");

    // 모니터링 시작
    collector.startMonitoring();

    // 5초간 대기 (이 동안 1초마다 메트릭 수집)
    Thread.sleep(5000);

    // 모니터링 종료
    collector.stopMonitoring();

    // 수집된 메트릭 출력
    collector.printSummary();
  }
}