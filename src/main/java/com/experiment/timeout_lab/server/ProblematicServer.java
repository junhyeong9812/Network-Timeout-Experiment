package com.experiment.timeout_lab.server;

import com.experiment.timeout_lab.util.Constants;
import com.experiment.timeout_lab.util.Logger;
import com.experiment.timeout_lab.util.Constants.ServerMode;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 다양한 타임아웃 상황을 시뮬레이션하는 문제적 서버
 *
 * 각 모드별로 다른 문제 상황을 재현:
 * - NO_ACCEPT: accept()를 하지 않아 Connect Timeout 유발
 * - NO_RESPONSE: 연결 후 응답하지 않아 Read Timeout 유발
 * - SLOW_RESPONSE: 매우 천천히 응답
 * - SLOW_READ: 클라이언트 데이터를 매우 천천히 읽어 Write Timeout 유발
 * - PARTIAL_READ: 데이터를 일부만 읽고 멈춤
 * 
 * 이때 HTTP가 아닌 TCP Socket을 사용하는 이유는 타임아웃 문제는 HTTP보다 더 아래 레벨(TCP)에서 발생
 * HTTP 클라이언트(WebClient, RestTemplate, Feign)는 결국 내부에서 Socket 사용
 * 모드	서버가 하는 짓	클라이언트 입장에서 무슨 일?
 * NO_ACCEPT	서버는 문 안 열어줌	클라이언트는 문 앞에서 계속 기다리다 Connect Timeout
 * NO_RESPONSE	문은 열지만 말 안 함	Read Timeout
 * SLOW_RESPONSE	아주 천천히 말함	Read Timeout 느리게 → 클라이언트 응답대기
 * SLOW_READ	서버가 입력을 느리게 읽음	클라이언트는 전송 버퍼 꽉 차서 Write Timeout
 * PARTIAL_READ	조금만 듣고 멈춤	클라는 요청 보냈는데 처리가 안 됨
 * NORMAL	정상 통신	문제 없음
 * */
public class ProblematicServer {

    private static final Logger logger = new Logger(ProblematicServer.class);

    private final int port;
    private final ServerMode mode;
    private ServerSocket serverSocket;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread serverThread;

    public ProblematicServer(int port, ServerMode mode) {
        this.port = port;
        this.mode = mode;
    }

    public ProblematicServer(ServerMode mode) {
        this(Constants.DEFAULT_SERVER_PORT, mode);
    }

    /**
     * 서버 시작
     * */
    public void start() throws IOException {
        if (running.get()) {
            logger.warn("서버가 이미 실행 중입니다.");
            return;
        }

        serverSocket = new ServerSocket(port, Constants.SERVER_BACKLOG);
        serverSocket.setReuseAddress(true);
        running.set(true);
        logger.info("ProblematicServer 시작 - Port: " + port + ", Mode:" + mode);
        logger.info("동작 설명: " + mode.getDescription());

        serverThread = new Thread(this::runServer, "ProblematicServer-" + mode);
        serverThread.start();
    }

    /**
     * 서버 메인 루프
     * */
    private void runServer() {
        try {
            switch (mode) {
                case NO_ACCEPT -> runNoAcceptMode();
                case NO_RESPONSE -> runNoResponseMode();
                case SLOW_RESPONSE -> runSlowResponseMode();
                case SLOW_READ -> runSlowReadMode();
                case PARTIAL_READ -> runPartialReadMode();
                case NORMAL -> runNormalMode();
            }
        } catch (Exception e) {
            if (running.get()) {
                logger.error("서버 실행 중 오류", e);
            }
        }
    }
    
    /**
     * NO_ACCEPT 모드: accept()를 하지 않음
     * Connect Timeout을 유발하기 위한 시나리오
     * */
    private void runNoAcceptMode() throws Exception {
        logger.info("[NO_ACCEPT] 서버 소켓은 열려있지만 accept()하지 않음");
        logger.info("클라이언트는 Connect Timeout이 발생할 때까지 대기하게 됩니다.");

        // 서버는 실행 중이지만 accept()를 하지 않음
        while (running.get()) {
            Thread.sleep(1000);
        }
    }
    
    /**
     * NO_RESPONSE 모드: 연결은 받지만 응답하지 않음
     * Read Timeout을 유발하기 위한 시나리오
     * */
    private void runNoResponseMode() throws Exception {
        logger.info("[NO_RESPONSE] 연결은 수락하지만 데이터를 보내지 않음");

        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("클라이언트 연결됨: " + clientSocket.getRemoteSocketAddress());
                logger.info("응답하지 않고 연결만 유지 (Read Timeout 유발)");

                // 연결은 유지하지만 아무것도 하지 않음
                new Thread(() -> {
                    try {
                        while (!clientSocket.isClosed() && running.get()) {
                            Thread.sleep(1000);
                        }
                    } catch (Exception e) {
                        // 연결이 끊어지면 종료
                    }
                }).start();
            } catch (SocketException e) {
                // 서버 소켓이 닫히면 종료
                break;
            }
        }
    }

    /**
     * SLOW_RESPONSE 모드: 매우 천천히 응답
     * */
    private void runSlowResponseMode() throws Exception{
        logger.info("[SLOW_RESPONSE] 1바이트씩 천천히 응답 (1byte/sec)");

        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("클라이언트 연결됨: " + clientSocket.getRemoteSocketAddress());

                new Thread(() -> handleSlowResponse(clientSocket)).start();
            } catch (SocketException e) {
                break;
            }
        }
    }

    /**
     * SLOW_RESPONSE 모드 핸들링 처리
     * */
    private void handleSlowResponse(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream())){

            // 요청 읽기
            String request = in.readLine();
            logger.info("요청 발음: " + request);

            // 매우 천천히 응답 (1글자씩)
            String response = "HTTP/1.1 200 OK\r\nContent-Length: 13\r\n\r\nHello, World!";
            for (char c : response.toCharArray()) {
                out.print(c);
                out.flush();
                Thread.sleep(1000); //1초에 1글자
                logger.debug("전송: " + c);
            }
            
        } catch (Exception e) {
            logger.error("느린 응답 처리 중 오류", e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // ignore
            }
        }

    }

    /**
     * SLOW_READ 모드: 클라이언트 데이터를 매우 천천히 읽음
     * Write Timeout을 유발하기 위한 시나리오
     * */
    private void runSlowReadMode() throws Exception {
        logger.info("[SLOW_READ] 클라이언트 데이터를 1byte/10sec 속도로 읽음");

        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("클라이언트 연결됨: " + clientSocket.getRemoteSocketAddress());

                new Thread(() -> handleSlowRead(clientSocket)).start();
            } catch (SocketException e) {
                break;
            }
        }
    }

    /**
     * SLOW_READ 모드 핸들링 처리
     * */
    private void handleSlowRead(Socket clientSocket) {
        try ( InputStream in = clientSocket.getInputStream();
              PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            logger.info("매우 천천히 데이터 읽기 시작 (Write Timeout 유발)");

            int bytesRead = 0;
            int data;
            while ((data = in.read()) != -1) {
                bytesRead++;
                logger.debug("읽은 바이트: " + bytesRead);
                Thread.sleep(10000); // 10초에 1바이트

                if (!running.get()) break;
            }

            out.println("OK - Read " + bytesRead + " bytes");
        } catch (Exception e) {
            logger.error("느린 읽기 처리 중 오류", e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    /**
     * PARTIAL_READ 모드: 일부만 읽고 멈춤
     * */
    private void runPartialReadMode() throws Exception {
        logger.info("[PARTIAL_READ] 데이터를 10바이트만 읽고 멈춤");

        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("클라이언트 연결됨: " + clientSocket.getRemoteSocketAddress());

                new Thread(() -> handlePartialRead(clientSocket)).start();
            } catch (SocketException e) {
                break;
            }
        }
    }

    /**
     * PARTIAL_READ 모드 핸들링 처리
     * */
    private void handlePartialRead(Socket clientSocket) {
        try (InputStream in = clientSocket.getInputStream()) {

            byte[] buffer = new byte[10];
            int bytesRead = in.read();
            logger.info("10바이트만 읽음: " + bytesRead + " bytes");

            // 이후 아무것도 하지 않고 연결만 유지
            while (running.get() && !clientSocket.isClosed()) {
                Thread.sleep(1000);
            }

        } catch (Exception e) {
            logger.error("부분 읽기 처리 중 오류", e);
        } finally {
            try {
              clientSocket.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    /**
     * NORMAL 모드: 정상 동작
     * */
    private void runNormalMode() throws Exception {
        logger.info("[NORMAL] 정상 에코 서버로 동작");

        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("클라이언트 연결됨: " + clientSocket.getRemoteSocketAddress());

                new Thread(() -> handleNormalEcho(clientSocket)).start();
            } catch (SocketException e) {
                break;
            }
        }
    }

    /**
     * 노말 모드 핸들링 처리
     * */
    private void handleNormalEcho(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

        } catch (Exception e) {
            logger.error("에코 처리 중 오류", e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    /**
     * 서버 종료
     * */
    public void stop() {
        if (!running.get()) {
            return;
        }

        logger.info("서버 종료 중...");
        running.set(false);

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("서버 소켓 닫기 실패", e);
        }

        if (serverThread != null) {
            try {
                serverThread.interrupt();
                serverThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        logger.info("서버 종료 완료");
    }
    
    public boolean isRunning() {
        return running.get();
    }
    
    public int getPort() {
        return port;
    }
    
    public ServerMode getMode() {
        return mode;
    }
    
    /**
     * 단독 실행을 위한 main 메서드
     * */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java ProblematicServer <mode> [port]");
            System.out.println("Modes: " + java.util.Arrays.toString(ServerMode.values()));
            System.exit(1);
        }

        try {
            ServerMode mode = ServerMode.valueOf(args[0].toUpperCase());
            int port = args.length > 1 ? Integer.parseInt(args[1]) : Constants.DEFAULT_SERVER_PORT;

            ProblematicServer server = new ProblematicServer(port, mode);
            server.start();

            // Shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

            // 서버 실행 유지
            Thread.currentThread().join();

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
