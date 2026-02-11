package com.example.tusminio.client.entity;

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
 * TUS 업로드 URL 저장 엔티티
 *
 * tus-java-client의 TusURLStore 인터페이스 구현체(TusURLDatabaseStore)가
 * 이 엔티티를 통해 업로드 URL을 PostgreSQL에 저장/조회/삭제한다.
 *
 * fingerprint: 파일의 고유 식별자 (파일 경로 + 크기 기반 해시)
 *   - tus-java-client가 내부적으로 생성하며, 동일 파일의 이어받기를 판별하는 키
 * uploadUrl: TUS 서버가 Creation 응답에서 반환한 업로드 URL (Location 헤더 값)
 *   - 이 URL로 PATCH 요청을 보내 이어받기 업로드를 수행
 */
@Entity
@Table(name = "tus_upload_url")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TusUploadUrl {

    /**
     * 파일 fingerprint (PK)
     * tus-java-client가 파일 경로와 크기로 자동 생성하는 고유 식별자
     */
    @Id
    @Column(name = "fingerprint", nullable = false, length = 512)
    private String fingerprint;

    /**
     * TUS 서버가 발급한 업로드 URL
     * 예: http://localhost:8086/files/abcdef1234567890
     */
    @Column(name = "upload_url", nullable = false, length = 2048)
    private String uploadUrl;

    /** 최초 등록 일시 */
    @Column(name = "reg_date")
    private LocalDateTime regDate;

    /** 최종 수정 일시 */
    @Column(name = "upd_date")
    private LocalDateTime updDate;

    @PrePersist
    protected void onCreate() {
        this.regDate = LocalDateTime.now();
        this.updDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updDate = LocalDateTime.now();
    }
}
