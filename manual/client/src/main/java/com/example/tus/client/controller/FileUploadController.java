package com.example.tus.client.controller;

import com.example.tus.client.config.TusClientProperties;
import com.example.tus.client.dto.BatchUploadRequest;
import com.example.tus.client.dto.BatchUploadResponse;
import com.example.tus.client.dto.UploadRequest;
import com.example.tus.client.dto.UploadResponse;
import com.example.tus.client.service.BatchUploadService;
import com.example.tus.client.service.TusClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * TUS 파일 업로드 REST 컨트롤러
 *
 * <p>클라이언트 측 API 엔드포인트를 제공합니다.
 * 외부 시스템이나 프론트엔드에서 이 API를 호출하면,
 * 내부적으로 TUS 프로토콜을 사용하여 파일을 서버에 업로드합니다.</p>
 *
 * <h3>제공하는 API 목록:</h3>
 * <table>
 *   <tr><th>메서드</th><th>URL</th><th>설명</th></tr>
 *   <tr><td>POST</td><td>/api/tus/upload</td><td>단일 파일 업로드</td></tr>
 *   <tr><td>POST</td><td>/api/tus/upload/batch</td><td>다중 파일 동시 업로드</td></tr>
 *   <tr><td>POST</td><td>/api/tus/resume</td><td>중단된 업로드 이어받기</td></tr>
 *   <tr><td>GET</td><td>/api/tus/progress/{fileId}</td><td>업로드 진행률 조회 (서버 프록시)</td></tr>
 * </table>
 */
@Slf4j
@RestController
@RequestMapping("/api/tus")
@RequiredArgsConstructor
public class FileUploadController {

    private final TusClientService tusClientService;
    private final BatchUploadService batchUploadService;
    private final WebClient webClient;
    private final TusClientProperties tusClientProperties;

    /**
     * 단일 파일 업로드 API
     *
     * <p>지정된 파일 경로의 파일을 TUS 프로토콜을 사용하여 서버에 업로드합니다.</p>
     *
     * <pre>
     * POST /api/tus/upload
     * Content-Type: application/json
     *
     * {
     *   "filePath": "C:/uploads/test-file.zip"
     * }
     *
     * → 응답:
     * {
     *   "fileId": "a1b2c3d4-...",
     *   "fileName": "test-file.zip",
     *   "totalSize": 15728640,
     *   "status": "COMPLETED",
     *   "message": "업로드가 성공적으로 완료되었습니다.",
     *   "checksum": "e3b0c44..."
     * }
     * </pre>
     *
     * <p><b>내부 동작 흐름:</b></p>
     * <ol>
     *   <li>파일 SHA-256 체크섬 계산</li>
     *   <li>POST → 세션 생성 (fileId 발급)</li>
     *   <li>PATCH × N → 청크 단위 데이터 전송</li>
     *   <li>완료 결과 반환</li>
     * </ol>
     *
     * @param request 업로드할 파일 경로를 담은 요청 객체
     * @return UploadResponse 업로드 결과
     */
    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> uploadFile(@Valid @RequestBody UploadRequest request) {
        log.info("=== [API] POST /api/tus/upload - filePath={} ===", request.getFilePath());

        UploadResponse response = tusClientService.uploadFile(request.getFilePath());

        log.info("=== [API] 업로드 결과: fileId={}, status={} ===",
                response.getFileId(), response.getStatus());

        return ResponseEntity.ok(response);
    }

    /**
     * 배치(다중 파일) 업로드 API
     *
     * <p>여러 파일을 동시에 업로드합니다. 동시 업로드 수는
     * tus.upload.max-concurrent 설정으로 제한됩니다.</p>
     *
     * <pre>
     * POST /api/tus/upload/batch
     * Content-Type: application/json
     *
     * {
     *   "filePaths": [
     *     "C:/uploads/file1.zip",
     *     "C:/uploads/file2.pdf"
     *   ]
     * }
     *
     * → 응답:
     * {
     *   "results": [...],
     *   "successCount": 2,
     *   "failCount": 0
     * }
     * </pre>
     *
     * @param request 업로드할 파일 경로 목록을 담은 요청 객체
     * @return BatchUploadResponse 전체 업로드 결과 (성공/실패 집계 포함)
     */
    @PostMapping("/upload/batch")
    public ResponseEntity<BatchUploadResponse> uploadBatch(@Valid @RequestBody BatchUploadRequest request) {
        log.info("=== [API] POST /api/tus/upload/batch - files={} ===", request.getFilePaths().size());

        BatchUploadResponse response = batchUploadService.uploadFiles(request.getFilePaths());

        log.info("=== [API] 배치 업로드 결과: success={}, fail={} ===",
                response.getSuccessCount(), response.getFailCount());

        return ResponseEntity.ok(response);
    }

    /**
     * 업로드 이어받기(Resume) API
     *
     * <p>네트워크 중단 등으로 멈춘 업로드를 이어서 진행합니다.
     * 서버에 HEAD 요청을 보내 현재 오프셋을 확인하고,
     * 해당 지점부터 나머지 데이터를 전송합니다.</p>
     *
     * <pre>
     * POST /api/tus/resume
     * Content-Type: application/json
     *
     * {
     *   "fileId": "a1b2c3d4-...",
     *   "filePath": "C:/uploads/test-file.zip"
     * }
     *
     * → 응답:
     * {
     *   "fileId": "a1b2c3d4-...",
     *   "status": "RESUMED",
     *   "message": "이어받기가 성공적으로 완료되었습니다."
     * }
     * </pre>
     *
     * @param requestBody fileId와 filePath를 담은 Map
     * @return UploadResponse 이어받기 결과
     */
    @PostMapping("/resume")
    public ResponseEntity<UploadResponse> resumeUpload(@RequestBody Map<String, String> requestBody) {
        String fileId = requestBody.get("fileId");
        String filePath = requestBody.get("filePath");

        log.info("=== [API] POST /api/tus/resume - fileId={}, filePath={} ===", fileId, filePath);

        // 필수 파라미터 검증
        if (fileId == null || fileId.isBlank()) {
            log.warn("=== [API] resume 요청에 fileId가 누락되었습니다 ===");
            return ResponseEntity.badRequest().body(
                    UploadResponse.builder()
                            .status("FAILED")
                            .message("fileId는 필수입니다.")
                            .build());
        }
        if (filePath == null || filePath.isBlank()) {
            log.warn("=== [API] resume 요청에 filePath가 누락되었습니다 ===");
            return ResponseEntity.badRequest().body(
                    UploadResponse.builder()
                            .status("FAILED")
                            .message("filePath는 필수입니다.")
                            .build());
        }

        UploadResponse response = tusClientService.resumeUpload(fileId, filePath);

        log.info("=== [API] 이어받기 결과: fileId={}, status={} ===",
                response.getFileId(), response.getStatus());

        return ResponseEntity.ok(response);
    }

    /**
     * 업로드 진행률 조회 API (서버 프록시)
     *
     * <p>TUS 서버의 진행률 API를 프록시하여 클라이언트에 반환합니다.
     * 클라이언트가 직접 TUS 서버에 접근하지 않고, 이 API를 통해 진행률을 확인할 수 있습니다.</p>
     *
     * <pre>
     * GET /api/tus/progress/{fileId}
     *
     * → 내부적으로 GET http://localhost:8082/api/progress/{fileId} 호출
     * → 서버 응답을 그대로 클라이언트에 전달
     * </pre>
     *
     * <p><b>프록시 패턴을 사용하는 이유:</b></p>
     * <ul>
     *   <li>클라이언트가 TUS 서버의 내부 URL을 몰라도 됨</li>
     *   <li>CORS 문제 없이 단일 엔드포인트로 통합</li>
     *   <li>향후 인증/캐싱 등의 로직을 추가하기 용이</li>
     * </ul>
     *
     * @param fileId 진행률을 조회할 파일의 고유 식별자
     * @return 서버의 진행률 응답 (JSON)
     */
    @GetMapping("/progress/{fileId}")
    public ResponseEntity<String> getProgress(@PathVariable String fileId) {
        log.info("=== [API] GET /api/tus/progress/{} ===", fileId);

        try {
            // TUS 서버의 진행률 API URL 구성
            // serverUrl이 "http://localhost:8082/files"이면
            // progressUrl은 "http://localhost:8082/api/progress/{fileId}"가 됩니다.
            String serverBaseUrl = tusClientProperties.getServerUrl();
            // "/files" 부분을 제거하고 "/api/progress" 경로 추가
            String progressUrl = serverBaseUrl.substring(0, serverBaseUrl.lastIndexOf('/'))
                    + "/api/progress/" + fileId;

            log.debug("=== [PROGRESS PROXY] 서버 URL: {} ===", progressUrl);

            // === WebClient를 사용하여 서버에 GET 요청 프록시 ===
            String responseBody = WebClient.create()
                    .get()
                    .uri(progressUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("=== [PROGRESS PROXY] 응답: {} ===", responseBody);

            return ResponseEntity.ok(responseBody);

        } catch (Exception e) {
            log.error("=== [PROGRESS ERROR] fileId={}, error={} ===", fileId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("{\"error\": \"진행률 조회 실패: " + e.getMessage() + "\"}");
        }
    }
}
