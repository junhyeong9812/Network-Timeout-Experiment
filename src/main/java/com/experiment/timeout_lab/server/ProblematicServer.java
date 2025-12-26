package com.experiment.timeout_lab.server;

import com.experiment.timeout_lab.util.Constants.ServerMode;
import com.experiment.timeout_lab.util.Logger;

import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ProblematicServer - 타임아웃 실험용 문제 서버
 *
 * 다양한 네트워크 문제 상황을 시뮬레이션합니다.
 * 백로그 문제 수정 버전
 */
public class ProblematicServer {
  private static final Logger logger = new Logger(ProblematicServer.class);

  private final int port;
  private final ServerMode mode;
  private ServerSocket serverSocket;
  private Thread serverThread;
  private final AtomicBoolean running = new AtomicBoolean(false);

  public ProblematicServer(int port, ServerMode mode) {
    this.port = port;
    this.mode = mode;
  }

  /**
   * 서버 시작
   */
  public void start() throws IOException {
    if (running.compareAndSet(false, true)) {
      // ===== 핵심 수정: 백로그 설정 =====
      if (mode == ServerMode.NO_ACCEPT) {
        // NO_ACCEPT 모드: 백로그를 1로 설정하여 OS 레벨 큐를 최소화
        // 이렇게 하면 1개 이상의 연결은 거부됨
        serverSocket = new ServerSocket(port, 1);  // 백로그 = 1

        // 추가로 SO_REUSEADDR 비활성화
        serverSocket.setReuseAddress(false);
      } else {
        // 다른 모드: 기본 백로그 사용
        serverSocket = new ServerSocket(port);
      }

      logger.info("ProblematicServer 시작 - Port: " + port + ", Mode: " + mode);
      logger.info("동작 설명: " + getModeDescription());

      serverThread = new Thread(() -> {
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
      }, "ProblematicServer-" + mode);

      serverThread.start();
    }
  }

  /**
   * 서버 종료
   */
  public void stop() {
    if (running.compareAndSet(true, false)) {
      logger.info("서버 종료 중...");
      try {
        if (serverSocket != null && !serverSocket.isClosed()) {
          serverSocket.close();
        }
        if (serverThread != null) {
          serverThread.interrupt();
          serverThread.join(1000);
        }
        logger.info("서버 종료 완료");
      } catch (Exception e) {
        logger.error("서버 종료 중 오류", e);
      }
    }
  }

  /**
   * NO_ACCEPT 모드 - 진짜로 accept()를 하지 않음
   * 백로그가 1이므로 2번째 연결부터는 타임아웃 발생
   */
  private void runNoAcceptMode() throws Exception {
    logger.info("[NO_ACCEPT] 서버 소켓은 열려있지만 accept()하지 않음");
    logger.info("백로그 = 1, 첫 번째 연결은 OS 큐에 대기, 나머지는 거부됨");
    logger.info("클라이언트는 Connect Timeout이 발생할 때까지 대기하게 됩니다.");

    // accept()를 전혀 호출하지 않음
    // 서버 소켓은 열려있지만 연결을 처리하지 않음
    while (running.get()) {
      Thread.sleep(1000);
      // 아무것도 하지 않음 - accept() 호출 없음
    }
  }

  /**
   * NO_RESPONSE 모드: 연결은 받지만 응답하지 않음
   */
  private void runNoResponseMode() throws Exception {
    logger.info("[NO_RESPONSE] 연결은 수락하지만 데이터를 보내지 않음");

    while (running.get()) {
      try {
        Socket clientSocket = serverSocket.accept();
        logger.info("클라이언트 연결됨: " + clientSocket.getRemoteSocketAddress());
        logger.info("응답하지 않고 연결만 유지 (Read Timeout 유발)");

        new Thread(() -> {
          try {
            // 입력은 읽지만 출력은 하지 않음
            BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream()));

            // 클라이언트 요청 읽기 (로깅용)
            String request = in.readLine();
            logger.debug("요청 받음: " + request);

            // 응답하지 않고 대기
            while (!clientSocket.isClosed() && running.get()) {
              Thread.sleep(1000);
            }
          } catch (Exception e) {
            // 연결 종료
          } finally {
            try {
              clientSocket.close();
            } catch (Exception e) {
              // ignore
            }
          }
        }).start();
      } catch (SocketException e) {
        break;
      }
    }
  }

  /**
   * SLOW_RESPONSE 모드: 매우 천천히 응답
   */
  private void runSlowResponseMode() throws Exception {
    logger.info("[SLOW_RESPONSE] 연결 수락 후 매우 천천히 응답 (1초에 1바이트)");

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

  private void handleSlowResponse(Socket clientSocket) {
    try (BufferedReader in = new BufferedReader(
        new InputStreamReader(clientSocket.getInputStream()));
        PrintWriter out = new PrintWriter(
            clientSocket.getOutputStream(), true)) {

      String request = in.readLine();
      logger.debug("요청: " + request);

      String response = "HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\nSlow Response...";

      // 1초에 1바이트씩 전송
      for (char c : response.toCharArray()) {
        if (!running.get() || clientSocket.isClosed()) break;
        out.print(c);
        out.flush();
        Thread.sleep(1000);
      }
    } catch (Exception e) {
      logger.debug("Slow response 중단: " + e.getMessage());
    }
  }

  /**
   * SLOW_READ 모드: 클라이언트 데이터를 천천히 읽음
   */
  private void runSlowReadMode() throws Exception {
    logger.info("[SLOW_READ] 클라이언트 데이터를 매우 천천히 읽음 (10초에 1바이트)");

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

  private void handleSlowRead(Socket clientSocket) {
    try (InputStream in = clientSocket.getInputStream();
        PrintWriter out = new PrintWriter(
            clientSocket.getOutputStream(), true)) {

      logger.info("천천히 읽기 시작...");
      int totalRead = 0;

      // 10초에 1바이트씩 읽기
      while (!clientSocket.isClosed() && running.get()) {
        int data = in.read();
        if (data == -1) break;

        totalRead++;
        logger.debug("읽은 바이트: " + totalRead);
        Thread.sleep(10000);  // 10초 대기
      }

      out.println("Read complete: " + totalRead + " bytes");
    } catch (Exception e) {
      logger.debug("Slow read 중단: " + e.getMessage());
    }
  }

  /**
   * PARTIAL_READ 모드: 일부만 읽고 멈춤
   */
  private void runPartialReadMode() throws Exception {
    logger.info("[PARTIAL_READ] 10바이트만 읽고 멈춤");

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

  private void handlePartialRead(Socket clientSocket) {
    try (InputStream in = clientSocket.getInputStream();
        PrintWriter out = new PrintWriter(
            clientSocket.getOutputStream(), true)) {

      byte[] buffer = new byte[10];
      int bytesRead = in.read(buffer);  // 수정됨: in.read(buffer) 사용
      logger.info("10바이트만 읽음: " + bytesRead);

      // 더 이상 읽지 않고 대기
      while (!clientSocket.isClosed() && running.get()) {
        Thread.sleep(1000);
      }
    } catch (Exception e) {
      logger.debug("Partial read 종료: " + e.getMessage());
    }
  }

  /**
   * NORMAL 모드: 정상 에코 서버
   */
  private void runNormalMode() throws Exception {
    logger.info("[NORMAL] 정상 에코 서버 모드");

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

  private void handleNormalEcho(Socket clientSocket) {
    try (BufferedReader in = new BufferedReader(
        new InputStreamReader(clientSocket.getInputStream()));
        PrintWriter out = new PrintWriter(
            clientSocket.getOutputStream(), true)) {

      String inputLine;
      while ((inputLine = in.readLine()) != null) {
        logger.debug("받음: " + inputLine);
        out.println("Echo: " + inputLine);

        if ("quit".equalsIgnoreCase(inputLine)) {
          break;
        }
      }
    } catch (Exception e) {
      logger.debug("에코 처리 종료: " + e.getMessage());
    }
  }

  private String getModeDescription() {
    return switch (mode) {
      case NO_ACCEPT -> "Accept 안 함 - Connect Timeout 유발";
      case NO_RESPONSE -> "응답 안 함 - Read Timeout 유발";
      case SLOW_RESPONSE -> "느린 응답 - Read Timeout 가능";
      case SLOW_READ -> "느린 읽기 - Write Timeout 유발";
      case PARTIAL_READ -> "일부만 읽기 - Write Timeout 유발";
      case NORMAL -> "정상 에코 서버";
    };
  }

  public boolean isRunning() {
    return running.get();
  }

  /**
   * 단독 실행용 main 메서드
   */
  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.out.println("Usage: ProblematicServer <mode> [port]");
      System.out.println("Modes: NO_ACCEPT, NO_RESPONSE, SLOW_RESPONSE, SLOW_READ, PARTIAL_READ, NORMAL");
      return;
    }

    ServerMode mode = ServerMode.valueOf(args[0].toUpperCase());
    int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;

    ProblematicServer server = new ProblematicServer(port, mode);
    server.start();

    // Shutdown hook
    Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

    // 서버 계속 실행
    Thread.currentThread().join();
  }
}