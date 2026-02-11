package com.example.tus.server.controller;

import com.example.tus.server.config.TusServerProperties;
import com.example.tus.server.config.ValidationProperties;
import com.example.tus.server.entity.FileInfo;
import com.example.tus.server.service.CallbackService;
import com.example.tus.server.service.ChecksumService;
import com.example.tus.server.service.TusUploadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

/**
 * TUS 프로토콜 핵심 컨트롤러.
 * <p>
 * TUS(Tus Upload Server) 프로토콜 1.0.0의 5가지 핵심 HTTP 엔드포인트를 구현합니다.
 * 각 엔드포인트는 TUS 프로토콜 사양에 따라 특정 헤더를 요구하고 반환합니다.
 * </p>
 *
 * <h3>TUS 프로토콜 요약:</h3>
 * <ul>
 *   <li><b>OPTIONS /files</b> - 서버가 지원하는 TUS 기능(버전, 확장, 최대크기)을 조회</li>
 *   <li><b>POST /files</b> - 새 업로드 세션 생성 (Upload-Length, Upload-Metadata 헤더 필요)</li>
 *   <li><b>HEAD /files/{id}</b> - 현재 업로드 오프셋 확인 (재개 시 사용)</li>
 *   <li><b>PATCH /files/{id}</b> - 청크 데이터 전송 (Content-Type: application/offset+octet-stream)</li>
 *   <li><b>DELETE /files/{id}</b> - 업로드 취소 및 리소스 삭제</li>
 * </ul>
 *
 * <p>모든 응답에는 <code>Tus-Resumable: 1.0.0</code> 헤더가 포함됩니다.</p>
 */
@Slf4j
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class TusProtocolController {

    /** TUS 프로토콜 버전 */
    private static final String TUS_RESUMABLE = "1.0.0";

    /** TUS 서버가 지원하는 확장 목록 */
    private static final String TUS_EXTENSIONS = "creation,termination,checksum";

    private final TusUploadService tusUploadService;
    private final ChecksumService checksumService;
    private final CallbackService callbackService;
    private final TusServerProperties tusProperties;
    private final ValidationProperties validationProperties;

    // ==================== OPTIONS /files ====================

    /**
     * TUS OPTIONS 엔드포인트 - 서버 기능 조회.
     * <p>
     * 클라이언트가 업로드 전에 서버의 TUS 지원 여부와 기능을 확인합니다.
     * 반환 헤더:
     * - Tus-Resumable: 프로토콜 버전
     * - Tus-Version: 지원 버전 목록
     * - Tus-Max-Size: 최대 업로드 크기
     * - Tus-Extension: 지원하는 확장 기능
     * </p>
     *
     * @return 204 No Content + TUS 헤더
     */
    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        log.info("=== [TUS OPTIONS] 서버 기능 조회 요청 ===");

        return ResponseEntity.noContent()
                .header("Tus-Resumable", TUS_RESUMABLE)
                .header("Tus-Version", TUS_RESUMABLE)
                .header("Tus-Max-Size", String.valueOf(tusProperties.getMaxUploadSize()))
                .header("Tus-Extension", TUS_EXTENSIONS)
                .build();
    }

    // ==================== POST /files ====================

    /**
     * TUS POST 엔드포인트 - 업로드 세션 생성.
     * <p>
     * 클라이언트가 새 파일 업로드를 시작할 때 호출합니다.
     * 필수 헤더:
     * - Upload-Length: 업로드할 파일의 전체 크기 (바이트)
     * - Upload-Metadata: 파일 메타데이터 (Base64 인코딩된 filename, checksum 등)
     * </p>
     * <p>
     * Upload-Metadata 형식: "filename dGVzdC50eHQ=,checksum YWJjMTIz"
     * (key와 Base64 인코딩된 value가 공백으로 구분, 쌍은 쉼표로 구분)
     * </p>
     *
     * @param uploadLength   파일 전체 크기 (Upload-Length 헤더)
     * @param uploadMetadata 파일 메타데이터 (Upload-Metadata 헤더)
     * @return 201 Created + Location 헤더 (업로드 URL)
     */
    @PostMapping
    public ResponseEntity<String> createUpload(
            @RequestHeader("Upload-Length") long uploadLength,
            @RequestHeader(value = "Upload-Metadata", required = false) String uploadMetadata,
            HttpServletRequest request) {

        log.info("=== [TUS POST] 세션 생성 요청 Upload-Length={}, Upload-Metadata={} ===",
                uploadLength, uploadMetadata);

        // === 파일 크기 검증 ===
        if (uploadLength > tusProperties.getMaxUploadSize()) {
            log.warn("=== [TUS POST] 파일 크기 초과! requested={}, max={} ===",
                    uploadLength, tusProperties.getMaxUploadSize());
            return ResponseEntity.status(HttpStatus.REQUEST_ENTITY_TOO_LARGE)
                    .header("Tus-Resumable", TUS_RESUMABLE)
                    .body("파일 크기가 최대 허용 크기를 초과합니다.");
        }

        // === 파일 확장자 검증 ===
        if (uploadMetadata != null) {
            Map<String, String> meta = tusUploadService.parseMetadata(uploadMetadata);
            String fileName = meta.getOrDefault("filename", "");
            if (!fileName.isEmpty() && !validationProperties.getAllowedExtensions().isEmpty()) {
                boolean extensionAllowed = validationProperties.getAllowedExtensions().stream()
                        .anyMatch(ext -> fileName.toLowerCase().endsWith(ext.toLowerCase()));
                if (!extensionAllowed) {
                    log.warn("=== [TUS POST] 허용되지 않는 확장자! fileName={}, allowed={} ===",
                            fileName, validationProperties.getAllowedExtensions());
                    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                            .header("Tus-Resumable", TUS_RESUMABLE)
                            .body("허용되지 않는 파일 확장자입니다: " + fileName);
                }
            }
        }

        try {
            FileInfo fileInfo = tusUploadService.createUpload(uploadLength, uploadMetadata);

            // Location 헤더: 이후 HEAD/PATCH/DELETE 요청에 사용할 URL
            String location = request.getRequestURL().toString() + "/" + fileInfo.getFileId();

            log.info("=== [TUS POST] 세션 생성 fileId={}, fileName={}, totalSize={} ===",
                    fileInfo.getFileId(), fileInfo.getFileName(), fileInfo.getTotalSize());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .header("Tus-Resumable", TUS_RESUMABLE)
                    .header("Location", location)
                    .header("Upload-Offset", "0")
                    .build();

        } catch (IOException e) {
            log.error("=== [TUS POST] 세션 생성 실패: {} ===", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Tus-Resumable", TUS_RESUMABLE)
                    .body("업로드 세션 생성 실패: " + e.getMessage());
        }
    }

    // ==================== HEAD /files/{fileId} ====================

    /**
     * TUS HEAD 엔드포인트 - 현재 업로드 오프셋 확인.
     * <p>
     * 클라이언트가 업로드 재개 시 현재 오프셋을 확인할 때 사용합니다.
     * 반환 헤더:
     * - Upload-Offset: 현재까지 서버가 수신한 바이트 수
     * - Upload-Length: 파일 전체 크기
     * </p>
     * <p>
     * 클라이언트는 이 응답의 Upload-Offset 값부터 청크를 이어서 전송합니다.
     * </p>
     *
     * @param fileId 업로드 세션 ID
     * @return 200 OK + Upload-Offset, Upload-Length 헤더
     */
    @RequestMapping(value = "/{fileId}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headUpload(@PathVariable String fileId) {
        log.info("=== [TUS HEAD] 오프셋 확인 요청 fileId={} ===", fileId);

        try {
            FileInfo fileInfo = tusUploadService.getFileInfo(fileId);

            log.info("=== [TUS HEAD] 오프셋 확인 fileId={}, offset={}/{} ===",
                    fileId, fileInfo.getOffset(), fileInfo.getTotalSize());

            return ResponseEntity.ok()
                    .header("Tus-Resumable", TUS_RESUMABLE)
                    .header("Upload-Offset", String.valueOf(fileInfo.getOffset()))
                    .header("Upload-Length", String.valueOf(fileInfo.getTotalSize()))
                    .header("Cache-Control", "no-store")
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("=== [TUS HEAD] 파일 정보 없음 fileId={} ===", fileId);
            return ResponseEntity.notFound()
                    .header("Tus-Resumable", TUS_RESUMABLE)
                    .build();
        }
    }

    // ==================== PATCH /files/{fileId} ====================

    /**
     * TUS PATCH 엔드포인트 - 청크 데이터 수신.
     * <p>
     * 클라이언트가 파일 데이터를 청크 단위로 전송할 때 사용합니다.
     * 필수 조건:
     * - Content-Type: application/offset+octet-stream
     * - Upload-Offset: 클라이언트가 전송하는 데이터의 시작 위치
     * </p>
     * <p>
     * 서버는 클라이언트의 Upload-Offset과 서버 DB의 offset이 일치하는지 검증하며,
     * 불일치 시 409 Conflict를 반환합니다. 이는 TUS 프로토콜의 핵심 안전장치입니다.
     * </p>
     *
     * @param fileId      업로드 세션 ID
     * @param contentType Content-Type 헤더 (application/offset+octet-stream이어야 함)
     * @param uploadOffset 클라이언트의 현재 오프셋 (Upload-Offset 헤더)
     * @param request     HTTP 요청 (청크 데이터는 request body에 포함)
     * @return 204 No Content + 갱신된 Upload-Offset 헤더
     */
    @PatchMapping("/{fileId}")
    public ResponseEntity<String> patchUpload(
            @PathVariable String fileId,
            @RequestHeader("Content-Type") String contentType,
            @RequestHeader("Upload-Offset") long uploadOffset,
            HttpServletRequest request) {

        log.info("=== [TUS PATCH] 청크 수신 요청 fileId={}, clientOffset={}, Content-Type={} ===",
                fileId, uploadOffset, contentType);

        // === Content-Type 검증 ===
        // TUS 프로토콜은 PATCH 요청의 Content-Type이 반드시
        // "application/offset+octet-stream"이어야 합니다.
        if (!"application/offset+octet-stream".equals(contentType)) {
            log.warn("=== [TUS PATCH] 잘못된 Content-Type: {} ===", contentType);
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .header("Tus-Resumable", TUS_RESUMABLE)
                    .body("Content-Type은 application/offset+octet-stream이어야 합니다.");
        }

        // === 디버그 장애 시뮬레이션 ===
        // 발표 시연용: DebugController에서 설정한 플래그에 따라 의도적으로 실패 응답
        if (DebugController.shouldFailNextPatch()) {
            log.warn("=== [TUS PATCH] 장애 시뮬레이션! fileId={} 에 대해 500 응답 반환 ===", fileId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Tus-Resumable", TUS_RESUMABLE)
                    .body("시뮬레이션: 서버 장애 발생!");
        }

        try {
            FileInfo fileInfo = tusUploadService.receiveChunk(fileId, uploadOffset, request.getInputStream());

            long previousOffset = uploadOffset;
            log.info("=== [TUS PATCH] 청크 수신 fileId={}, offset={}->{}/{} ===",
                    fileId, previousOffset, fileInfo.getOffset(), fileInfo.getTotalSize());

            // === 업로드 완료 처리 ===
            if ("completed".equals(fileInfo.getStatus())) {
                log.info("=== [TUS PATCH] 업로드 완료! fileId={} ===", fileId);

                // 체크섬 검증 (클라이언트가 체크섬을 제공한 경우)
                checksumService.verifyChecksum(fileInfo);

                // 완료 콜백 전송 (콜백이 활성화된 경우)
                callbackService.notifyCompletion(fileInfo);
            }

            return ResponseEntity.noContent()
                    .header("Tus-Resumable", TUS_RESUMABLE)
                    .header("Upload-Offset", String.valueOf(fileInfo.getOffset()))
                    .build();

        } catch (IllegalStateException e) {
            // 오프셋 불일치 → 409 Conflict
            // 클라이언트는 HEAD 요청으로 올바른 오프셋을 다시 확인해야 합니다.
            log.warn("=== [TUS PATCH] 오프셋 충돌 fileId={}: {} ===", fileId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .header("Tus-Resumable", TUS_RESUMABLE)
                    .body(e.getMessage());

        } catch (IllegalArgumentException e) {
            log.warn("=== [TUS PATCH] 파일 정보 없음 fileId={} ===", fileId);
            return ResponseEntity.notFound()
                    .header("Tus-Resumable", TUS_RESUMABLE)
                    .build();

        } catch (IOException e) {
            log.error("=== [TUS PATCH] 청크 수신 실패 fileId={}: {} ===", fileId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Tus-Resumable", TUS_RESUMABLE)
                    .body("청크 수신 중 오류 발생: " + e.getMessage());
        }
    }

    // ==================== DELETE /files/{fileId} ====================

    /**
     * TUS DELETE 엔드포인트 - 업로드 취소/삭제.
     * <p>
     * TUS termination 확장을 구현합니다.
     * 클라이언트가 업로드를 취소하거나, 완료된 업로드를 삭제할 때 사용합니다.
     * 디스크의 파일과 DB 레코드가 모두 삭제됩니다.
     * </p>
     *
     * @param fileId 업로드 세션 ID
     * @return 204 No Content
     */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<String> deleteUpload(@PathVariable String fileId) {
        log.info("=== [TUS DELETE] 업로드 삭제 요청 fileId={} ===", fileId);

        try {
            tusUploadService.deleteUpload(fileId);
            log.info("=== [TUS DELETE] 업로드 삭제 fileId={} ===", fileId);

            return ResponseEntity.noContent()
                    .header("Tus-Resumable", TUS_RESUMABLE)
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("=== [TUS DELETE] 파일 정보 없음 fileId={} ===", fileId);
            return ResponseEntity.notFound()
                    .header("Tus-Resumable", TUS_RESUMABLE)
                    .build();

        } catch (IOException e) {
            log.error("=== [TUS DELETE] 삭제 실패 fileId={}: {} ===", fileId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Tus-Resumable", TUS_RESUMABLE)
                    .body("삭제 중 오류 발생: " + e.getMessage());
        }
    }
}
