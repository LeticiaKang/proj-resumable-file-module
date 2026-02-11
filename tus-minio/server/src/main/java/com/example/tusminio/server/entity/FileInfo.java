package com.example.tusminio.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * TUS-MinIO 업로드 파일 정보 엔티티.
 * <p>
 * tus-java-server 라이브러리가 관리하는 업로드 세션의 상태를 PostgreSQL에 추적합니다.
 * 수동 구현 서버와 달리, 이 엔티티는 uploadUri(TUS 라이브러리가 생성한 URI)를 기준으로 추적하며,
 * MinIO 전송 상태까지 관리합니다.
 * </p>
 *
 * <h3>상태 흐름:</h3>
 * <ul>
 *   <li>"uploading" → TUS POST로 세션 생성됨, 청크 수신 중</li>
 *   <li>"completed" → 모든 청크 수신 완료, MinIO 전송 대기</li>
 *   <li>"transferred" → MinIO 버킷으로 전송 완료</li>
 *   <li>"failed" → 업로드 또는 전송 실패</li>
 * </ul>
 */
@Entity
@Table(name = "tus_minio_file_info")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInfo {

    /** 자동 생성 ID (시퀀스) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * TUS 업로드 URI (고유값).
     * tus-java-server 라이브러리가 POST 응답의 Location 헤더로 반환하는 URI입니다.
     * 예: /files/1a2b3c4d-5e6f-...
     */
    @Column(name = "upload_uri", unique = true)
    private String uploadUri;

    /** 원본 파일명 (클라이언트가 Upload-Metadata의 filename으로 전달) */
    @Column(name = "file_name")
    private String fileName;

    /** 파일 전체 크기 (바이트). Upload-Length 헤더에서 받은 값 */
    @Column(name = "total_size")
    private long totalSize;

    /**
     * 현재 업로드 오프셋 (바이트).
     * tus-java-server가 관리하는 UploadInfo.getOffset()과 동기화됩니다.
     * 클라이언트 진행률 조회에 사용됩니다.
     */
    @Column(name = "offset_bytes")
    private long offset;

    /**
     * 업로드/전송 상태.
     * - "uploading": 업로드 진행 중 (청크 수신 중)
     * - "completed": 업로드 완료 (로컬 디스크에 전체 파일 존재)
     * - "transferred": MinIO 버킷으로 전송 완료
     * - "failed": 업로드 또는 MinIO 전송 실패
     */
    @Column(name = "status", length = 20)
    private String status;

    /** 클라이언트가 제공한 SHA-256 체크섬 (무결성 검증 기준값) */
    @Column(name = "checksum")
    private String checksum;

    /** 서버 측 체크섬 검증 완료 여부 */
    @Column(name = "checksum_verified")
    private boolean checksumVerified;

    /**
     * MinIO 오브젝트 키.
     * MinIO 버킷 내에서 파일을 식별하는 키입니다.
     * 형식: "{uploadId}/{fileName}"
     * 전송 완료 후 설정됩니다.
     */
    @Column(name = "minio_object_key")
    private String minioObjectKey;

    /** 업로드 완료 콜백 전송 여부 */
    @Column(name = "callback_sent")
    private boolean callbackSent;

    /** 레코드 생성 일시 */
    @Column(name = "reg_date", updatable = false)
    private LocalDateTime regDate;

    /** 레코드 수정 일시 (마지막 청크 수신 또는 상태 변경 시각) */
    @Column(name = "upd_date")
    private LocalDateTime updDate;

    /** 엔티티 최초 저장 시 생성일시/수정일시 자동 설정 */
    @PrePersist
    protected void onCreate() {
        this.regDate = LocalDateTime.now();
        this.updDate = LocalDateTime.now();
    }

    /** 엔티티 수정 시 수정일시 자동 갱신 */
    @PreUpdate
    protected void onUpdate() {
        this.updDate = LocalDateTime.now();
    }
}
