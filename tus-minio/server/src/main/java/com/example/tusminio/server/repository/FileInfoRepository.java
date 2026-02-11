package com.example.tusminio.server.repository;

import com.example.tusminio.server.entity.FileInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * TUS-MinIO 파일 정보 리포지토리.
 * <p>
 * PostgreSQL에 저장된 업로드 상태를 조회/관리합니다.
 * tus-java-server 라이브러리가 자체적으로 관리하는 업로드 정보와 별도로,
 * 이 리포지토리를 통해 진행률 조회, 만료 정리, 콜백 관리 등의 부가 기능을 지원합니다.
 * </p>
 */
@Repository
public interface FileInfoRepository extends JpaRepository<FileInfo, Long> {

    /**
     * TUS 업로드 URI로 파일 정보 조회.
     * tus-java-server가 생성한 업로드 URI를 기준으로 DB 레코드를 찾습니다.
     *
     * @param uploadUri TUS 업로드 URI (예: /files/1a2b3c4d-...)
     * @return 해당 업로드의 FileInfo (존재하지 않으면 empty)
     */
    Optional<FileInfo> findByUploadUri(String uploadUri);

    /**
     * 특정 상태의 모든 업로드 조회.
     * 예: "uploading" 상태의 진행 중 업로드, "completed" 상태의 전송 대기 파일 등
     *
     * @param status 업로드 상태 ("uploading", "completed", "transferred", "failed")
     * @return 해당 상태의 FileInfo 목록
     */
    List<FileInfo> findByStatus(String status);

    /**
     * 만료된 업로드 조회.
     * 특정 상태이면서 지정 시각 이전에 마지막 수정된 레코드를 찾습니다.
     * 만료 정리 스케줄러(ExpirationService)에서 사용합니다.
     *
     * @param status 대상 상태 (주로 "uploading")
     * @param before 기준 시각 (이 시각 이전에 수정된 레코드만 조회)
     * @return 만료 대상 FileInfo 목록
     */
    List<FileInfo> findByStatusAndUpdDateBefore(String status, LocalDateTime before);
}
