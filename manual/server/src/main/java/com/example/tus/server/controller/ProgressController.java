package com.example.tus.server.controller;

import com.example.tus.server.entity.FileInfo;
import com.example.tus.server.service.TusUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 업로드 진행률 조회 컨트롤러.
 * <p>
 * TUS 프로토콜 외부에서 업로드 진행 상황을 확인할 수 있는 REST API입니다.
 * 프론트엔드 UI에서 진행률 바를 표시하거나, 모니터링 대시보드에서
 * 업로드 상태를 확인할 때 사용합니다.
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final TusUploadService tusUploadService;

    /**
     * 특정 업로드의 진행률을 조회합니다.
     * <p>
     * 응답 JSON:
     * {
     *   "fileId": "uuid",
     *   "fileName": "test.pdf",
     *   "offset": 5242880,
     *   "totalSize": 10485760,
     *   "percent": 50,
     *   "status": "uploading"
     * }
     * </p>
     *
     * @param fileId 업로드 세션 ID
     * @return 업로드 진행률 정보 JSON
     */
    @GetMapping("/{fileId}")
    public ResponseEntity<Map<String, Object>> getProgress(@PathVariable String fileId) {
        log.debug("=== [PROGRESS] 진행률 조회 fileId={} ===", fileId);

        try {
            FileInfo fileInfo = tusUploadService.getFileInfo(fileId);

            // 진행률 계산 (0으로 나누기 방지)
            long percent = 0;
            if (fileInfo.getTotalSize() > 0) {
                percent = (fileInfo.getOffset() * 100) / fileInfo.getTotalSize();
            }

            Map<String, Object> result = new HashMap<>();
            result.put("fileId", fileInfo.getFileId());
            result.put("fileName", fileInfo.getFileName());
            result.put("offset", fileInfo.getOffset());
            result.put("totalSize", fileInfo.getTotalSize());
            result.put("percent", percent);
            result.put("status", fileInfo.getStatus());

            log.debug("=== [PROGRESS] fileId={}, {}% ({}/{}) status={} ===",
                    fileId, percent, fileInfo.getOffset(), fileInfo.getTotalSize(), fileInfo.getStatus());

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.warn("=== [PROGRESS] 파일 정보 없음 fileId={} ===", fileId);
            return ResponseEntity.notFound().build();
        }
    }
}
