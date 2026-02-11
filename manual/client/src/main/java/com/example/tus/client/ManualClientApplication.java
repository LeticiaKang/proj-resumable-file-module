package com.example.tus.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TUS 프로토콜 수동 구현 클라이언트 애플리케이션
 *
 * <p>이 애플리케이션은 TUS(Tus Upload Server) 프로토콜을 WebClient를 사용하여
 * 직접(수동으로) 구현한 파일 업로드 클라이언트입니다.</p>
 *
 * <h3>TUS 프로토콜 핵심 흐름:</h3>
 * <ol>
 *   <li><b>POST</b> - 업로드 세션 생성 (파일 메타데이터 전송, Location 헤더로 fileId 수신)</li>
 *   <li><b>PATCH</b> - 청크 단위 데이터 전송 (Upload-Offset 헤더로 이어받기 위치 지정)</li>
 *   <li><b>HEAD</b> - 현재 업로드 오프셋 조회 (이어받기 시 서버의 현재 위치 확인)</li>
 * </ol>
 *
 * <p>포트: 8081 / TUS 서버: localhost:8082</p>
 */
@SpringBootApplication
public class ManualClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(ManualClientApplication.class, args);
    }
}
