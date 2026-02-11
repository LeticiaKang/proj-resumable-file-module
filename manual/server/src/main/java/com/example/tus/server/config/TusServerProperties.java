package com.example.tus.server.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * TUS 서버 핵심 설정.
 * <p>
 * - storagePath: 업로드 파일이 저장될 로컬 디스크 경로
 * - maxUploadSize: 허용되는 최대 업로드 크기 (바이트 단위, 기본 1GB)
 * </p>
 */
@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "tus")
public class TusServerProperties {

    /** 업로드 파일 저장 디렉토리 경로 */
    private String storagePath = "./manual/server/files";

    /** 최대 업로드 크기 (바이트). 기본값: 1GB (1073741824 bytes) */
    private long maxUploadSize = 1_073_741_824L;

    /**
     * 서버 시작 시 저장 디렉토리가 존재하지 않으면 자동 생성합니다.
     * TUS 프로토콜에서 파일 청크를 저장할 디렉토리가 반드시 필요합니다.
     */
    @PostConstruct
    public void init() throws IOException {
        Path path = Paths.get(storagePath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
            log.info("=== [TUS CONFIG] 저장 디렉토리 생성: {} ===", path.toAbsolutePath());
        } else {
            log.info("=== [TUS CONFIG] 저장 디렉토리 확인 완료: {} ===", path.toAbsolutePath());
        }
        log.info("=== [TUS CONFIG] 최대 업로드 크기: {} bytes ({} MB) ===",
                maxUploadSize, maxUploadSize / (1024 * 1024));
    }
}
