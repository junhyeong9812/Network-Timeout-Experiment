package com.experiment.timeout_lab.client;

import com.experiment.timeout_lab.util.Constants;
import com.experiment.timeout_lab.util.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
import java.time.Instant;

/**
 * 타임아웃 설정이 가능한 TCP 클라이언트
 *
 * Connect, Read, Write 타임아웃을 각각 설정할 수 있으며,
 * 타임아웃 발생 시 상세한 정보를 제공합니다.
 * */
public class TimeoutClient {

    private static final Logger logger = new Logger(TimeoutClient.class);

    private final String host;
    private final int port;
    private int connectTimeout;
    private int readTimeout;
    private int writeTimeout;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    // 측정 결과
    private long connectTime = -1;
    private long readTime = -1;
    private long writeTime = -1;
    private Exception lastException;

    public TimeoutClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.connectTimeout = Constants.DEFAULT_CONNECT_TIMEOUT;
        this.readTimeout = Constants.DEFAULT_READ_TIMEOUT;
        this.writeTimeout = Constants.DEFAULT_WRITE_TIMEOUT;
    }

    public TimeoutClient(String host) {
        this(host, Constants.DEFAULT_SERVER_PORT);
    }

    /**
     * 서버에 연결 (Connect Timeout 적용)
     * */
    public boolean connect() {
        try {
            logger.info(String.format("서버 연결 시도 - %s:%d (Connect Timeout: %dms)",
                    host, port, connectTimeout));

            socket = new Socket();
            socket.setReuseAddress(true);

            // Connect Timeout 설정
            InetSocketAddress address = new InetSocketAddress(host, port);
            Instant startTime = Instant.now();
            // Instant란 표준 세계시 기준으로 시간값을 나타낸다.
            // 어디서 접근해도 같은 순간을 표현하기 위함

            try {
                socket.connect(address, connectTimeout);
                connectTime = Duration.between(startTime, Instant.now()).toMillis();

                // Read Timeout 설정
                socket.setSoTimeout(readTimeout);

                // 스트림 초기화
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                writer = new PrintWriter(socket.getOutputStream(), true);

                return true;

            } catch (SocketException e) {
                connectTime = Duration.between(startTime, Instant.now()).toMillis();
                logger.error("❌ Connect Timeout 발생! (대기시간: " + connectTime + "ms)");
                lastException = e;
                return false;
            }

        } catch (Exception e) {
            logger.error("연결 실패", e);
            lastException = e;
            return false;
        }
    }

    /**
     * 데이터 전송 (Write Timeout 시뮬레이션)
     *
     * 참고: Java Socket API는 직접적인 Write Timeout을 지원하지 않음
     * 실제로는 OS 레벨의 TCP 버퍼가 가득 찰 때만 블로킹됨
     * */
    public boolean sendData(String data) {
        if (socket == null || !socket.isConnected()) {
            logger.error("연결되지 않은 상태에서 전송 시도");
            return false;
        }

        try {
            logger.info("데이터 전송 중... (크기: " + data.length() + " bytes)");
            Instant startTime = Instant.now();

            // 대용량 데이터 전송 시뮬레이션
            if (data.equals("LARGE_DATA")) {
                // 1MB 데이터 생성
                StringBuilder largeData = new StringBuilder();
                for (int i = 0; i < Constants.LARGE_BUFFER_SIZE; i ++) {
                    largeData.append("X");
                }
                data = largeData.toString();
                logger.info("대용량 데이터 전송 (1MB)");
            }

            writer.println(data);
            writer.flush();

            writeTime = Duration.between(startTime, Instant.now()).toMillis();
            logger.info("전송 완료 (소요시간: " + writeTime + "ms)");
            return true;

        } catch (Exception e) {
            logger.error("데이터 전송 실패", e);
            lastException = e;
            return false;
        }
    }

    /**
     * 데이터 수신 (Read Timeout 적용)
     * */
    public String receiveData() {
        if (socket == null || !socket.isConnected()) {
            logger.error("연결되지 않은 상태에서 수신 시도");
            return null;
        }

        try {
            logger.info("데이터 수신 대기... (Read Timeout: " + readTimeout + "ms)");
            Instant startTime = Instant.now();

            try {
                String response = reader.readLine();
                readTime = Duration.between(startTime, Instant.now()).toMillis();

                if (response != null) {
                    logger.info("수신 완료 (소요시간: " + readTime + "ms): " + response);
                } else {
                    logger.warn("연결이 종료됨 (EOF)");
                }

                return response;

            } catch (SocketException e) {
                readTime = Duration.between(startTime, Instant.now()).toMillis();
                logger.error("Read Timeout 발생! (대기시간: " + readTime + "ms)");
                lastException = e;
                return null;
            }

        } catch (Exception e) {
            logger.error("데이터 수신 실패", e);
            lastException = e;
            return null;
        }
    }

    /**
     * 에코 테스트 (전송 후 수신)
     * */
    public boolean echoTest(String message) {
        logger.info("=== Echo Test 시작 ===");

        if (!sendData(message)) {
            return false;
        }

        String response = receiveData();
        if (response != null) {
            logger.info("Echo 응답: " + response);
            return true;
        }

        return false;
    }

    /**
     * 연결 종료
     * */
    public void disconnect() {
        logger.info("연결 종료 중...");

        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            logger.info("연결 종료 완료");
        } catch (IOException e) {
            logger.error("연결 종료 중 오류", e);
        }
    }

    /**
     * 타임아웃 테스트 결과 출력
     */
    public void printResults() {
        logger.separator();
        System.out.println("📊 타임아웃 테스트 결과:");
        System.out.println("├─ Host: " + host + ":" + port);
        System.out.println("├─ Connect Timeout 설정: " + connectTimeout + "ms");
        System.out.println("├─ Read Timeout 설정: " + readTimeout + "ms");
        System.out.println("├─ Write Timeout 설정: " + writeTimeout + "ms");
        System.out.println("│");
        System.out.println("├─ 실제 Connect 시간: " +
                (connectTime >= 0 ? connectTime + "ms" : "N/A"));
        System.out.println("├─ 실제 Read 시간: " +
                (readTime >= 0 ? readTime + "ms" : "N/A"));
        System.out.println("├─ 실제 Write 시간: " +
                (writeTime >= 0 ? writeTime + "ms" : "N/A"));

        if (lastException != null) {
            System.out.println("│");
            System.out.println("└─ 마지막 예외: " + lastException.getClass().getSimpleName()
                    + " - " + lastException.getMessage());
        }
        logger.separator();
    }

    // Getter & Setter
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
        // 이미 연결된 경우 즉시 적용
        if (socket != null && socket.isConnected()) {
            try {
                socket.setSoTimeout(readTimeout);
            } catch (SocketException e) {
                logger.error("Read Timeout 설정 실패", e);
            }
        }
    }

    public void setWriteTimeout(int writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    public void setAllTimeouts(int timeout) {
        this.connectTimeout = timeout;
        this.readTimeout = timeout;
        this.writeTimeout = timeout;

        if (socket != null && socket.isConnected()) {
            try {
                socket.setSoTimeout(timeout);
            } catch (SocketException e) {
                logger.error("Timeout 설정 실패", e);
            }
        }
    }

    public long getConnectTime() {
        return connectTime;
    }

    public long getReadTime() {
        return readTime;
    }

    public long getWriteTime() {
        return writeTime;
    }

    public Exception getLastException() {
        return lastException;
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * 단독 테스트용 main 메서드
     * */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java TimeoutClient <host> [port] [connectTimeout] [readTimeout]");
            System.exit(1);
        }

        String host = args[0];
        int port = args.length > 1 ? Integer.parseInt(args[1]) : Constants.DEFAULT_SERVER_PORT;

        TimeoutClient client = new TimeoutClient(host, port);

        if (args.length > 2) {
            client.setConnectTimeout(Integer.parseInt(args[2]));
        }
        if (args.length > 2) {
            client.setReadTimeout(Integer.parseInt(args[3]));
        }

        try {
            // 연결 테스트
            if (client.connect()) {
                // 에코 테스트
                client.echoTest("Hello, Server!");
            }

            // 결과 출력
            client.printResults();

        } finally {
            client.disconnect();
        }
    }
}
