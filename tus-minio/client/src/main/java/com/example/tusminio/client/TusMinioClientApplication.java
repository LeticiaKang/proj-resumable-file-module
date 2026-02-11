package com.example.tusminio.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * TUS 프로토콜 클라이언트 애플리케이션 (tus-java-client 라이브러리 사용)
 *
 * tus-java-client 라이브러리가 TUS 프로토콜의 Creation, Upload, Resume 등의
 * HTTP 요청/응답 처리를 내부적으로 래핑하므로, 클라이언트는 간단한 API 호출만으로
 * 대용량 파일의 이어받기 업로드를 구현할 수 있다.
 *
 * @EnableAsync: 배치 업로드 시 비동기 처리를 위해 활성화
 */
@EnableAsync
@SpringBootApplication
public class TusMinioClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(TusMinioClientApplication.class, args);
    }
}
