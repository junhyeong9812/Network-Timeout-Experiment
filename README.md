# 🚀 Java Timeout Lab - 네트워크 타임아웃 실험실

> Java 네트워크 프로그래밍에서 타임아웃 설정의 중요성을 실험적으로 증명하는 프로젝트

## 📖 프로젝트 소개

이 프로젝트는 실제 운영 환경에서 발생할 수 있는 다양한 타임아웃 시나리오를 시뮬레이션하고, 타임아웃 미설정으로 인한 장애를 재현합니다. 특히 스레드풀 고갈(Thread Pool Exhaustion)이 어떻게 전체 시스템을 마비시킬 수 있는지 실증적으로 보여줍니다.

### 주요 특징
- ✅ 4가지 타임아웃 시나리오 실험
- ✅ 문제 서버 시뮬레이션
- ✅ 실시간 메트릭 수집
- ✅ 벤치마크 및 리포트 생성

---

## 🔬 실험 시나리오

### 1. **Connect Timeout** - 연결 시도 타임아웃
- 서버가 accept()를 하지 않을 때 발생
- TCP 백로그 효과 관찰

### 2. **Read Timeout** - 데이터 읽기 타임아웃
- 서버가 응답하지 않거나 느리게 응답할 때 발생
- setSoTimeout() 동작 검증

### 3. **Write Timeout** - 데이터 전송 타임아웃
- TCP 버퍼가 가득 찰 때 발생
- Java의 한계와 해결방법

### 4. **Thread Pool Exhaustion** - 스레드풀 고갈
- 타임아웃 미설정으로 인한 서비스 마비
- 연쇄 장애(Cascading Failure) 재현

---

## 📊 전체 벤치마크 실행 결과

### 실행 환경
- **OS**: Windows 11
- **Java**: OpenJDK 21.0.7
- **CPU**: 24 cores
- **Memory**: 9.43 GB
- **실행 시간**: 2025-12-26 22:47 ~ 22:52 (4분 56초)

### 종합 결과

| 시나리오 | 성공률 | 평균 응답시간 | 핵심 발견 |
|---------|--------|--------------|----------|
| **Connect Timeout** | 0% | 11.75ms | 백로그 효과, Connection Refused |
| **Read Timeout** | 75% | 9,081ms | 정확한 타임아웃 작동 |
| **Write Timeout** | 75% | 1,257ms | Future로 구현 가능 |
| **Thread Exhaustion** | 100%* | 17,060ms | 타임아웃 필수성 증명 |

*Thread Exhaustion은 타임아웃 설정 시 100% 성공

---

## 🔍 시나리오별 상세 결과

### 1. Connect Timeout 시나리오

#### 실험 설정
- **서버 모드**: NO_ACCEPT (accept() 호출하지 않음)
- **백로그**: 1 (OS 큐에 1개만 허용)
- **테스트**: 20회

#### 결과
```
✅ 워밍업 첫 번째 연결: 성공 (백로그 큐에 진입)
❌ 나머지 모든 연결: Connection Refused (즉시 거부)

실행 통계:
- 성공: 0%
- 평균 응답 시간: 11.75ms (즉시 거부)
```

#### 트러블슈팅 - 백로그 문제

**문제 상황**
```java
// 초기 코드
serverSocket = new ServerSocket(port);  // 기본 백로그 = 50
// 결과: 50개 연결이 큐에 들어가 타임아웃 발생하지 않음
```

**해결 방법**
```java
// 수정된 코드
serverSocket = new ServerSocket(port, 1);  // 백로그 = 1
serverSocket.setReuseAddress(false);
// 결과: 첫 연결만 수락, 나머지는 즉시 거부
```

---

### 2. Read Timeout 시나리오

#### 실험 설정
- **서버 모드 1**: NO_RESPONSE (무응답)
- **서버 모드 2**: SLOW_RESPONSE (1초에 1바이트)

#### 결과

**NO_RESPONSE 모드**
```
Timeout 설정: 1000ms → 실제 대기: 1009ms (오차 0.9%)
Timeout 설정: 5000ms → 실제 대기: 5010ms (오차 0.2%)
```

#### 트러블슈팅 - SLOW_RESPONSE 이해

**혼란스러웠던 점**
```
예상: 3초 타임아웃 → 3초 후 실패
실제: 15초 후 전체 응답 수신 성공
```

**원인 파악**
```java
socket.setSoTimeout(3000);  // Read Timeout 3초

// Read Timeout의 실제 의미
// ❌ "전체 응답을 3초 내에 받아야 함"
// ✅ "다음 데이터 조각이 3초 내에 와야 함"

// 서버가 1초마다 1바이트 전송
// → 매번 타임아웃 타이머 리셋
// → 15초 걸려도 타임아웃 없음
```

---

### 3. Write Timeout 시나리오 ⚠️

#### 실험 설정
- **서버 모드**: PARTIAL_READ (10바이트만 읽고 멈춤)
- **데이터 크기**: 1MB

#### 발견된 문제 - 무한 블로킹

**문제 발생**
```
[22:22:46] 1MB 데이터 전송 시작
[22:22:46] 서버가 10바이트만 읽음
[22:42:46] 20분 경과... 여전히 블로킹 중
[수동 중단 필요]
```

**문제 분석**
```java
// Java의 근본적 한계
socket.setSoTimeout(5000);      // ✅ Read Timeout 지원
socket.setWriteTimeout(5000);   // ❌ 메서드 없음!

// write()가 블로킹되면?
outputStream.write(largeData);  // 영원히 대기...
```

**TCP 버퍼 메커니즘**
```
1MB 전송 시도
↓
10바이트: 서버가 읽음
999,990바이트: TCP 송신 버퍼에 갇힘
↓
송신 버퍼 가득 → write() 블로킹
수신 버퍼 가득 → 서버가 안 읽음
↓
데드락 상태 (무한 대기)
```

#### 해결 방법 - Future를 사용한 타임아웃

**수정된 WriteTimeoutScenario**
```java
// ExecutorService 추가
private ExecutorService writeExecutor = Executors.newFixedThreadPool(2);
private static final long WRITE_TIMEOUT = 5000; // 5초

// Write 작업을 Future로 실행
Future<Boolean> writeFuture = writeExecutor.submit(() -> {
    return client.sendData(data);
});

try {
    // 5초 타임아웃 적용
    sent = writeFuture.get(WRITE_TIMEOUT, TimeUnit.MILLISECONDS);
} catch (TimeoutException e) {
    // Write Timeout 발생!
    writeFuture.cancel(true);  // 블로킹된 스레드 중단
    logger.warn("Write Timeout 발생! ({}ms 초과)", WRITE_TIMEOUT);
}
```

**수정 후 결과**
```
1MB + PARTIAL_READ 테스트:
- Write Timeout 발생: 6회
- 타임아웃 시간: 정확히 5초
- 블로킹 해제: 성공
- 테스트 정상 완료
```

---

### 4. Thread Pool Exhaustion 시나리오 ⭐

#### 실험 설정
- **스레드풀**: 10개
- **동시 요청**: 50개
- **비교**: 타임아웃 미설정 vs 3초 설정

#### 결과 비교

| 설정 | 완료 요청 | 블로킹 스레드 | 서비스 상태 | 결과 |
|------|----------|--------------|------------|------|
| **타임아웃 없음** | 50 | 10/10 (100%) | DEAD | 30초 후 강제 종료 |
| **타임아웃 3초** | 50 | 0/10 (0%) | HEALTHY | 정상 처리 |

#### 스레드풀 고갈 시각화

**타임아웃 미설정 시**
```
시간 진행 →
T+0s : [🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴] 10개 스레드 모두 블로킹
T+10s: [🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴] 여전히 블로킹
T+30s: [🔴🔴🔴🔴🔴🔴🔴🔴🔴🔴] 강제 종료 필요
요청 11-50: 큐에서 영원히 대기
```

**타임아웃 3초 설정 시**
```
시간 진행 →
T+0s : [🟡🟡🟡🟡🟡🟡🟡🟡🟡🟡] 10개 스레드 작업 중
T+3s : [🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢] 타임아웃 후 해제
T+6s : [🟡🟡🟡🟡🟡🟡🟡🟡🟡🟡] 다음 10개 처리 중
T+9s : [🟢🟢🟢🟢🟢🟢🟢🟢🟢🟢] 모든 요청 완료
```

---

## 🛠️ 트러블슈팅 총정리

### 1. Connect Timeout - 백로그 문제
- **문제**: 기본 백로그 50으로 인해 타임아웃 미발생
- **해결**: 백로그를 1로 설정하여 즉시 거부 유도
- **교훈**: OS 레벨 TCP 동작 이해 필요

### 2. Read Timeout - SLOW_RESPONSE 혼란
- **문제**: 15초 걸려도 타임아웃 없음
- **원인**: Read Timeout은 "다음 데이터까지" 시간
- **교훈**: 전체 응답 시간 제한은 별도 구현 필요

### 3. Write Timeout - 무한 블로킹
- **문제**: 1MB 전송 시 20분+ 블로킹
- **원인**: Java의 Write Timeout 미지원
- **해결**: Future + ExecutorService로 타임아웃 구현
- **교훈**: TCP 버퍼와 흐름 제어 이해 필수

#### 🧪 직접 실험해보기 - 무한 블로킹 체험

**주의사항**
```
⚠️ 경고: 실제로 프로그램이 멈춥니다!
- CPU 사용률 0%로 떨어짐
- 20분 이상 블로킹 가능
- Ctrl+C로만 종료 가능
```

**Step 1: 타임아웃 없는 원본 버전 실행**
```bash
# 무한 블로킹을 경험하고 싶다면
java -cp build/classes/java/main \
  com.experiment.timeout_lab.scenario.WriteTimeoutScenario_noTimeout

# 실행 결과:
# 테스트 1-3: 정상 완료
# 테스트 4: "서버가 일부만 읽고 멈춤, 데이터 크기 = 1 MB" ← 여기서 멈춤!
# 
# [22:51:53] 📤 데이터 전송 시작 (1 MB)
# ... 아무 로그 없이 멈춤 ...
# [Ctrl+C] 강제 종료 필요
```

**Step 2: 블로킹 상태 확인**
```bash
# 다른 터미널에서 TCP 연결 상태 확인
netstat -an | grep 8085

# 결과:
# tcp  0  131072  127.0.0.1:xxxxx  127.0.0.1:8085  ESTABLISHED
#           ^^^^^^ Send-Q에 데이터 쌓임 (버퍼 가득)
```

**Step 3: 수정된 버전으로 해결 확인**
```bash
# Future로 타임아웃 구현한 버전
java -cp build/classes/java/main \
  com.experiment.timeout_lab.scenario.WriteTimeoutScenario

# 결과:
# [22:51:58] 📤 데이터 전송 시작 (1 MB)
# [22:52:03] ⏱️ Write Timeout 발생! (5000ms 초과)
# [22:52:03] 💡 예상된 동작: TCP 버퍼 가득 → Write 블로킹
# 테스트 정상 완료!
```

#### TCP 버퍼 시각화

```
블로킹 발생 과정:

[Client]                [TCP Layer]              [Server]
write(1MB) ──────→ [Send Buffer 64KB] ──→ [Recv Buffer 64KB] ──→ read(10B)
    ↑                      ↓                        ↓                  ↓
    │                  가득 참!                   가득 참!            STOP!
    │                      ↓                        ↓
    └──────────────── 블로킹! ←─────────────────────┘
                    (영원히 대기...)
```

#### 왜 이 실험이 중요한가?

1. **실제 장애 시뮬레이션**: 프로덕션에서 발생 가능한 상황
2. **TCP 이해**: 버퍼와 흐름 제어 메커니즘 체험
3. **타임아웃 필요성**: 왜 Write Timeout이 필수인지 체감
4. **Java 한계 인식**: 기본 API의 부족함 경험

### 4. Thread Exhaustion - 서비스 마비
- **문제**: 타임아웃 없으면 전체 스레드풀 고갈
- **해결**: 모든 네트워크 작업에 타임아웃 설정
- **교훈**: 하나의 설정 누락이 전체 장애 유발

---

## 💡 실무 권장사항

### 1. 필수 타임아웃 체크리스트

```java
// ✅ 올바른 설정
Socket socket = new Socket();
socket.setSoTimeout(3000);                    // Read Timeout
socket.connect(address, 5000);                // Connect Timeout

// Write Timeout (Future 사용)
ExecutorService executor = Executors.newCachedThreadPool();
Future<Void> future = executor.submit(() -> {
    outputStream.write(data);
    return null;
});
future.get(5, TimeUnit.SECONDS);

// ❌ 절대 금지
socket.connect(address);                      // 타임아웃 없음!
outputStream.write(largeData);                // 블로킹 위험!
```

### 2. 환경별 권장 타임아웃

| 환경 | Connect | Read | Write | 근거 |
|------|---------|------|-------|------|
| **로컬** | 1-2초 | 3-5초 | 3초 | 낮은 레이턴시 |
| **동일 DC** | 2-3초 | 5-10초 | 5초 | 안정적 네트워크 |
| **다른 지역** | 5초 | 10-30초 | 10초 | 네트워크 지연 |
| **외부 API** | 5-10초 | 30-60초 | 30초 | 불확실성 |

### 3. 스레드풀 보호 전략

```java
// 1. 적절한 풀 크기
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    10,                                    // core
    50,                                    // max  
    60L, TimeUnit.SECONDS,                // keepAlive
    new ArrayBlockingQueue<>(100),        // queue
    new ThreadPoolExecutor.CallerRunsPolicy()
);

// 2. Circuit Breaker 적용
CircuitBreaker breaker = CircuitBreaker.ofDefaults("backend");

// 3. 타임아웃 필수
// 모든 네트워크 작업에 타임아웃 설정
```

### 4. 모니터링 필수 지표

- ⏱️ **응답 시간**: P50, P95, P99
- 📊 **타임아웃 비율**: 목표 < 1%
- 🧵 **스레드 사용률**: < 80%
- ❌ **Connection Refused**: 즉시 알림
- 🔄 **Circuit Breaker 상태**: Open/Closed

---

## 🎯 핵심 교훈

### 1. 타임아웃은 필수, 선택이 아님
> "타임아웃 미설정 = 잠재적 시스템 마비"

### 2. Java의 한계를 인지하고 우회
> "Write Timeout 없음 → Future로 해결"

### 3. TCP 버퍼 동작 이해
> "write() 완료 ≠ 데이터 전송 완료"

### 4. 작은 실수가 큰 장애로
> "하나의 타임아웃 누락 → 전체 서비스 다운"

---

## 🚀 빠른 시작

### 요구사항
- Java 21+
- Gradle 8.0+

### 실행 방법

```bash
# 1. 클론
git clone https://github.com/yourusername/timeout-lab.git
cd timeout-lab

# 2. 빌드
./gradlew clean build

# 3. 전체 벤치마크 실행
java -cp build/classes/java/main \
  com.experiment.timeout_lab.benchmark.BenchmarkRunner

# 개별 시나리오 실행
java -cp build/classes/java/main \
  com.experiment.timeout_lab.scenario.ConnectTimeoutScenario
```

### 🧪 Write Timeout 실험 파일

프로젝트에는 두 가지 버전의 WriteTimeoutScenario가 포함되어 있습니다:

| 파일명 | 설명 | 용도 |
|--------|------|------|
| `WriteTimeoutScenario_noTimeout.java` | 타임아웃 미구현 (원본) | 무한 블로킹 체험용 ⚠️ |
| `WriteTimeoutScenario.java` | Future로 타임아웃 구현 | 정상 작동 버전 ✅ |

```bash
# 무한 블로킹 체험 (주의!)
java -cp build/classes/java/main \
  com.experiment.timeout_lab.scenario.WriteTimeoutScenario_noTimeout

# 안전한 실행
java -cp build/classes/java/main \
  com.experiment.timeout_lab.scenario.WriteTimeoutScenario
```

### 결과 확인
```
results/
├── benchmarks/    # CSV 데이터
├── reports/       # HTML 리포트
└── logs/          # 상세 로그
```

---

## 📚 참고 자료

### 필독 자료
- [Java Socket Programming Guide](https://docs.oracle.com/javase/tutorial/networking/sockets/)
- [TCP/IP RFC 793](https://www.rfc-editor.org/rfc/rfc793)
- [Resilience4j Documentation](https://resilience4j.readme.io/)

### 추천 도서
- "Release It!" - Michael T. Nygard
- "Designing Data-Intensive Applications" - Martin Kleppmann
- "Systems Performance" - Brendan Gregg

### 관련 프로젝트
- [Netty](https://netty.io/) - 비동기 네트워크 프레임워크
- [Hystrix](https://github.com/Netflix/Hystrix) - Circuit Breaker 구현
- [Apache HttpClient](https://hc.apache.org/) - HTTP 클라이언트

---

## ⚠️ 중요 경고

**프로덕션 환경에서 절대 타임아웃을 미설정하지 마세요!**

이 실험이 증명한 것:
1. 타임아웃 미설정 → 스레드풀 고갈
2. 스레드풀 고갈 → 서비스 응답 불가
3. 서비스 응답 불가 → 연쇄 장애
4. 연쇄 장애 → 전체 시스템 다운

**타임아웃은 시스템의 안전벨트입니다!**