package com.example.tus.server;

import com.example.tus.server.config.CallbackProperties;
import com.example.tus.server.config.ExpirationProperties;
import com.example.tus.server.config.TusServerProperties;
import com.example.tus.server.config.ValidationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TUS 프로토콜 수동 구현 서버 애플리케이션.
 * <p>
 * TUS(Tus Upload Server) 프로토콜의 5가지 핵심 엔드포인트를 직접 구현하여
 * 대용량 파일의 재개 가능한(Resumable) 업로드를 지원합니다.
 * </p>
 * <ul>
 *   <li>OPTIONS /files - 서버 기능 조회</li>
 *   <li>POST /files - 업로드 세션 생성</li>
 *   <li>HEAD /files/{fileId} - 현재 오프셋 확인</li>
 *   <li>PATCH /files/{fileId} - 청크 데이터 수신</li>
 *   <li>DELETE /files/{fileId} - 업로드 취소/삭제</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        TusServerProperties.class,
        ExpirationProperties.class,
        ValidationProperties.class,
        CallbackProperties.class
})
public class ManualServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ManualServerApplication.class, args);
    }
}
