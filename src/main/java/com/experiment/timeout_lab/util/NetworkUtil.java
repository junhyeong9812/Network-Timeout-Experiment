package com.experiment.timeout_lab.util;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * 네트워크 관련 유틸리티 클래스
 *
 * 포트 확인, 네트워크 인터페이스 정보, 연결 테스트 등
 * 네트워크 관련 도우미 기능을 제공합니다.
 * */
public class NetworkUtil {

  private static final Logger logger = new Logger(NetworkUtil.class);

  /**
   * 지정된 포트가 사용 가능한지 확인
   *
   * @param port 확인할 포트 번호
   * @return true면 사용 가능, false면 이미 사용 중
   */
  public static boolean isPortAvailable(int port) {
    try (ServerSocket serverSocket = new ServerSocket(port)) {
      serverSocket.setReuseAddress(true);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * 사용 가능한 포트를 찾아 반환
   *
   * @param startPort 시작 포트 번호
   * @param endPort 종료 포트 번호
   * @return 사용 가능한 포트 번호, 없으면 -1
   */
  public static int findAvailablePort(int startPort, int endPort) {
    for (int port = startPort; port <= endPort; port++) {
      if (isPortAvailable(port)) {
        return port;
      }
    }
    return -1;
  }

  /**
   * 서버에 연결 가능한지 확인 (ping 대체)
   *
   * @param host 호스트 주소
   * @param port 포트 번호
   * @param timeout 타임아웃 (밀리초)
   * @return 연결 가능 여부
   */
  public static boolean isReachable(String host, int port, int timeout) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), timeout);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * 로컬 IP 주소 목록 반환
   *
   * @return IP 주소 목록
   */
  public static List<String> getLocalIpAddress() {
    List<String> addresses = new ArrayList<>();
    try {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces.hasMoreElements()) {
        NetworkInterface ni = interfaces.nextElement();
        if (ni.isUp() && ni.isLoopback()) {
          Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
          while (inetAddresses.hasMoreElements()) {
            InetAddress addr = inetAddresses.nextElement();
            if (addr instanceof Inet4Address) {
              addresses.add(addr.getHostAddress());
            }
          }
        }
      }
    } catch (SocketException e) {
      logger.error("네트워크 인터페이스 조회 실패", e);
    }
    return addresses;
  }

  /**
   * 시스템 네트워크 정보 출력
   */
  public static void printNetworkInfo() {
    System.out.println("\n=== 시스템 네트워크 정보 ===");

    try {
      /* 호스트 정보 */
      InetAddress localhost = InetAddress.getLocalHost();
      System.out.println("호스트명: " + localhost.getHostName());
      System.out.println("로컬 IP: " + localhost.getHostAddress());

      // 모든 네트워크 인터페이스
      System.out.println("\n네트워크 인터페이스");
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces.hasMoreElements()) {
        NetworkInterface ni = interfaces.nextElement();
        if(ni.isUp()) {
          System.out.println("  • " + ni.getName() + " - " + ni.getDisplayName());
          System.out.println("    상태: " + (ni.isUp() ? "UP" : "DOWN") +
              ", Loopback: " + ni.isLoopback());

          Enumeration<InetAddress> addresses = ni.getInetAddresses();
          while (addresses.hasMoreElements()) {
            InetAddress addr = addresses.nextElement();
            System.out.println("    IP: " + addr.getHostAddress());
          }
        }
      }

    } catch (Exception e) {
      logger.error("네트워크 정보 조회 실패", e);
    }
  }

  /**
   * TCP 소켓 옵션 정보 출력
   *
   * @param socket 소켓 객체
   */
  public static void printSocketOptions(Socket socket) {
    try {
      System.out.println("\n=== Socket Options ===");
      System.out.println("SO_TIMEOUT: " + socket.getSoTimeout() + "ms");
      System.out.println("SO_KEEPALIVE: " + socket.getKeepAlive());
      System.out.println("TCP_NODELAY: " + socket.getTcpNoDelay());
      System.out.println("SO_LINGER: " + socket.getSoLinger());
      System.out.println("SO_RCVBUF: " + socket.getReceiveBufferSize() + " bytes");
      System.out.println("SO_SNDBUF: " + socket.getSendBufferSize() + " bytes");
      System.out.println("SO_REUSEADDR: " + socket.getReuseAddress());
    } catch (SocketException e) {
      logger.error("소켓 옵션 조회 실패", e);
    }
  }

  /**
   * 연결 지연시간 측정
   *
   * @param host 호스트 주소
   * @param port 포트 번호
   * @param attempts 시도 횟수
   * @return 평균 지연시간 (밀리초), 실패 시 -1
   */
  public static long measureLatency(String host, int port, int attempts) {
    long totalTime = 0;
    int successCount = 0;

    for (int i = 0; i < attempts; i++) {
      long startTime = System.currentTimeMillis();

      try (Socket socket = new Socket()) {
        socket.connect(new InetSocketAddress(host, port), 5000);
        long latency = System.currentTimeMillis() - startTime;
        totalTime += latency;
        successCount++;
      } catch (IOException e) {
        // 연결 실패
        System.out.println("연결 실패");
      }
    }

    return successCount > 0 ? totalTime / successCount : -1;
  }

  /**
   * 포트 범위 스캔
   *
   * @param host 호스트 주소
   * @param startPort 시작 포트
   * @param endPort 종료 포트
   * @return 열려있는 포트 목록
   */
  public static List<Integer> scanPorts(String host, int startPort, int endPort) {
    List<Integer> openPorts = new ArrayList<>();

    System.out.println("포트 스캔 중: " + host + " (" + startPort + "-" + endPort + ")");

    for (int port = startPort; port <= endPort; port++) {
      try (Socket socket = new Socket()) {
        socket.connect(new InetSocketAddress(host, port), 100);
        openPorts.add(port);
        System.out.println("  • Port " + port + ": OPEN");
      } catch (IOException e) {
        //포트 닫힘
        System.out.println("  • Port " + port + ": CLOSE");
      }
    }

    return openPorts;
  }

  /**
   * 바이트 크기를 읽기 쉬운 형식으로 변환
   *
   * @param bytes 바이트 크기
   * @return 포맷된 문자열 (예: "1.5 MB")
   */
  public static String formatBytes(long bytes) {
    if (bytes < 1024) return bytes + "B";
    if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
    if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
    return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
  }

  /**
   * 시간을 읽기 쉬운 형식으로 변환
   *
   * @param millis 밀리초
   * @return 포맷된 문자열 (예: "1m 30s")
   */
  public static String formatDuration(long millis) {
    if (millis < 1000) return millis + "ms";

    long seconds = millis / 1000;
    if (seconds < 60) return seconds + "s " + (millis % 1000) + "ms";

    long minutes = seconds / 60;
    seconds = seconds % 60;
    if (minutes < 60) return minutes + "m " + seconds + "s";

    long hours = minutes / 60;
    minutes = minutes % 60;
    return hours + "h " + minutes + "m " + seconds + "s";
  }
}
