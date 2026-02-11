package com.example.tusminio.server.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 오브젝트 스토리지 접속 설정
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /** MinIO 서버 엔드포인트 URL */
    private String endpoint = "http://localhost:9000";

    /** MinIO 접근 키 (Access Key) */
    private String accessKey = "minioadmin";

    /** MinIO 비밀 키 (Secret Key) */
    private String secretKey = "minioadmin123";

    /** 업로드 파일이 저장될 MinIO 버킷 이름 */
    private String bucket = "tus-uploads";

    /** MinIO 전송 완료 후 로컬 임시 파일 삭제 여부 */
    private boolean deleteLocalAfterTransfer = true;
}
