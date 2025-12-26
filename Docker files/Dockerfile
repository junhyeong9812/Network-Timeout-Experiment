# Multi-stage build for Java application
FROM gradle:8.5-jdk21 AS build

# 작업 디렉토리 설정
WORKDIR /app

# Gradle 설정 파일 복사
COPY build.gradle settings.gradle ./

# 소스 코드 복사
COPY src ./src

# 프로젝트 빌드
RUN gradle build --no-daemon

# 실행 이미지
FROM openjdk:21-slim

# 작업 디렉토리 설정
WORKDIR /app

# 빌드된 JAR 파일 복사
COPY --from=build /app/build/classes ./classes
COPY --from=build /app/build/libs ./libs

# 결과 디렉토리 생성
RUN mkdir -p logs results/benchmarks results/reports

# 포트 노출 (서버용)
EXPOSE 8080 8081 8082 8083 8084 8085 8086

# JVM 옵션 설정
ENV JAVA_OPTS="--enable-preview -Xms256m -Xmx1024m"

# 실행 명령 (기본: 메인 애플리케이션)
ENTRYPOINT ["java"]
CMD ["-cp", "classes/java/main", "com.experiment.timeout_lab.TimeoutLabApplication"]