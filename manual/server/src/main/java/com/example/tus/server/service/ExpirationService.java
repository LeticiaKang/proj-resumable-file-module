package com.example.tus.server.service;

import com.example.tus.server.config.ExpirationProperties;
import com.example.tus.server.entity.FileInfo;
import com.example.tus.server.repository.FileInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 만료 업로드 정리 스케줄러 서비스.
 * <p>
 * TUS 프로토콜의 expiration 확장을 구현합니다.
 * 일정 시간(기본 24시간) 동안 청크 수신이 없는 미완료 업로드를
 * 주기적으로(기본 1시간 간격) 확인하여 디스크와 DB에서 정리합니다.
 * </p>
 * <p>
 * 이를 통해 클라이언트가 업로드를 중단하고 재개하지 않는 경우
 * 서버 스토리지가 불필요하게 차지되는 것을 방지합니다.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpirationService {

    private final ExpirationProperties expirationProperties;
    private final FileInfoRepository fileInfoRepository;
    private final TusUploadService tusUploadService;

    /**
     * 만료된 업로드를 정기적으로 정리합니다.
     * <p>
     * 실행 조건:
     * - tus.expiration.enabled=true 인 경우에만 동작
     * - 실행 주기: tus.expiration.cleanup-interval (기본 1시간)
     * </p>
     * <p>
     * 정리 대상:
     * - status가 "uploading"이면서
     * - updDate가 (현재시각 - timeout) 이전인 레코드
     * </p>
     */
    @Scheduled(fixedDelayString = "${tus.expiration.cleanup-interval:PT1H}")
    public void cleanupExpiredUploads() {
        if (!expirationProperties.isEnabled()) {
            log.debug("=== [EXPIRATION] 만료 정리 비활성화 상태 ===");
            return;
        }

        // 만료 기준 시각 계산: 현재 시각 - timeout
        LocalDateTime expirationThreshold = LocalDateTime.now()
                .minus(expirationProperties.getTimeout());

        log.info("=== [EXPIRATION] 만료 업로드 정리 시작 (기준시각: {} 이전) ===", expirationThreshold);

        // 만료 대상 조회: "uploading" 상태이면서 마지막 업데이트가 기준시각 이전인 레코드
        List<FileInfo> expiredUploads = fileInfoRepository
                .findByStatusAndUpdDateBefore("uploading", expirationThreshold);

        if (expiredUploads.isEmpty()) {
            log.info("=== [EXPIRATION] 만료된 업로드 없음 ===");
            return;
        }

        int deletedCount = 0;
        for (FileInfo fileInfo : expiredUploads) {
            try {
                // 디스크 파일 삭제
                Path filePath = tusUploadService.getFilePath(fileInfo.getFileId());
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                    log.debug("=== [EXPIRATION] 디스크 파일 삭제: {} ===", filePath);
                }

                // DB 레코드 삭제
                fileInfoRepository.delete(fileInfo);
                deletedCount++;

                log.info("=== [EXPIRATION] 만료 업로드 삭제 fileId={}, fileName={}, lastUpdate={} ===",
                        fileInfo.getFileId(), fileInfo.getFileName(), fileInfo.getUpdDate());

            } catch (IOException e) {
                log.error("=== [EXPIRATION] 만료 업로드 삭제 실패 fileId={}: {} ===",
                        fileInfo.getFileId(), e.getMessage(), e);
            }
        }

        log.info("=== [EXPIRATION] 만료 업로드 정리: {}건 삭제 ===", deletedCount);
    }
}
