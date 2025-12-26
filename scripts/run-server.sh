#!/bin/bash
# ============================================
# Timeout Lab - 서버 실행 스크립트 (Linux/Mac)
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
echo "║    Problematic Server (Linux/Mac)        ║"
echo "╚══════════════════════════════════════════╗"
echo ""

# 서버 모드 선택
echo "서버 모드를 선택하세요:"
echo ""
echo "  1. NORMAL - 정상 에코 서버"
echo "  2. NO_ACCEPT - Connect Timeout 유발"
echo "  3. NO_RESPONSE - Read Timeout 유발"
echo "  4. SLOW_RESPONSE - 느린 응답"
echo "  5. SLOW_READ - Write Timeout 유발"
echo "  6. PARTIAL_READ - 일부만 읽기"
echo "  0. 종료"
echo ""
read -p "선택 (0-6): " choice

# 포트 입력
read -p "포트 번호 (기본: 8080): " port
if [ -z "$port" ]; then
    port=8080
fi

# 클래스패스 설정
CP="build/classes/java/main"

# 서버 모드 매핑
case $choice in
    1) mode="NORMAL" ;;
    2) mode="NO_ACCEPT" ;;
    3) mode="NO_RESPONSE" ;;
    4) mode="SLOW_RESPONSE" ;;
    5) mode="SLOW_READ" ;;
    6) mode="PARTIAL_READ" ;;
    0)
        echo "프로그램을 종료합니다."
        exit 0
        ;;
    *)
        echo -e "${RED}❌ 잘못된 선택입니다!${NC}"
        exit 1
        ;;
esac

echo ""
echo -e "${BLUE}서버 시작 중...${NC}"
echo "모드: $mode"
echo "포트: $port"
echo ""
echo -e "${YELLOW}Ctrl+C를 눌러 서버를 종료하세요${NC}"
echo ""

java -cp $CP com.experiment.timeout_lab.server.ProblematicServer $mode $port