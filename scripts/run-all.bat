@echo off
REM ============================================
REM Timeout Lab - 전체 실행 스크립트 (Windows)
REM ============================================

echo.
echo ╔══════════════════════════════════════════════════════════════╗
echo ║           Network Timeout Experiment Lab v1.0               ║
echo ║                    Windows Edition                          ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.

REM Java 버전 확인
echo [1/4] Java 버전 확인 중...
java -version 2>&1 | findstr "version"
if errorlevel 1 (
    echo ❌ Java가 설치되어 있지 않습니다!
    echo Java 21 이상을 설치해주세요.
    pause
    exit /b 1
)

REM 프로젝트 빌드
echo.
echo [2/4] 프로젝트 빌드 중...
call gradlew.bat clean build
if errorlevel 1 (
    echo ❌ 빌드 실패!
    pause
    exit /b 1
)

REM 결과 디렉토리 생성
echo.
echo [3/4] 디렉토리 준비 중...
if not exist "logs" mkdir logs
if not exist "results\benchmarks" mkdir results\benchmarks
if not exist "results\reports" mkdir results\reports

REM 메인 애플리케이션 실행
echo.
echo [4/4] 애플리케이션 실행 중...
echo.
call gradlew.bat run

echo.
echo ✅ 실행 완료!
echo.
echo 결과 확인:
echo - 로그: logs\
echo - 벤치마크: results\benchmarks\
echo - 리포트: results\reports\
echo.
pause