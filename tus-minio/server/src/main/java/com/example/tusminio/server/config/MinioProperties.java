package com.example.tusminio.server.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 오브젝트 스토리지 접속 설정.
 * <p>
 * TUS 프로토콜로 로컬에 저장된 파일을 MinIO로 전송할 때 사용되는 설정입니다.
 * - endpoint: MinIO 서버 URL
 * - accessKey/secretKey: 인증 정보
 * - bucket: 파일이 저장될 버킷 이름
 * - deleteLocalAfterTransfer: MinIO 전송 완료 후 로컬 파일 삭제 여부
 * </p>
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
