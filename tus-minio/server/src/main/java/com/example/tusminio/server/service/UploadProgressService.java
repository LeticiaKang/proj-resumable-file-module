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
 * <p>
 * TusFilter(서블릿 필터)에서는 Spring 프록시 기반 @Transactional이 동작하지 않으므로,
 * DB 조작 로직을 이 서비스로 위임하여 트랜잭션과 Optimistic Locking을 보장합니다.
 * </p>
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
    public FileInfo updateOffset(String uploadUri, long currentOffset) {
        Optional<FileInfo> optional = fileInfoRepository.findByUploadUri(uploadUri);
        if (optional.isEmpty()) {
            log.warn("=== [PROGRESS] DB에 FileInfo 없음: {} ===", uploadUri);
            return null;
        }

        FileInfo fileInfo = optional.get();
        fileInfo.updateOffset(currentOffset);
        return fileInfoRepository.save(fileInfo);
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
}
