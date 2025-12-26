package com.experiment.timeout_lab.test;

import java.net.*;
import java.io.*;

/**
 * 진짜 Connect Timeout 테스트
 *
 * 실제 네트워크 환경에서 타임아웃 발생
 */
public class RealTimeoutTest {

  public static void main(String[] args) {
    System.out.println("=== 진짜 Connect Timeout 테스트 ===\n");

    // 테스트 1: 도달 불가능한 IP (프라이빗 네트워크)
    System.out.println("테스트 1: 도달 불가능한 IP");
    testTimeout("10.255.255.1", 80, 3000);

    // 테스트 2: 방화벽에 막힌 포트
    System.out.println("\n테스트 2: 방화벽 포트 (Google DNS의 닫힌 포트)");
    testTimeout("8.8.8.8", 81, 3000);

    // 테스트 3: 블랙홀 IP (문서화된 테스트용 IP)
    System.out.println("\n테스트 3: 블랙홀 IP (192.0.2.1)");
    testTimeout("192.0.2.1", 80, 3000);
  }

  static void testTimeout(String host, int port, int timeout) {
    System.out.println("연결 시도: " + host + ":" + port + " (타임아웃: " + timeout + "ms)");

    long startTime = System.currentTimeMillis();

    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), timeout);
      System.out.println("❌ 연결 성공 (예상: 타임아웃)");
    } catch (SocketTimeoutException e) {
      long actualTime = System.currentTimeMillis() - startTime;
      System.out.println("✅ Connect Timeout 발생!");
      System.out.println("   설정: " + timeout + "ms, 실제: " + actualTime + "ms");
      System.out.println("   오차: " + Math.abs(actualTime - timeout) + "ms");
    } catch (ConnectException e) {
      long actualTime = System.currentTimeMillis() - startTime;
      System.out.println("❓ Connection refused (즉시 거부)");
      System.out.println("   소요 시간: " + actualTime + "ms");
    } catch (Exception e) {
      System.out.println("❓ 다른 예외: " + e.getClass().getSimpleName() + " - " + e.getMessage());
    }
  }
}