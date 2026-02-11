package com.example.tusminio.server.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 클라이언트 설정.
 * <p>
 * MinIO 오브젝트 스토리지에 연결하기 위한 MinioClient 빈을 생성합니다.
 * 서버 시작 시 지정된 버킷이 존재하는지 확인하고, 없으면 자동 생성합니다.
 * </p>
 *
 * <h3>로컬 → MinIO 전송 패턴:</h3>
 * <ol>
 *   <li>tus-java-server가 업로드 청크를 로컬 디스크에 저장</li>
 *   <li>업로드 완료 시 MinioClient를 사용하여 MinIO 버킷으로 전송</li>
 *   <li>전송 완료 후 로컬 임시 파일 삭제 (설정에 따라)</li>
 * </ol>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MinioConfig {

    private final MinioProperties minioProperties;

    /**
     * MinioClient 빈 생성.
     * MinioProperties에서 엔드포인트, 접근 키, 비밀 키를 읽어 클라이언트를 구성합니다.
     */
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();
    }

    /**
     * 서버 시작 시 MinIO 버킷 존재 여부를 확인하고, 없으면 생성합니다.
     * 업로드 파일이 저장될 버킷이 반드시 존재해야 합니다.
     */
    @PostConstruct
    public void ensureBucketExists() {
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(minioProperties.getEndpoint())
                    .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                    .build();
            String bucket = minioProperties.getBucket();

            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build()
            );

            if (!exists) {
                client.makeBucket(
                        MakeBucketArgs.builder().bucket(bucket).build()
                );
                log.info("=== [MINIO] 버킷 확인/생성: {} (새로 생성됨) ===", bucket);
            } else {
                log.info("=== [MINIO] 버킷 확인/생성: {} (이미 존재) ===", bucket);
            }
        } catch (Exception e) {
            log.warn("=== [MINIO] 버킷 확인/생성 실패: {} (서버 시작 후 수동 확인 필요) ===",
                    e.getMessage());
        }
    }
}
