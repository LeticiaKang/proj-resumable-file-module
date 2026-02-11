package com.example.tusminio.server.service;

import com.example.tusminio.server.entity.FileInfo;
import com.example.tusminio.server.repository.FileInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 업로드 진행 상태를 트랜잭션 내에서 안전하게 갱신하는 서비스.
 * TusFilter(서블릿 필터)에서는 Spring 프록시 기반 @Transactional이 동작하지 않으므로,
 * DB 조작 로직을 이 서비스로 위임하여 트랜잭션과 Optimistic Locking을 보장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadProgressService {

    private final FileInfoRepository fileInfoRepository;

    /**
     * 업로드 오프셋을 갱신합니다.
     *
     * @return 갱신된 FileInfo, 없으면 null
     */
    @Transactional
    public FileInfo updateOffsetIfNeeded(String uploadUri, long currentOffset, long totalLength, int offsetUpdatePercent) {
        Optional<FileInfo> optional = fileInfoRepository.findByUploadUri(uploadUri);
        if (optional.isEmpty()) {
            log.warn("=== [PROGRESS] DB에 FileInfo 없음: {} ===", uploadUri);
            return null;
        }

        FileInfo fileInfo = optional.get();
        boolean isComplete = currentOffset == totalLength;

        if (isComplete || shouldUpdate(fileInfo.getOffset(), currentOffset, totalLength, offsetUpdatePercent)) {
            fileInfo.updateOffset(currentOffset);
            fileInfoRepository.save(fileInfo);
            log.debug("=== [PROGRESS] offset DB 갱신: {}/{} ===", currentOffset, totalLength);
        }

        return fileInfo;
    }

    /**
     * 업로드 완료 상태로 변경합니다.
     */
    @Transactional
    public FileInfo markCompleted(String uploadUri) {
        Optional<FileInfo> optional = fileInfoRepository.findByUploadUri(uploadUri);
        if (optional.isEmpty()) {
            return null;
        }

        FileInfo fileInfo = optional.get();
        fileInfo.markCompleted();
        return fileInfoRepository.save(fileInfo);
    }

    /**
     * 퍼센트 구간이 변경되었는지 판단합니다.
     * 예: offsetUpdatePercent=10이면, 0→9%는 스킵, 9→10%에서 갱신.
     */
    private boolean shouldUpdate(long lastOffset, long currentOffset, long totalLength, int offsetUpdatePercent) {
        if (offsetUpdatePercent <= 0 || totalLength <= 0) {
            return true; // 0이면 매번 갱신
        }
        int lastBucket = (int) (lastOffset * 100 / totalLength / offsetUpdatePercent);
        int currentBucket = (int) (currentOffset * 100 / totalLength / offsetUpdatePercent);
        return currentBucket > lastBucket;
    }
}
