package com.experiment.timeout_lab.server;

import com.experiment.timeout_lab.util.Constants;
import com.experiment.timeout_lab.util.Logger;
import com.experiment.timeout_lab.util.Constants.ServerMode;
import com.experiment.timeout_lab.util.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
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
        
    }
    
    /**
     * NO_RESPONSE 모드: 연결은 받지만 응답하지 않음
     * Read Timeout을 유발하기 위한 시나리오
     * */
    private void runNoResponseMode() throws Exception {
        
    }

    /**
     * SLOW_RESPONSE 모드: 매우 천천히 응답
     * */
    private void runSlowResponseMode() throws Exception{
        
    }

    /**
     * SLOW_RESPONSE 모드 핸들링 처리
     * */
    private void handleSlowResponse(Socket clientSocket) {
        
    }

    /**
     * SLOW_READ 모드: 클라이언트 데이터를 매우 천천히 읽음
     * Write Timeout을 유발하기 위한 시나리오
     * */
    private void runSlowReadMode() throws Exception {

    }

    /**
     * SLOW_READ 모드 핸들링 처리
     * */
    private void handleSlowRead(Socket clientSocket) {

    }

    /**
     * PARTIAL_READ 모드: 일부만 읽고 멈춤
     * */
    private void runPartialReadMode() throws Exception {

    }

    /**
     * PARTIAL_READ 모드 핸들링 처리
     * */
    private void handlePartialRead(Socket clientSocket) {

    }

    /**
     * NORMAL 모드: 정상 동작
     * */
    private void runNormalMode() throws Exception {

    }

    /**
     * 노말 모드 핸들링 처리
     * */
    private void handleNormalEcho(Socket clientSocket) {

    }

    /**
     * 서버 종료
     * */
    public void stop() {
        
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

    }
}
