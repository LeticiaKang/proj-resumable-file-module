package com.example.tus.server.controller;

import com.example.tus.server.entity.FileInfo;
import com.example.tus.server.service.TusUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 파일 다운로드 컨트롤러.
 * <p>
 * TUS 프로토콜로 업로드가 완료된 파일을 다운로드할 수 있는 REST API입니다.
 * 업로드 상태가 "completed"인 파일만 다운로드를 허용합니다.
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class DownloadController {

    private final TusUploadService tusUploadService;

    /**
     * 업로드 완료된 파일을 다운로드합니다.
     * <p>
     * 파일이 존재하고 상태가 "completed"인 경우에만 다운로드를 허용합니다.
     * Content-Disposition 헤더를 설정하여 브라우저에서 파일 다운로드 대화상자가 표시됩니다.
     * </p>
     *
     * @param fileId 업로드 세션 ID
     * @return 파일 스트리밍 응답
     */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileId) {
        log.info("=== [DOWNLOAD] 파일 다운로드 요청 fileId={} ===", fileId);

        try {
            FileInfo fileInfo = tusUploadService.getFileInfo(fileId);

            // 업로드 완료 상태 확인
            if (!"completed".equals(fileInfo.getStatus())) {
                log.warn("=== [DOWNLOAD] 업로드 미완료 파일 다운로드 시도 fileId={}, status={} ===",
                        fileId, fileInfo.getStatus());
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }

            // 디스크 파일 존재 확인
            Path filePath = tusUploadService.getFilePath(fileId);
            if (!Files.exists(filePath)) {
                log.error("=== [DOWNLOAD] 디스크에 파일 없음 fileId={}, path={} ===",
                        fileId, filePath);
                return ResponseEntity.notFound().build();
            }

            // 파일 리소스 생성 및 응답 헤더 설정
            Resource resource = new FileSystemResource(filePath);

            // 파일명 URL 인코딩 (한글 파일명 지원)
            String encodedFileName = URLEncoder.encode(fileInfo.getFileName(), StandardCharsets.UTF_8)
                    .replace("+", "%20");

            log.info("=== [DOWNLOAD] 파일 다운로드 fileId={}, fileName={} ===",
                    fileId, fileInfo.getFileName());

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(fileInfo.getTotalSize())
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + encodedFileName + "\"; " +
                                    "filename*=UTF-8''" + encodedFileName)
                    .body(resource);

        } catch (IllegalArgumentException e) {
            log.warn("=== [DOWNLOAD] 파일 정보 없음 fileId={} ===", fileId);
            return ResponseEntity.notFound().build();
        }
    }
}
