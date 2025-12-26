@echo off
REM ============================================
REM Timeout Lab - 시나리오 실행 스크립트 (Windows)
REM ============================================

setlocal enabledelayedexpansion

echo.
echo ╔══════════════════════════════════════════╗
echo ║     Timeout Scenario Runner (Windows)    ║
echo ╚══════════════════════════════════════════╝
echo.

REM 시나리오 선택
echo 실행할 시나리오를 선택하세요:
echo.
echo   1. Connect Timeout Scenario
echo   2. Read Timeout Scenario
echo   3. Write Timeout Scenario
echo   4. Thread Pool Exhaustion Scenario
echo   5. All Scenarios (Benchmark)
echo   0. 종료
echo.
set /p choice="선택 (0-5): "

REM 클래스패스 설정
set CP=build\classes\java\main

REM 시나리오별 실행
if "%choice%"=="1" (
    echo.
    echo Connect Timeout Scenario 실행 중...
    java -cp %CP% com.experiment.timeout_lab.scenario.ConnectTimeoutScenario
) else if "%choice%"=="2" (
    echo.
    echo Read Timeout Scenario 실행 중...
    java -cp %CP% com.experiment.timeout_lab.scenario.ReadTimeoutScenario
) else if "%choice%"=="3" (
    echo.
    echo Write Timeout Scenario 실행 중...
    java -cp %CP% com.experiment.timeout_lab.scenario.WriteTimeoutScenario
) else if "%choice%"=="4" (
    echo.
    echo Thread Pool Exhaustion Scenario 실행 중...
    java -cp %CP% com.experiment.timeout_lab.scenario.ThreadExhaustionScenario
) else if "%choice%"=="5" (
    echo.
    echo 전체 벤치마크 실행 중...
    java -cp %CP% com.experiment.timeout_lab.benchmark.BenchmarkRunner
) else if "%choice%"=="0" (
    echo.
    echo 프로그램을 종료합니다.
    exit /b 0
) else (
    echo.
    echo ❌ 잘못된 선택입니다!
)

echo.
pause