package com.example.tusminio.server.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * TUS 서버 핵심 설정
 */
@Slf4j
@Getter
@Setter
@ConfigurationProperties(prefix = "tus")
public class TusServerProperties {

    /** 업로드 파일 임시 저장 디렉토리 경로 (tus-java-server가 청크를 저장하는 위치) */
    private String storagePath = "./tus-minio/server/files";

    /** 최대 업로드 크기 (바이트). 기본값: 1GB (1073741824 bytes) */
    private long maxUploadSize = 1_073_741_824L;

    /** DB offset 갱신 간격 (퍼센트 단위). 기본값: 10 → 10% 단위로 갱신. 0이면 매 청크마다 갱신 */
    private int offsetUpdatePercent = 10;

    /**
     * 서버 시작 시 저장 디렉토리가 존재하지 않으면 자동 생성합니다.
     * tus-java-server 라이브러리가 파일 청크를 저장할 디렉토리가 반드시 필요합니다.
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
