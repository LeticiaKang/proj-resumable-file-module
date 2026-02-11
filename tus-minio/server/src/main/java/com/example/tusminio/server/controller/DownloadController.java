package com.example.tusminio.server.controller;

import com.example.tusminio.server.config.MinioProperties;
import com.example.tusminio.server.entity.FileInfo;
import com.example.tusminio.server.repository.FileInfoRepository;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.desair.tus.server.TusFileUploadService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 파일 다운로드 컨트롤러.
 * <p>
 * 업로드 완료된 파일을 다운로드할 수 있는 엔드포인트를 제공합니다.
 * 파일이 MinIO로 전송된 경우 MinIO presigned URL로 리다이렉트하고,
 * 아직 로컬에만 있는 경우 직접 스트리밍합니다.
 * </p>
 *
 * <h3>다운로드 소스 결정 로직:</h3>
 * <ul>
 *   <li>status="transferred" → MinIO presigned URL 생성 후 리다이렉트</li>
 *   <li>status="completed" → 로컬 파일에서 직접 스트리밍 (MinIO 전송 전)</li>
 *   <li>기타 상태 → 다운로드 불가 (아직 업로드 중이거나 실패)</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class DownloadController {

    private final FileInfoRepository fileInfoRepository;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final TusFileUploadService tusFileUploadService;

    /** MinIO presigned URL 만료 시간 (1시간) */
    private static final int PRESIGNED_URL_EXPIRY_HOURS = 1;

    /**
     * 업로드된 파일을 다운로드합니다.
     * <p>
     * MinIO에 전송 완료된 파일은 presigned URL로 리다이렉트하고,
     * 로컬에만 있는 파일은 tus-java-server의 getUploadedBytes()로 직접 스트리밍합니다.
     * </p>
     *
     * @param uploadUri TUS 업로드 URI (URL 인코딩된 상태로 전달 가능)
     * @return 파일 스트림 또는 MinIO presigned URL 리다이렉트
     */
    @GetMapping("/{uploadUri}/download")
    public ResponseEntity<?> download(@PathVariable String uploadUri) {
        // URL 디코딩 (경로 변수에 특수 문자가 포함될 수 있음)
        String decodedUri = URLDecoder.decode(uploadUri, StandardCharsets.UTF_8);

        // uploadUri가 /files/로 시작하지 않으면 접두사 추가
        if (!decodedUri.startsWith("/files/")) {
            decodedUri = "/files/" + decodedUri;
        }

        // DB에서 파일 정보 조회
        FileInfo fileInfo = fileInfoRepository.findByUploadUri(decodedUri).orElse(null);
        if (fileInfo == null) {
            log.warn("=== [DOWNLOAD] 파일 정보 없음: {} ===", decodedUri);
            return ResponseEntity.notFound().build();
        }

        String status = fileInfo.getStatus();

        // === MinIO에 전송 완료된 파일: presigned URL로 리다이렉트 ===
        if ("transferred".equals(status)) {
            return handleMinioDownload(fileInfo);
        }

        // === 로컬에만 있는 완료 파일: 직접 스트리밍 ===
        if ("completed".equals(status)) {
            return handleLocalDownload(decodedUri, fileInfo);
        }

        // === 아직 업로드 중이거나 실패 상태 ===
        log.warn("=== [DOWNLOAD] 다운로드 불가 상태: fileId={}, status={} ===",
                fileInfo.getId(), status);
        return ResponseEntity.badRequest().body(
                "{\"error\":\"다운로드 불가 상태입니다: " + status + "\"}"
        );
    }

    /**
     * MinIO에서 presigned URL을 생성하여 리다이렉트합니다.
     * presigned URL은 1시간 동안 유효합니다.
     */
    private ResponseEntity<?> handleMinioDownload(FileInfo fileInfo) {
        try {
            // MinIO presigned URL 생성 (GET 메서드, 1시간 유효)
            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioProperties.getBucket())
                            .object(fileInfo.getMinioObjectKey())
                            .expiry(PRESIGNED_URL_EXPIRY_HOURS, TimeUnit.HOURS)
                            .build()
            );

            log.info("=== [DOWNLOAD] fileId={}, source=minio ===", fileInfo.getId());

            // 302 리다이렉트로 presigned URL 반환
            return ResponseEntity
                    .status(302)
                    .header(HttpHeaders.LOCATION, presignedUrl)
                    .build();

        } catch (Exception e) {
            log.error("=== [DOWNLOAD] MinIO presigned URL 생성 실패: {} ===", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    "{\"error\":\"MinIO 다운로드 URL 생성 실패\"}"
            );
        }
    }

    /**
     * 로컬 저장소에서 tus-java-server의 getUploadedBytes()로 파일을 스트리밍합니다.
     * MinIO로 전송되기 전인 "completed" 상태의 파일에 사용됩니다.
     */
    private ResponseEntity<?> handleLocalDownload(String uploadUri, FileInfo fileInfo) {
        try {
            // tus-java-server에서 업로드된 파일의 InputStream 획득
            InputStream inputStream = tusFileUploadService.getUploadedBytes(uploadUri);
            if (inputStream == null) {
                log.warn("=== [DOWNLOAD] 로컬 파일을 찾을 수 없음: {} ===", uploadUri);
                return ResponseEntity.notFound().build();
            }

            log.info("=== [DOWNLOAD] fileId={}, source=local ===", fileInfo.getId());

            // 파일 스트리밍 응답
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileInfo.getFileName() + "\"")
                    .contentLength(fileInfo.getTotalSize())
                    .body(new InputStreamResource(inputStream));

        } catch (Exception e) {
            log.error("=== [DOWNLOAD] 로컬 파일 스트리밍 실패: {} ===", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    "{\"error\":\"로컬 파일 다운로드 실패\"}"
            );
        }
    }
}
