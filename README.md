# 🕐 Network Timeout Experiment

## 📋 프로젝트 개요

### 목적
TCP/IP 네트워크 통신에서 발생하는 다양한 타임아웃 상황을 순수 Java Socket Programming으로 직접 구현하고 실험함으로써, 분산 시스템에서의 타임아웃 설정의 중요성과 영향을 체험적으로 학습하는 프로젝트입니다.

### 핵심 목표
1. **Connect, Read, Write Timeout의 차이점을 명확히 이해**
2. **타임아웃 미설정 시 발생하는 스레드풀 고갈 현상 재현**
3. **적절한 타임아웃 값 설정의 중요성 입증**
4. **실제 장애 상황 시뮬레이션 및 대응 방안 학습**

### 기술 스택
- **언어**: Java 21 (Virtual Threads 활용 가능)
- **네트워크**: Pure Java Socket Programming (java.net.*)
- **동시성**: java.util.concurrent (ExecutorService, ThreadPool)
- **빌드**: Gradle 8.5
- **의존성**: 없음 (순수 Java 표준 라이브러리만 사용)

---

## 🎯 학습 목표

### 1차 목표 (필수)
- [ ] TCP 3-way handshake 과정에서 Connect Timeout 이해
- [ ] 소켓 통신에서 Read/Write Timeout 구현
- [ ] 스레드풀 고갈 상황 재현 및 모니터링
- [ ] 타임아웃 값에 따른 성능 벤치마킹

### 2차 목표 (선택)
- [ ] Circuit Breaker 패턴 간단 구현
- [ ] Retry 메커니즘 적용
- [ ] Connection Pool 효과 측정

---

## 🎓 Computer Science 핵심 개념

### 1. OSI 7계층과 TCP/IP 스택
```
Application Layer    [Java Application]
     ↕                    ↕
Transport Layer      [TCP Socket]
     ↕                    ↕
Network Layer        [IP Protocol]
     ↕                    ↕
Data Link Layer      [Ethernet]
```

이 프로젝트는 **Transport Layer (전송 계층)**에서 일어나는 타임아웃을 다룹니다.

### 2. TCP 연결 과정 (3-Way Handshake)
```
Client                          Server
  |                               |
  |-------SYN (seq=x)-----------> |  1️⃣ SYN: 연결 요청
  |                               |
  |<---SYN-ACK (seq=y, ack=x+1)-- |  2️⃣ SYN-ACK: 연결 수락
  |                               |
  |-------ACK (ack=y+1)---------> |  3️⃣ ACK: 연결 확립
  |                               |
  |<====== Connection Established ======>
```
**Connect Timeout**은 이 3-way handshake 과정이 완료되기를 기다리는 시간입니다.

### 3. TCP 상태 다이어그램
```
CLOSED → SYN_SENT → ESTABLISHED → FIN_WAIT → TIME_WAIT → CLOSED
         ↑                ↓
         └── SYN_RCVD ←───┘
```

### 4. 소켓 버퍼와 Flow Control
```
Application Write → [Send Buffer] → Network → [Receive Buffer] → Application Read
                         ↓                           ↑
                    Write Timeout               Read Timeout
```

### 5. 블로킹 I/O vs 논블로킹 I/O
```java
// Blocking I/O (이 프로젝트에서 다루는 방식)
socket.getInputStream().read();  // 데이터가 올 때까지 스레드 블록

// Non-blocking I/O
SocketChannel channel = SocketChannel.open();
channel.configureBlocking(false);  // 즉시 리턴
```

### 6. 스레드 라이프사이클
```
NEW → RUNNABLE → BLOCKED → WAITING → TIMED_WAITING → TERMINATED
                    ↑          ↑           ↑
                    └──────────┴───────────┘
                      (타임아웃 발생 시)
```

### 7. 스레드풀과 자원 고갈
```
Thread Pool (size=10)
┌─────────────────────────────────────┐
│ T1: Blocked on connect() - ∞ wait   │
│ T2: Blocked on connect() - ∞ wait   │
│ T3: Blocked on connect() - ∞ wait   │
│ ...                                  │
│ T10: Blocked on connect() - ∞ wait  │
└─────────────────────────────────────┘
↓
11번째 요청: ❌ 처리 불가 (모든 스레드 블록)
```

### 8. 동시성 제어 메커니즘
- **Mutex**: 상호 배제
- **Semaphore**: 카운팅 세마포어
- **Monitor**: Java의 synchronized
- **Thread Pool**: 스레드 재사용

### 9. 네트워크 프로그래밍 핵심 시스템 콜
```c
// Java Socket 내부에서 사용되는 시스템 콜
socket()     // 소켓 생성
bind()       // 주소 바인딩
listen()     // 연결 대기
accept()     // 연결 수락
connect()    // 연결 시도
send()/recv() // 데이터 송수신
close()      // 연결 종료
```

### 10. 타임아웃 관련 소켓 옵션
```java
// SO_TIMEOUT - Read Timeout
socket.setSoTimeout(5000);

// Connect Timeout
socket.connect(address, timeout);

// SO_LINGER - Close 시 대기 시간
socket.setSoLinger(true, 5);

// TCP_NODELAY - Nagle 알고리즘 비활성화
socket.setTcpNoDelay(true);

// SO_KEEPALIVE - 연결 유지 확인
socket.setKeepAlive(true);
```

---

## 📚 핵심 개념

### 1. Timeout의 종류와 의미

#### 1.1 Connect Timeout
```
[Client] ----SYN----> [Server]
         <---SYN/ACK-- (이 응답을 기다리는 시간)
         ----ACK----->
```
- **정의**: TCP 연결 확립(3-way handshake)을 기다리는 최대 시간
- **발생 시점**: Socket.connect() 호출 시
- **실패 원인**:
    - 서버 다운
    - 네트워크 단절
    - 방화벽 차단
    - 잘못된 IP/Port

#### 1.2 Read Timeout
```
[Client] ----Request----> [Server]
         <---Response---- (이 응답 데이터를 기다리는 시간)
```
- **정의**: 연결된 소켓에서 데이터를 읽기 위해 대기하는 최대 시간
- **발생 시점**: InputStream.read() 호출 시
- **실패 원인**:
    - 서버 처리 지연
    - 네트워크 지연
    - 서버 무응답

#### 1.3 Write Timeout
```
[Client] ----Data----> [Buffer Full] [Server]
         (전송 완료를 기다리는 시간)
```
- **정의**: 데이터를 상대방에게 전송 완료하기를 기다리는 최대 시간
- **발생 시점**: OutputStream.write() 호출 시
- **실패 원인**:
    - 수신측 버퍼 풀
    - 네트워크 혼잡
    - 수신측 처리 지연

### 2. 타임아웃이 없을 때의 문제점

#### 2.1 스레드풀 고갈 (Thread Pool Exhaustion)
```java
// 타임아웃 미설정 시 시나리오
Thread-1: connect() → 무한 대기 (서버 응답 없음)
Thread-2: connect() → 무한 대기
Thread-3: connect() → 무한 대기
...
Thread-10: connect() → 무한 대기
→ 모든 스레드 블로킹 → 신규 요청 처리 불가 → 서비스 장애
```

#### 2.2 연쇄 장애 (Cascading Failure)
```
[Service A] --timeout:∞--> [Service B (Down)]
     ↓ (모든 스레드 대기)
[Service A Down]
     ↓
[전체 시스템 장애]
```

### 3. 적절한 타임아웃 설정 가이드라인

| 구분 | 권장 시간 | 고려 사항 |
|------|----------|-----------|
| Connect Timeout | 1-5초 | 네트워크 환경에 따라 조정 |
| Read Timeout | 5-30초 | 서버 처리 시간 고려 |
| Write Timeout | 5-10초 | 데이터 크기 고려 |

---

## 🏗️ 프로젝트 구조

```
timeout-lab/
│
├── build.gradle                         # Gradle 빌드 설정
├── settings.gradle                      # 프로젝트 설정
├── README.md                           # 프로젝트 문서
│
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/
│   │           └── experiment/
│   │               └── timeout_lab/
│   │                   ├── TimeoutLabApplication.java   # 메인 실행 클래스
│   │                   │
│   │                   ├── server/                      # 서버 구현체
│   │                   │   ├── SimpleServer.java        # 정상 동작 서버
│   │                   │   ├── ProblematicServer.java   # 문제 상황 시뮬레이션
│   │                   │   └── ServerConfig.java        # 서버 설정
│   │                   │
│   │                   ├── client/                      # 클라이언트 구현체
│   │                   │   ├── TimeoutClient.java       # 타임아웃 설정 가능 클라이언트
│   │                   │   ├── ClientConfig.java        # 클라이언트 설정
│   │                   │   └── ClientPool.java          # 멀티스레드 클라이언트 풀
│   │                   │
│   │                   ├── scenario/                    # 실험 시나리오
│   │                   │   ├── BaseScenario.java        # 시나리오 기본 클래스
│   │                   │   ├── ConnectTimeoutScenario.java
│   │                   │   ├── ReadTimeoutScenario.java
│   │                   │   ├── WriteTimeoutScenario.java
│   │                   │   └── ThreadExhaustionScenario.java
│   │                   │
│   │                   ├── benchmark/                   # 성능 측정
│   │                   │   ├── BenchmarkRunner.java     # 벤치마크 실행기
│   │                   │   ├── Metrics.java             # 측정 지표
│   │                   │   └── Report.java              # 결과 리포트
│   │                   │
│   │                   ├── monitor/                     # 모니터링
│   │                   │   ├── ThreadMonitor.java       # 스레드 상태 모니터
│   │                   │   ├── ResourceMonitor.java     # 자원 사용량 모니터
│   │                   │   └── MetricsCollector.java    # 지표 수집기
│   │                   │
│   │                   └── util/                        # 유틸리티
│   │                       ├── Logger.java              # 간단한 로거
│   │                       ├── NetworkUtil.java         # 네트워크 유틸
│   │                       └── Constants.java           # 상수 정의
│   │
│   └── test/                                           # 테스트 코드
│       └── java/
│           └── com/experiment/timeout_lab/
│               ├── ServerTest.java
│               ├── ClientTest.java
│               └── ScenarioTest.java
│
├── logs/                                               # 실행 로그
│   ├── connect-timeout.log
│   ├── read-timeout.log
│   ├── write-timeout.log
│   └── thread-exhaustion.log
│
├── results/                                            # 실험 결과
│   ├── benchmarks/
│   │   ├── connect-timeout-results.csv
│   │   ├── read-timeout-results.csv
│   │   └── write-timeout-results.csv
│   └── reports/
│       └── summary-report.md
│
└── scripts/                                            # 실행 스크립트
    ├── run-server.sh                                  # 서버 실행
    ├── run-client.sh                                  # 클라이언트 실행
    ├── run-scenario.sh                                # 시나리오 실행
    └── run-all.sh                                     # 전체 실행
```

### 패키지 구조 설명

| 패키지 | 설명 | 주요 클래스 |
|--------|------|-------------|
| `com.experiment.timeout_lab` | 메인 패키지 | TimeoutLabApplication |
| `com.experiment.timeout_lab.server` | 다양한 서버 구현체 | SimpleServer, ProblematicServer |
| `com.experiment.timeout_lab.client` | 클라이언트 구현체 | TimeoutClient, ClientPool |
| `com.experiment.timeout_lab.scenario` | 실험 시나리오 | Connect/Read/Write Timeout 시나리오 |
| `com.experiment.timeout_lab.benchmark` | 성능 측정 도구 | BenchmarkRunner, Metrics |
| `com.experiment.timeout_lab.monitor` | 시스템 모니터링 | ThreadMonitor, ResourceMonitor |
| `com.experiment.timeout_lab.util` | 공통 유틸리티 | Logger, NetworkUtil, Constants |

---

## 🚀 구현 계획

### Phase 1: 기본 서버/클라이언트 구현 (2시간)
- [x] 기본 소켓 서버 구현
- [x] 타임아웃 설정 가능한 클라이언트 구현
- [x] 서버-클라이언트 통신 테스트

### Phase 2: 타임아웃 시나리오 구현 (3시간)

#### Connect Timeout 시나리오
1. **서버 미시작**: 아무도 듣지 않는 포트로 연결 시도
2. **Accept 미수행**: 서버는 열려있지만 accept()를 하지 않음
3. **방화벽 시뮬레이션**: 패킷 드롭 상황 재현

#### Read Timeout 시나리오
1. **완전 무응답**: 연결 후 서버가 아무 데이터도 보내지 않음
2. **부분 응답**: 헤더만 보내고 바디는 보내지 않음
3. **매우 느린 응답**: 1byte/10sec 속도로 응답

#### Write Timeout 시나리오
1. **수신 거부**: 서버가 read()를 하지 않음
2. **느린 읽기**: 서버가 매우 천천히 읽음
3. **버퍼 오버플로우**: 대용량 데이터 전송

### Phase 3: 스레드풀 고갈 실험 (2시간)
```java
// 실험 시나리오
1. 스레드풀 크기: 10
2. 동시 요청: 100개
3. 타임아웃 설정: 없음 vs 3초 vs 10초
4. 측정: 처리량, 응답시간, 스레드 상태
```

### Phase 4: 벤치마킹 및 분석 (2시간)

#### 측정 지표
- **응답 시간**: 평균, 최소, 최대, 99 percentile
- **처리량**: TPS (Transaction Per Second)
- **자원 사용량**: CPU, Memory, Thread 수
- **실패율**: Timeout 발생 비율

#### 벤치마킹 매트릭스
| Timeout 설정 | 1초 | 3초 | 5초 | 10초 | 30초 | 무한대 |
|-------------|-----|-----|-----|------|------|---------|
| Connect     | ⚡ | ⚡ | ⚡ | ⚡ | ⚡ | ⚡ |
| Read        | ⚡ | ⚡ | ⚡ | ⚡ | ⚡ | ⚡ |
| Write       | ⚡ | ⚡ | ⚡ | ⚡ | ⚡ | ⚡ |

### Phase 5: 문서화 (1시간)
- 실험 결과 정리
- 그래프 생성
- 권장 사항 도출

---

## 🧪 실험 시나리오

### 시나리오 1: Connect Timeout 실험
```bash
# 서버 시작 (Accept 안 함)
java -cp . server.ProblematicServer --port 8080 --scenario NO_ACCEPT

# 클라이언트 테스트
java -cp . client.TimeoutClient --host localhost --port 8080 --connect-timeout 3000
```

### 시나리오 2: Read Timeout 실험
```bash
# 서버 시작 (응답 지연)
java -cp . server.ProblematicServer --port 8080 --scenario SLOW_RESPONSE

# 클라이언트 테스트
java -cp . client.TimeoutClient --host localhost --port 8080 --read-timeout 5000
```

### 시나리오 3: 스레드풀 고갈 실험
```bash
# 서버 시작 (무응답)
java -cp . server.ProblematicServer --port 8080 --scenario NO_RESPONSE

# 멀티스레드 클라이언트 (타임아웃 없음)
java -cp . benchmark.ThreadExhaustionTest --threads 10 --requests 100 --timeout 0

# 멀티스레드 클라이언트 (타임아웃 3초)
java -cp . benchmark.ThreadExhaustionTest --threads 10 --requests 100 --timeout 3000
```

---

## 📊 예상 실험 결과

### 1. Connect Timeout 효과
```
타임아웃 없음: 평균 대기시간 75초 (OS 기본값)
타임아웃 3초: 평균 대기시간 3초
→ 72초 단축, 빠른 실패 처리 가능
```

### 2. 스레드풀 고갈 비교
```
타임아웃 없음:
- 10개 스레드 모두 블로킹
- 11번째 요청부터 처리 불가
- 서비스 완전 정지

타임아웃 3초:
- 3초 후 스레드 반환
- 계속해서 새 요청 처리 가능
- 서비스 가용성 유지
```

### 3. 처리량 비교
```
시나리오: 서버 50% 확률로 5초 지연 응답
- 타임아웃 없음: 12 TPS
- 타임아웃 3초: 200 TPS (실패 포함)
- 타임아웃 10초: 100 TPS
```

---

## 🔧 실행 방법

### 1. 프로젝트 클론
```bash
git clone https://github.com/junhyeong9812/timeout-experiment.git
cd timeout-experiment
```

### 2. 컴파일
```bash
javac -d out src/main/java/**/*.java
```

### 3. 실행
```bash
# 전체 시나리오 실행
./scripts/run-all-scenarios.sh

# 개별 시나리오 실행
java -cp out Main --scenario connect-timeout
java -cp out Main --scenario read-timeout
java -cp out Main --scenario write-timeout
java -cp out Main --scenario thread-exhaustion
```

### 4. 결과 확인
```bash
cat docs/experiment-results.md
```

---

## 📈 학습 성과

이 프로젝트를 완료하면 다음을 이해하게 됩니다:

1. **네트워크 타임아웃의 실제 동작 원리**
2. **타임아웃 미설정 시 발생하는 실제 문제들**
3. **적절한 타임아웃 값 설정 기준**
4. **마이크로서비스 환경에서의 장애 전파 메커니즘**
5. **Circuit Breaker 패턴의 필요성**

---

## 🔍 추가 탐구 주제

- **Circuit Breaker Pattern**: 장애 차단 메커니즘
- **Retry with Backoff**: 지능적 재시도 전략
- **Connection Pooling**: 연결 재사용을 통한 성능 향상
- **Load Balancing**: 부하 분산과 타임아웃의 관계
- **Distributed Tracing**: 분산 환경에서의 타임아웃 추적

---

## 📚 참고 자료

- [TCP/IP Illustrated](https://www.amazon.com/TCP-Illustrated-Vol-Addison-Wesley-Professional/dp/0201633469)
- [Java Network Programming](https://www.oreilly.com/library/view/java-network-programming/9781449365936/)
- [Resilience4j Documentation](https://resilience4j.readme.io/)
- [Netflix Hystrix (archived)](https://github.com/Netflix/Hystrix)

---