package com.experiment.timeout_lab.util;

/**
 * 프로젝트 전역 상수 정의
 * */
public class Constants {

    // 서버 관련 상수
    public static final int DEFAULT_SERVER_PORT = 8080;
    public static final int SERVER_BACKLOG = 50;

    // 타입아웃 기분값 (밀리초)
    public static final int DEFAULT_CONNECT_TIMEOUT = 5000; // 5초
    public static final int DEFAULT_READ_TIMEOUT = 10000;   // 10초
    public static final int DEFAULT_WRITE_TIMEOUT = 10000;  // 10초

    // 스레드풀 설정
    public static final int DEAFULT_THREAD_POOL_SIZE = 10;
    public static final int MAX_THREAD_POOL_SIZE = 50;
    public static final int THREAD_POOL_QUEUE_SIZE = 100;

    // 벤치마크 설정
    public static final int BENCHMARK_ITERATIONS = 100;
    public static final int WARMUP_ITERATIONS = 10;

    // 버퍼 크기
    public static final int BUFFER_SIZE = 8192; // 8KB
    public static final int LARGE_BUFFER_SIZE = 1048576; // 1MB

    // 실험 시나리오 타입
    public enum ScenarioType {
        CONNECT_TIMEOUT("Connect Timeout"),
        READ_TIMEOUT("Read Timeout"),
        WRITE_TIMEOUT("Write Timeout"),
        THREAD_EXHAUSTION("Thread Pool Exhaustion");

        private final String description;

        ScenarioType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // 서버 동작 모드
    public enum ServerMode {
        NORMAL("정상 동작"),
        NO_ACCEPT("Accept 안 함 - Connect Timeout 유발"),
        NO_RESPONSE("응답 없음 - Read Timeout 유발"),
        SLOW_RESPONSE("느린 응답"),
        SLOW_READ("느린 읽기 - Write Timeout 유발"),
        PARTIAL_READ("일부만 읽기");

        private final String description;

        ServerMode(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    private Constants() {
        // 인스턴스화 방지
    }
}
