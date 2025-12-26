#!/bin/bash
# ============================================
# Timeout Lab - 시나리오 실행 스크립트 (Linux/Mac)
# ============================================

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

clear

echo ""
echo "╔══════════════════════════════════════════╗"
echo "║   Timeout Scenario Runner (Linux/Mac)    ║"
echo "╚══════════════════════════════════════════╗"
echo ""

# 시나리오 선택
echo "실행할 시나리오를 선택하세요:"
echo ""
echo "  1. Connect Timeout Scenario"
echo "  2. Read Timeout Scenario"
echo "  3. Write Timeout Scenario"
echo "  4. Thread Pool Exhaustion Scenario"
echo "  5. All Scenarios (Benchmark)"
echo "  0. 종료"
echo ""
read -p "선택 (0-5): " choice

# 클래스패스 설정
CP="build/classes/java/main"

# Java 실행 옵션
JAVA_OPTS="--enable-preview -Xms256m -Xmx1024m"

# 시나리오별 실행
case $choice in
    1)
        echo ""
        echo -e "${BLUE}Connect Timeout Scenario 실행 중...${NC}"
        java $JAVA_OPTS -cp $CP com.experiment.timeout_lab.scenario.ConnectTimeoutScenario
        ;;
    2)
        echo ""
        echo -e "${BLUE}Read Timeout Scenario 실행 중...${NC}"
        java $JAVA_OPTS -cp $CP com.experiment.timeout_lab.scenario.ReadTimeoutScenario
        ;;
    3)
        echo ""
        echo -e "${BLUE}Write Timeout Scenario 실행 중...${NC}"
        java $JAVA_OPTS -cp $CP com.experiment.timeout_lab.scenario.WriteTimeoutScenario
        ;;
    4)
        echo ""
        echo -e "${BLUE}Thread Pool Exhaustion Scenario 실행 중...${NC}"
        java $JAVA_OPTS -cp $CP com.experiment.timeout_lab.scenario.ThreadExhaustionScenario
        ;;
    5)
        echo ""
        echo -e "${BLUE}전체 벤치마크 실행 중...${NC}"
        java $JAVA_OPTS -cp $CP com.experiment.timeout_lab.benchmark.BenchmarkRunner
        ;;
    0)
        echo ""
        echo "프로그램을 종료합니다."
        exit 0
        ;;
    *)
        echo ""
        echo -e "${RED}❌ 잘못된 선택입니다!${NC}"
        ;;
esac

echo ""
echo -e "${GREEN}실행 완료${NC}"