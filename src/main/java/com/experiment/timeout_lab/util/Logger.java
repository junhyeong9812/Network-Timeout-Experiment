package com.experiment.timeout_lab.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 간단한 로깅 유틸리티
 * */
public class Logger {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public enum Level {
        DEBUG("[DEBUG]"),
        INFO("[INFO]"),
        WARN("[WARN]"),
        ERROR("[ERROR]");

        private final String prefix;

        Level(String prefix) {
            this.prefix = prefix;
        }

    }
    private static Level currentLevel = Level.INFO;
    private final String className;

    public Logger(Class<?> clazz) {
        this.className = clazz.getSimpleName();
    }

    public static void setLevel(Level level) {
        currentLevel = level;
    }

    public void debug(String message) {
        log(Level.DEBUG, message);
    }

    public void info(String message) {
        log(Level.INFO, message);
    }

    public void warn(String message) {
        log(Level.WARN, message);
    }

    public void error(String message) {
        log(Level.ERROR, message);
    }

    public void error(String message, Throwable throwable) {
        log(Level.ERROR, message + " - " + throwable.getMessage());
        if (currentLevel.ordinal() <= Level.DEBUG.ordinal()) {
            throwable.printStackTrace();
        }
    }


    private void log(Level level, String message) {
        if (level.ordinal() >= currentLevel.ordinal()) {
            String timestamp = LocalDateTime.now().format(FORMATTER);
            String threadName = Thread.currentThread().getName();
            System.out.printf("%s %s [%s] %s - %s%n",
                    timestamp, level.prefix, threadName, className, message);
        }
    }

    // 구분선 출력
    public void separator() {
        System.out.println("=".repeat(80));
    }

    // 진행 상황 출력
    public void progress(String label, int current, int total) {
        // 전체 진행률을 % 값으로 환산 (0~100 범위)
        int percentage = (current * 100) / total;

        // 진행바 전체 길이(총 출력 문자 수)
        int barLength = 50;

        // 현재 진행 비율을 barLength 길이에 맞게 변환하여 채워질 칸 수 계산
        int filledLength = (barLength * current) / total;

        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < barLength; i++) {
            if (i < filledLength) {
                bar.append("█"); // 진행된 구간
            } else {
                bar.append("░");  // 아직 진행되지 않은 구간 표시
            }
        }
        bar.append("]");

        System.out.printf("\r%s: %s %d%% (%d/%d)",
                label, bar, percentage, current, total);

        if (current == total) {
            System.out.println();
        }
    }

}
