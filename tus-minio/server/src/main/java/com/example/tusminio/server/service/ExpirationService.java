package com.example.tusminio.server.service;

import com.example.tusminio.server.config.ExpirationProperties;
import com.example.tusminio.server.entity.FileInfo;
import com.example.tusminio.server.repository.FileInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.desair.tus.server.TusFileUploadService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 만료 업로드 정리 스케줄 서비스.
 * <p>
 * 일정 시간 동안 완료되지 않은 업로드를 주기적으로 정리합니다.
 * tus-java-server 라이브러리의 로컬 저장소와 PostgreSQL DB에서 모두 삭제합니다.
 * </p>
 *
 * <h3>정리 대상:</h3>
 * <ul>
 *   <li>상태가 "uploading"이면서 만료 시간(tus.expiration.timeout)이 지난 업로드</li>
 *   <li>tus-java-server의 deleteUpload()로 로컬 파일 삭제</li>
 *   <li>DB에서 FileInfo 레코드 삭제</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpirationService {

    private final ExpirationProperties expirationProperties;
    private final FileInfoRepository fileInfoRepository;
    private final TusFileUploadService tusFileUploadService;

    /**
     * 만료된 업로드를 주기적으로 정리
     */
    @Scheduled(fixedDelayString = "#{@expirationProperties.cleanupInterval.toMillis()}")
    public void cleanupExpiredUploads() {
        // 만료 정책이 비활성화된 경우 건너뜀
        if (!expirationProperties.isEnabled()) {
            return;
        }

        // 만료 기준 시각 계산: 현재 시각 - 만료 시간
        LocalDateTime cutoff = LocalDateTime.now().minus(expirationProperties.getTimeout());

        // DB에서 만료 대상 조회: 상태가 "uploading"이면서 cutoff 이전에 마지막 수정된 레코드
        List<FileInfo> expiredUploads = fileInfoRepository.findByStatusAndUpdDateBefore(
                "uploading", cutoff
        );

        if (expiredUploads.isEmpty()) {
            return;
        }

        int count = 0;
        for (FileInfo fileInfo : expiredUploads) {
            try {
                // tus-java-server의 로컬 저장소에서 파일 삭제
                tusFileUploadService.deleteUpload(fileInfo.getUploadUri());

                // DB에서 레코드 삭제
                fileInfoRepository.delete(fileInfo);
                count++;

                log.debug("=== [EXPIRATION] 만료 업로드 삭제: uri={}, fileName={} ===",
                        fileInfo.getUploadUri(), fileInfo.getFileName());

            } catch (Exception e) {
                log.error("=== [EXPIRATION] 만료 업로드 삭제 실패: uri={}, error={} ===",
                        fileInfo.getUploadUri(), e.getMessage());
            }
        }

        log.info("=== [EXPIRATION] 만료 업로드 정리: {}건 ===", count);
    }
}
