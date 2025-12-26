package com.experiment.timeout_lab.benchmark;

import com.experiment.timeout_lab.util.Logger;
import com.experiment.timeout_lab.util.NetworkUtil;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * HTML 형식의 벤치마크 리포트 생성기
 *
 * 실행 결과를 시각적으로 보기 좋은 HTML 형식으로 변환합니다.
 * 차트와 테이블을 포함하여 결과를 직관적으로 확인할 수 있습니다.
 */
public class Report {

  private static final Logger logger = new Logger(Report.class);
  private static final String REPORTS_DIR = "results/reports/";

  private final List<BenchmarkRunner.BenchmarkResult> results;
  private final MetricsCollector.Metrics metrics;

  public Report(List<BenchmarkRunner.BenchmarkResult> results,
      MetricsCollector.Metrics metrics) {
    this.results = results;
    this.metrics = metrics;

    // 리포트 디렉토리 생성
    createReportsDirectory();
  }

  /**
   * HTML 리포트 생성
   */
  public void generateHtmlReport() {
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    String filename = REPORTS_DIR + "report_" + timestamp + ".html";

    try (FileWriter writer = new FileWriter(filename)) {
      writer.write(generateHtml());
      logger.info("✅ HTML 리포트 생성 완료: " + filename);
    } catch (IOException e) {
      logger.error("리포트 생성 실패", e);
    }
  }

  /**
   * HTML 내용 생성
   */
  private String generateHtml() {
    StringBuilder html = new StringBuilder();

    // HTML 헤더
    html.append("<!DOCTYPE html>\n");
    html.append("<html lang=\"ko\">\n");
    html.append("<head>\n");
    html.append("    <meta charset=\"UTF-8\">\n");
    html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
    html.append("    <title>Timeout Experiment Benchmark Report</title>\n");
    html.append("    <script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script>\n");
    html.append(generateStyles());
    html.append("</head>\n");
    html.append("<body>\n");

    // 헤더
    html.append("    <div class=\"container\">\n");
    html.append("        <header>\n");
    html.append("            <h1>🕐 Network Timeout Experiment Report</h1>\n");
    html.append("            <p class=\"timestamp\">Generated: ").append(LocalDateTime.now()).append("</p>\n");
    html.append("        </header>\n");

    // 요약 섹션
    html.append(generateSummarySection());

    // 시나리오 결과 섹션
    html.append(generateScenarioSection());

    // 메트릭 섹션
    html.append(generateMetricsSection());

    // 차트 섹션
    html.append(generateChartSection());

    // 권장사항 섹션
    html.append(generateRecommendationSection());

    // 푸터
    html.append("        <footer>\n");
    html.append("            <p>© 2025 Timeout Lab - Pure Java Socket Programming Experiment</p>\n");
    html.append("        </footer>\n");
    html.append("    </div>\n");

    // JavaScript
    html.append(generateScripts());

    html.append("</body>\n");
    html.append("</html>\n");

    return html.toString();
  }

  /**
   * CSS 스타일 생성
   */
  private String generateStyles() {
    return """
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body { 
                    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; 
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    min-height: 100vh;
                    padding: 20px;
                }
                .container { 
                    max-width: 1200px; 
                    margin: 0 auto; 
                    background: white; 
                    border-radius: 20px; 
                    box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                    overflow: hidden;
                }
                header { 
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); 
                    color: white; 
                    padding: 40px;
                    text-align: center;
                }
                h1 { font-size: 2.5em; margin-bottom: 10px; }
                .timestamp { opacity: 0.9; font-size: 0.9em; }
                section { padding: 40px; border-bottom: 1px solid #eee; }
                h2 { 
                    color: #333; 
                    margin-bottom: 20px; 
                    font-size: 1.8em;
                    border-left: 4px solid #667eea;
                    padding-left: 15px;
                }
                .summary-grid { 
                    display: grid; 
                    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); 
                    gap: 20px; 
                    margin: 20px 0; 
                }
                .summary-card { 
                    background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%); 
                    padding: 20px; 
                    border-radius: 10px; 
                    text-align: center;
                }
                .summary-card h3 { color: #555; font-size: 0.9em; margin-bottom: 10px; }
                .summary-card .value { font-size: 2em; color: #667eea; font-weight: bold; }
                table { 
                    width: 100%; 
                    border-collapse: collapse; 
                    margin: 20px 0;
                    box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                }
                th { 
                    background: #667eea; 
                    color: white; 
                    padding: 15px; 
                    text-align: left;
                    font-weight: 600;
                }
                td { 
                    padding: 12px 15px; 
                    border-bottom: 1px solid #eee; 
                }
                tr:hover { background: #f5f7fa; }
                .success { color: #4caf50; font-weight: bold; }
                .failure { color: #f44336; font-weight: bold; }
                .timeout { color: #ff9800; font-weight: bold; }
                .chart-container { 
                    position: relative; 
                    height: 400px; 
                    margin: 30px 0;
                }
                .recommendation {
                    background: #e8f5e9;
                    border-left: 4px solid #4caf50;
                    padding: 20px;
                    margin: 20px 0;
                    border-radius: 5px;
                }
                .warning {
                    background: #fff3e0;
                    border-left: 4px solid #ff9800;
                    padding: 20px;
                    margin: 20px 0;
                    border-radius: 5px;
                }
                footer { 
                    background: #f5f5f5; 
                    text-align: center; 
                    padding: 20px; 
                    color: #666;
                }
            </style>
            """;
  }

  /**
   * 요약 섹션 생성
   */
  private String generateSummarySection() {
    int totalTests = results.stream().mapToInt(r -> r.successCount + r.failureCount).sum();
    int totalSuccess = results.stream().mapToInt(r -> r.successCount).sum();
    int totalFailure = results.stream().mapToInt(r -> r.failureCount).sum();
    int totalTimeout = results.stream().mapToInt(r -> r.timeoutCount).sum();

    StringBuilder html = new StringBuilder();
    html.append("        <section>\n");
    html.append("            <h2>📊 실행 요약</h2>\n");
    html.append("            <div class=\"summary-grid\">\n");
    html.append("                <div class=\"summary-card\">\n");
    html.append("                    <h3>총 테스트</h3>\n");
    html.append("                    <div class=\"value\">").append(totalTests).append("</div>\n");
    html.append("                </div>\n");
    html.append("                <div class=\"summary-card\">\n");
    html.append("                    <h3>성공</h3>\n");
    html.append("                    <div class=\"value success\">").append(totalSuccess).append("</div>\n");
    html.append("                </div>\n");
    html.append("                <div class=\"summary-card\">\n");
    html.append("                    <h3>실패</h3>\n");
    html.append("                    <div class=\"value failure\">").append(totalFailure).append("</div>\n");
    html.append("                </div>\n");
    html.append("                <div class=\"summary-card\">\n");
    html.append("                    <h3>타임아웃</h3>\n");
    html.append("                    <div class=\"value timeout\">").append(totalTimeout).append("</div>\n");
    html.append("                </div>\n");
    html.append("            </div>\n");
    html.append("        </section>\n");

    return html.toString();
  }

  /**
   * 시나리오 결과 섹션 생성
   */
  private String generateScenarioSection() {
    StringBuilder html = new StringBuilder();
    html.append("        <section>\n");
    html.append("            <h2>🎯 시나리오별 결과</h2>\n");
    html.append("            <table>\n");
    html.append("                <thead>\n");
    html.append("                    <tr>\n");
    html.append("                        <th>시나리오</th>\n");
    html.append("                        <th>성공</th>\n");
    html.append("                        <th>실패</th>\n");
    html.append("                        <th>타임아웃</th>\n");
    html.append("                        <th>평균 응답시간</th>\n");
    html.append("                        <th>총 실행시간</th>\n");
    html.append("                    </tr>\n");
    html.append("                </thead>\n");
    html.append("                <tbody>\n");

    for (BenchmarkRunner.BenchmarkResult result : results) {
      html.append("                    <tr>\n");
      html.append("                        <td>").append(result.scenarioName).append("</td>\n");
      html.append("                        <td class=\"success\">").append(result.successCount).append("</td>\n");
      html.append("                        <td class=\"failure\">").append(result.failureCount).append("</td>\n");
      html.append("                        <td class=\"timeout\">").append(result.timeoutCount).append("</td>\n");
      html.append("                        <td>").append(String.format("%.2f ms", result.avgResponseTime)).append("</td>\n");
      html.append("                        <td>").append(NetworkUtil.formatDuration(result.totalTime)).append("</td>\n");
      html.append("                    </tr>\n");
    }

    html.append("                </tbody>\n");
    html.append("            </table>\n");
    html.append("        </section>\n");

    return html.toString();
  }

  /**
   * 메트릭 섹션 생성
   */
  private String generateMetricsSection() {
    StringBuilder html = new StringBuilder();
    html.append("        <section>\n");
    html.append("            <h2>💻 시스템 메트릭</h2>\n");
    html.append("            <div class=\"summary-grid\">\n");
    html.append("                <div class=\"summary-card\">\n");
    html.append("                    <h3>평균 CPU 사용률</h3>\n");
    html.append("                    <div class=\"value\">").append(String.format("%.1f%%", metrics.getAvgCpuUsage())).append("</div>\n");
    html.append("                </div>\n");
    html.append("                <div class=\"summary-card\">\n");
    html.append("                    <h3>최대 메모리</h3>\n");
    html.append("                    <div class=\"value\">").append(NetworkUtil.formatBytes(metrics.getMaxMemoryBytes())).append("</div>\n");
    html.append("                </div>\n");
    html.append("                <div class=\"summary-card\">\n");
    html.append("                    <h3>최대 스레드 수</h3>\n");
    html.append("                    <div class=\"value\">").append(metrics.getMaxThreadCount()).append("</div>\n");
    html.append("                </div>\n");
    html.append("                <div class=\"summary-card\">\n");
    html.append("                    <h3>GC 횟수</h3>\n");
    html.append("                    <div class=\"value\">").append(metrics.getTotalGcCount()).append("</div>\n");
    html.append("                </div>\n");
    html.append("            </div>\n");
    html.append("        </section>\n");

    return html.toString();
  }

  /**
   * 차트 섹션 생성
   */
  private String generateChartSection() {
    StringBuilder html = new StringBuilder();
    html.append("        <section>\n");
    html.append("            <h2>📈 성능 차트</h2>\n");
    html.append("            <div class=\"chart-container\">\n");
    html.append("                <canvas id=\"performanceChart\"></canvas>\n");
    html.append("            </div>\n");
    html.append("        </section>\n");

    return html.toString();
  }

  /**
   * 권장사항 섹션 생성
   */
  private String generateRecommendationSection() {
    StringBuilder html = new StringBuilder();
    html.append("        <section>\n");
    html.append("            <h2>💡 권장사항</h2>\n");
    html.append("            <div class=\"recommendation\">\n");
    html.append("                <h3>✅ 모범 사례</h3>\n");
    html.append("                <ul>\n");
    html.append("                    <li>모든 네트워크 연결에 적절한 타임아웃을 설정하세요</li>\n");
    html.append("                    <li>Connect Timeout: 1-5초, Read Timeout: 5-30초 권장</li>\n");
    html.append("                    <li>Circuit Breaker 패턴을 사용하여 장애 전파를 방지하세요</li>\n");
    html.append("                </ul>\n");
    html.append("            </div>\n");
    html.append("            <div class=\"warning\">\n");
    html.append("                <h3>⚠️ 주의사항</h3>\n");
    html.append("                <ul>\n");
    html.append("                    <li>타임아웃 미설정 시 스레드풀 고갈로 서비스 장애 발생</li>\n");
    html.append("                    <li>너무 짧은 타임아웃은 정상 요청도 실패시킬 수 있음</li>\n");
    html.append("                    <li>네트워크 환경에 따라 적절한 값 조정 필요</li>\n");
    html.append("                </ul>\n");
    html.append("            </div>\n");
    html.append("        </section>\n");

    return html.toString();
  }

  /**
   * JavaScript 생성
   */
  private String generateScripts() {
    StringBuilder js = new StringBuilder();
    js.append("<script>\n");
    js.append("    const ctx = document.getElementById('performanceChart').getContext('2d');\n");
    js.append("    new Chart(ctx, {\n");
    js.append("        type: 'bar',\n");
    js.append("        data: {\n");
    js.append("            labels: [");

    // 라벨 추가
    for (int i = 0; i < results.size(); i++) {
      if (i > 0) js.append(", ");
      js.append("'").append(results.get(i).scenarioName).append("'");
    }

    js.append("],\n");
    js.append("            datasets: [{\n");
    js.append("                label: '평균 응답시간 (ms)',\n");
    js.append("                data: [");

    // 데이터 추가
    for (int i = 0; i < results.size(); i++) {
      if (i > 0) js.append(", ");
      js.append(results.get(i).avgResponseTime);
    }

    js.append("],\n");
    js.append("                backgroundColor: 'rgba(102, 126, 234, 0.5)',\n");
    js.append("                borderColor: 'rgba(102, 126, 234, 1)',\n");
    js.append("                borderWidth: 2\n");
    js.append("            }]\n");
    js.append("        },\n");
    js.append("        options: {\n");
    js.append("            responsive: true,\n");
    js.append("            maintainAspectRatio: false,\n");
    js.append("            scales: {\n");
    js.append("                y: {\n");
    js.append("                    beginAtZero: true\n");
    js.append("                }\n");
    js.append("            }\n");
    js.append("        }\n");
    js.append("    });\n");
    js.append("</script>\n");

    return js.toString();
  }

  /**
   * 리포트 디렉토리 생성
   */
  private void createReportsDirectory() {
    java.io.File dir = new java.io.File(REPORTS_DIR);
    if (!dir.exists()) {
      dir.mkdirs();
    }
  }
}