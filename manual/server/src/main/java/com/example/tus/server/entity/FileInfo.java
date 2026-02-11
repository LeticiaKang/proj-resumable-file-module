package com.example.tus.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * TUS 업로드 파일 정보 엔티티.
 * <p>
 * TUS 프로토콜에서 각 업로드 세션의 상태를 추적하는 핵심 엔티티입니다.
 * - fileId: 업로드 세션의 고유 식별자 (UUID)
 * - offset: 현재까지 수신된 바이트 수 (재개 시 이 위치부터 이어서 수신)
 * - status: 업로드 상태 ("uploading" → "completed" 또는 "failed")
 * - checksum: 클라이언트가 제공한 SHA-256 체크섬 (무결성 검증용)
 * </p>
 */
@Entity
@Table(name = "tus_file_info")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInfo {

    /** 업로드 세션 고유 ID (UUID 형식) */
    @Id
    @Column(name = "file_id", length = 36)
    private String fileId;

    /** 원본 파일명 (클라이언트가 Upload-Metadata로 전달) */
    @Column(name = "file_name")
    private String fileName;

    /** 파일 전체 크기 (바이트). Upload-Length 헤더에서 받은 값 */
    @Column(name = "total_size")
    private long totalSize;

    /**
     * 현재 업로드 오프셋 (바이트).
     * TUS HEAD 요청 시 이 값을 Upload-Offset 헤더로 반환하며,
     * 클라이언트는 이 위치부터 이어서 청크를 전송합니다.
     */
    @Column(name = "offset_bytes")
    private long offset;

    /**
     * 업로드 상태.
     * - "uploading": 업로드 진행 중
     * - "completed": 업로드 완료
     * - "failed": 업로드 실패
     */
    @Column(name = "status", length = 20)
    private String status;

    /** 클라이언트가 제공한 SHA-256 체크섬 (무결성 검증 기준값) */
    @Column(name = "checksum")
    private String checksum;

    /** 서버 측 체크섬 검증 완료 여부 */
    @Column(name = "checksum_verified")
    private boolean checksumVerified;

    /** 업로드 완료 콜백 전송 여부 */
    @Column(name = "callback_sent")
    private boolean callbackSent;

    /** 레코드 생성 일시 */
    @Column(name = "reg_date", updatable = false)
    private LocalDateTime regDate;

    /** 레코드 수정 일시 (마지막 청크 수신 시각) */
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
