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

@Entity
@Table(name = "tus_minio_file_info")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "upload_uri", nullable = false, unique = true)
    private String uploadUri;

    /** Upload-Metadata의 filename으로 전달 */
    @Column(name = "file_name")
    private String fileName;

    /** Upload-Length 헤더에서 받은 값 */
    @Column(name = "total_size")
    private long totalSize;

    @Column(name = "offset_bytes")
    private long offset;

    /**
     * POST 요청 (세션 생성)
     *     ↓
     * "uploading" → PATCH로 청크 수신 중 (offset 증가)
     *     ↓
     * "completed" → 모든 청크 도착, 로컬 디스크에 완성된 파일 존재
     *     ↓
     * "transferred" → MinIO 버킷으로 전송 완료
     *     ↓
     * (실패 시 어느 단계에서든 "failed"로 전환)
     */
    @Column(name = "status", length = 20)
    private String status;

    /** 클라이언트가 제공한 SHA-256 체크섬 (무결성 검증 기준값) */
    @Column(name = "checksum")
    private String checksum;

    /** 서버 측 체크섬 검증 완료 여부 */
    @Column(name = "checksum_verified")
    private boolean checksumVerified;

    @Column(name = "minio_object_key")
    private String minioObjectKey;

    @Column(name = "callback_sent")
    private boolean callbackSent;

    /** 레코드 생성 일시 */
    @Column(name = "reg_date", updatable = false)
    private LocalDateTime regDate;

    @Column(name = "upd_date")
    private LocalDateTime updDate;

    @PrePersist
    protected void onCreate() {
        this.regDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updDate = LocalDateTime.now();
    }

    public void updateOffset(long newOffset) {
        this.offset = newOffset;
    }

    public void markCompleted() {
        this.status = "completed";
    }

    public void markTransferred(String objectKey) {
        this.status = "transferred";
        this.minioObjectKey = objectKey;
    }

    public void markFailed() {
        this.status = "failed";
    }

    public void markChecksumVerified(boolean verified) {
        this.checksumVerified = verified;
    }

    public void markCallbackSent() {
        this.callbackSent = true;
    }
}
