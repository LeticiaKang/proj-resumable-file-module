package com.example.tus.server.repository;

import com.example.tus.server.entity.FileInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * TUS 파일 정보 JPA 리포지토리.
 * <p>
 * 업로드 세션의 상태를 PostgreSQL에서 관리합니다.
 * 만료 정리, 상태 조회 등에 사용됩니다.
 * </p>
 */
@Repository
public interface FileInfoRepository extends JpaRepository<FileInfo, String> {

    /** 파일 ID로 업로드 정보 조회 */
    Optional<FileInfo> findByFileId(String fileId);

    /** 상태별 업로드 목록 조회 (예: "uploading", "completed") */
    List<FileInfo> findByStatus(String status);

    /**
     * 특정 상태이면서 지정 시각 이전에 마지막으로 업데이트된 업로드 조회.
     * 만료 정리 스케줄러에서 오래된 미완료 업로드를 찾을 때 사용합니다.
     */
    List<FileInfo> findByStatusAndUpdDateBefore(String status, LocalDateTime before);
}
