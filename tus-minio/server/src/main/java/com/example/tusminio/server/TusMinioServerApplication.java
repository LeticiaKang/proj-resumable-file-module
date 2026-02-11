package com.example.tusminio.server;

import com.example.tusminio.server.config.CallbackProperties;
import com.example.tusminio.server.config.ExpirationProperties;
import com.example.tusminio.server.config.MinioProperties;
import com.example.tusminio.server.config.TusServerProperties;
import com.example.tusminio.server.config.ValidationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TUS 프로토콜 + MinIO 스토리지 서버 애플리케이션.
 * <p>
 * tus-java-server 라이브러리(me.desair.tus)를 사용하여 TUS 프로토콜을 처리하고,
 * 업로드 완료된 파일을 MinIO 오브젝트 스토리지로 전송합니다.
 * PostgreSQL에 업로드 상태를 추적하여 진행률 조회, 콜백, 만료 기능을 지원합니다.
 * </p>
 *
 * <h3>아키텍처 흐름:</h3>
 * <ol>
 *   <li>클라이언트 → TUS 프로토콜 (POST/PATCH/HEAD) → tus-java-server 라이브러리가 로컬 디스크에 저장</li>
 *   <li>업로드 완료 → 로컬 파일을 MinIO 버킷으로 전송</li>
 *   <li>전송 완료 → 로컬 파일 삭제 (설정에 따라)</li>
 * </ol>
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        TusServerProperties.class,
        ExpirationProperties.class,
        ValidationProperties.class,
        CallbackProperties.class,
        MinioProperties.class
})
public class TusMinioServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TusMinioServerApplication.class, args);
    }
}
