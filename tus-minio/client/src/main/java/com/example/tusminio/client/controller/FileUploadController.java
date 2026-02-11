package com.example.tusminio.client.controller;

import com.example.tusminio.client.dto.BatchUploadRequest;
import com.example.tusminio.client.dto.BatchUploadResponse;
import com.example.tusminio.client.dto.UploadRequest;
import com.example.tusminio.client.dto.UploadResponse;
import com.example.tusminio.client.service.BatchUploadService;
import com.example.tusminio.client.service.TusClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 엔드포인트 목록:
 * - POST /api/tus/upload       : 단일 파일 업로드
 * - POST /api/tus/upload/batch : 다중 파일 배치 업로드
 * - POST /api/tus/resume/{filePath} : 파일 이어받기 업로드
 * - GET  /api/tus/progress/{fileId} : 업로드 진행 상황 조회 (서버 프록시)
 */
@Slf4j
@RestController
@RequestMapping("/api/tus")
@RequiredArgsConstructor
public class FileUploadController {

    private final TusClientService tusClientService;
    private final BatchUploadService batchUploadService;
    private final WebClient.Builder webClientBuilder;

    /**
     * 단일 파일 업로드
     *
     * tus-java-client의 resumeOrCreateUpload()를 사용하므로,
     * DB에 이전 업로드 URL이 있으면 자동으로 이어받기를 시도한다.
     *
     * @param request 업로드 요청 (filePath 필수)
     * @return 업로드 결과 (fileId, fileName, status 등)
     */
    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> uploadFile(@Valid @RequestBody UploadRequest request) {

        log.info("=== [API] POST /api/tus/upload - filePath={} ===", request.getFilePath());

        UploadResponse response = tusClientService.uploadFile(request.getFilePath());
        return ResponseEntity.ok(response);
    }

    /**
     * 다중 파일 배치 업로드
     *
     * 여러 파일을 동시에 업로드한다.
     * Semaphore로 동시 업로드 수를 제한하고, CompletableFuture로 비동기 처리한다.
     *
     * @param request 배치 업로드 요청 (filePaths 목록 필수)
     * @return 전체 업로드 결과 (각 파일별 결과 + 성공/실패 집계)
     */
    @PostMapping("/upload/batch")
    public ResponseEntity<BatchUploadResponse> uploadBatch(
            @Valid @RequestBody BatchUploadRequest request) {

        log.info("=== [API] POST /api/tus/upload/batch - {} 파일 ===", request.getFilePaths().size());

        BatchUploadResponse response = batchUploadService.uploadBatch(request.getFilePaths());
        return ResponseEntity.ok(response);
    }

    /**
     * 파일 이어받기 업로드
     *
     * 이전에 중단된 업로드를 이어서 진행한다.
     * tus-java-client가 TusURLDatabaseStore에서 이전 업로드 URL을 조회하고,
     * HEAD 요청으로 서버의 현재 offset을 확인한 뒤 해당 위치부터 전송한다.
     *
     * 이전 업로드 URL이 DB에 없으면 새 업로드를 생성한다.
     *
     * @param filePath 이어받기할 파일의 경로 (URL 인코딩된 경로)
     * @return 업로드 결과
     */
    @PostMapping("/resume/{filePath}")
    public ResponseEntity<UploadResponse> resumeUpload(
            @PathVariable String filePath) {

        log.info("=== [API] POST /api/tus/resume/{} ===", filePath);

        UploadResponse response = tusClientService.resumeUpload(filePath);
        return ResponseEntity.ok(response);
    }

    /**
     * 업로드 진행 상황 조회 (TUS 서버 프록시)
     *
     * TUS 서버(localhost:8086)의 진행 상황 API를 프록시하여 반환한다.
     * 클라이언트가 서버에 직접 접근하지 않고 이 엔드포인트를 통해 확인할 수 있다.
     *
     * @param fileId TUS 서버가 발급한 파일 ID
     * @return 서버의 진행 상황 응답 (JSON 문자열)
     */
    @GetMapping("/progress/{fileId}")
    public ResponseEntity<String> getUploadProgress(@PathVariable String fileId) {
        log.info("=== [API] GET /api/tus/progress/{} ===", fileId);

        try {
            // TUS 서버의 진행 상황 API를 프록시 호출
            String progressResponse = webClientBuilder.build()
                    .get()
                    .uri("http://localhost:8086/api/progress/{fileId}", fileId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return ResponseEntity.ok(progressResponse);
        } catch (Exception e) {
            log.error("=== [API] 진행 상황 조회 실패: {} ===", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("{\"error\": \"진행 상황 조회 실패: " + e.getMessage() + "\"}");
        }
    }
}
