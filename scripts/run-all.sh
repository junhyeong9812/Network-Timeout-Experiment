#!/bin/bash
# ============================================
# Timeout Lab - 전체 실행 스크립트 (Linux/Mac)
# ============================================

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

clear

echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║           Network Timeout Experiment Lab v1.0               ║"
echo "║                    Linux/Mac Edition                        ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

# Java 버전 확인
echo -e "${BLUE}[1/4] Java 버전 확인 중...${NC}"
java_version=$(java -version 2>&1 | head -n 1)
if [ $? -eq 0 ]; then
    echo -e "✅ Java 발견: $java_version"
else
    echo -e "${RED}❌ Java가 설치되어 있지 않습니다!${NC}"
    echo "Java 21 이상을 설치해주세요."
    exit 1
fi

# 프로젝트 빌드
echo ""
echo -e "${BLUE}[2/4] 프로젝트 빌드 중...${NC}"
if [ -f "./gradlew" ]; then
    chmod +x ./gradlew
    ./gradlew clean build
    if [ $? -ne 0 ]; then
        echo -e "${RED}❌ 빌드 실패!${NC}"
        exit 1
    fi
    echo -e "${GREEN}✅ 빌드 성공${NC}"
else
    echo -e "${YELLOW}⚠️  Gradle wrapper가 없습니다. gradle 명령으로 시도합니다...${NC}"
    gradle clean build
    if [ $? -ne 0 ]; then
        echo -e "${RED}❌ 빌드 실패!${NC}"
        exit 1
    fi
fi

# 결과 디렉토리 생성
echo ""
echo -e "${BLUE}[3/4] 디렉토리 준비 중...${NC}"
mkdir -p logs
mkdir -p results/benchmarks
mkdir -p results/reports
echo -e "${GREEN}✅ 디렉토리 생성 완료${NC}"

# 메인 애플리케이션 실행
echo ""
echo -e "${BLUE}[4/4] 애플리케이션 실행 중...${NC}"
echo ""

if [ -f "./gradlew" ]; then
    ./gradlew run
else
    gradle run
fi

echo ""
echo -e "${GREEN}✅ 실행 완료!${NC}"
echo ""
echo "결과 확인:"
echo "- 로그: logs/"
echo "- 벤치마크: results/benchmarks/"
echo "- 리포트: results/reports/"
echo ""