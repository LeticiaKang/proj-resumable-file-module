package com.example.tusminio.client.service;

import com.example.tusminio.client.config.RetryProperties;
import com.example.tusminio.client.config.TusClientProperties;
import com.example.tusminio.client.dto.UploadResponse;
import com.example.tusminio.client.store.TusURLDatabaseStore;
import com.example.tusminio.client.util.ChecksumCalculator;
import io.tus.java.client.ProtocolException;
import io.tus.java.client.TusClient;
import io.tus.java.client.TusExecutor;
import io.tus.java.client.TusUpload;
import io.tus.java.client.TusUploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import java.util.HashMap;
import java.util.Map;

/**
 * 1. TusClient 생성 및 설정
 *    - setUploadCreationURL(): TUS 서버의 업로드 엔드포인트 설정
 *    - enableResuming(TusURLStore): 이어받기 활성화 + URL 저장소 연결
 *
 * 2. TusUpload 생성
 *    - new TusUpload(File): 파일 경로, 크기, 입력 스트림을 자동 설정
 *    - setFingerprint(): 이어받기 판별을 위한 고유 식별자 (라이브러리가 자동 생성)
 *
 * 3. TusUploader 생성 (client.resumeOrCreateUpload)
 *    - 내부적으로 TusURLStore.get(fingerprint)을 호출하여 이전 업로드 URL 조회
 *    - URL이 있으면: HEAD 요청 → Upload-Offset 확인 → 해당 위치부터 이어서 업로드
 *    - URL이 없으면: POST 요청 → 새 업로드 생성 → Location 헤더에서 URL 수신
 *
 * 4. 청크 업로드 루프
 *    - uploader.uploadChunk(): chunkSize만큼 PATCH 요청으로 전송
 *    - 반환값 -1이면 전송 완료
 *
 * 5. 업로드 완료
 *    - uploader.finish(): 연결 종료 및 리소스 정리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TusClientService {

    private final TusClientProperties tusClientProperties;
    private final RetryProperties retryProperties;
    private final TusURLDatabaseStore tusURLDatabaseStore;

    // 공통 검증 메서드
    private File validateAndGetFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("파일이 존재하지 않습니다: " + filePath);
        }
        return file;
    }

    /**
     * 파일 업로드 수행 (신규 또는 자동 이어받기)
     */
    public UploadResponse uploadFile(String filePath) {
        try {
            File file = validateAndGetFile(filePath);
            return executeWithRetry(file, false);
        } catch (IllegalArgumentException e) {
            return UploadResponse.builder()
                    .fileName(new File(filePath).getName())
                    .status("FAILED")
                    .message(e.getMessage())
                    .build();
        }
    }

    /**
     * 파일 이어받기 업로드 수행
     */
    public UploadResponse resumeUpload(String filePath) {
        try {
            File file = validateAndGetFile(filePath);
            return executeWithRetry(file, true);
        } catch (IllegalArgumentException e) {
            return UploadResponse.builder()
                    .fileName(new File(filePath).getName())
                    .status("FAILED")
                    .message(e.getMessage())
                    .build();
        }
    }

    /**
     * 재시도 로직을 적용한 업로드 실행
     *
     * @param file     업로드할 파일
     * @param isResume 이어받기 요청 여부 (로그 구분용)
     * @return 업로드 결과 정보
     */
    private UploadResponse executeWithRetry(File file, boolean isResume) {
        int attempt = 0;
        long delay = retryProperties.getInitialDelayMs();

        while (attempt < retryProperties.getMaxAttempts()) {
            attempt++;
            try {
                log.info("=== [ATTEMPT {}/{}] {} ===", attempt, retryProperties.getMaxAttempts(), file.getName());

                return doUpload(file, isResume);

            } catch (Exception e) {
                log.error("=== [ATTEMPT {}/{} FAILED] {} - {} ===", attempt, retryProperties.getMaxAttempts(), file.getName(), e.getMessage());

                if (attempt >= retryProperties.getMaxAttempts()) {
                    log.error("=== [UPLOAD FAILED] 최대 재시도 횟수 초과: {} ===", file.getName());
                    return UploadResponse.builder()
                            .fileName(file.getName())
                            .totalSize(file.length())
                            .status("FAILED")
                            .message("최대 재시도 횟수 초과: " + e.getMessage())
                            .build();
                }

                // 지수 백오프 대기
                try {
                    log.info("=== [RETRY WAIT] {}ms 후 재시도 ===", delay);
                    Thread.sleep(delay);
                    delay = Math.min( (long) (delay * retryProperties.getMultiplier()), retryProperties.getMaxDelayMs());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return UploadResponse.builder()
                            .fileName(file.getName())
                            .status("FAILED")
                            .message("업로드 중단됨 (인터럽트)")
                            .build();
                }
            }
        }

        // 이 코드에 도달하면 안 되지만 안전을 위해
        return UploadResponse.builder()
                .fileName(file.getName())
                .status("FAILED")
                .message("알 수 없는 오류")
                .build();
    }

    /**
     * 실제 TUS 업로드 수행
     * TusClient 생성 → TusUpload 생성 → resumeOrCreateUpload → uploadChunk 루프 → finish
     *
     * @param file     업로드할 파일
     * @param isResume 이어받기 여부 (로그용)
     * @return 업로드 결과
     * @throws Exception 업로드 중 발생한 예외
     */
    private UploadResponse doUpload(File file, boolean isResume) throws Exception {
        // 1. SHA-256 체크섬 계산
        String checksum;
        try {
            checksum = ChecksumCalculator.calculateSha256(file.getAbsolutePath());
            log.debug("=== [체크썸] {} → {} ===", file.getName(), checksum);
        } catch (IOException e) {
            throw new RuntimeException("체크섬 계산 실패: " + e.getMessage(), e);
        }

        // 2. TusClient 생성 및 설정
        TusClient client = new TusClient();
        client.setUploadCreationURL(new URL(tusClientProperties.getServerUrl())); // TUS 서버의 업로드 생성 엔드포인트 URL
        client.enableResuming(tusURLDatabaseStore); // DB 기반 URL 저장소를 연결하여 이어받기

        log.debug("=== [TUS CLIENT] serverUrl={}, chunkSize={} ===", tusClientProperties.getServerUrl(), tusClientProperties.getChunkSize());

        // 3. TusUpload 객체 생성
        TusUpload upload = new TusUpload(file); 
        // File 객체로부터 파일 크기, 입력 스트림을 자동 설정
        // fingerprint는 라이브러리가 파일 경로 + 크기 기반으로 자동 생성

        // 4. 메타데이터 설정 (서버에서 파일 정보 확인용)
        Map<String, String> metadata = new HashMap<>();
        metadata.put("filename", file.getName());
        metadata.put("checksum", checksum);
        upload.setMetadata(metadata);

        log.info("=== [TUS UPLOAD] file={}, size={} bytes, fingerprint={} ===", file.getName(), upload.getSize(), upload.getFingerprint());

        // 5. 업로드 생성 또는 이어받기
        //    resumeOrCreateUpload() 내부 동작:
        //    (1) TusURLStore.get(fingerprint)으로 이전 URL 조회
        //    (2) URL 있으면 → HEAD 요청으로 서버의 Upload-Offset 확인 → 해당 위치부터 이어서 전송
        //    (3) URL 없으면 → POST 요청으로 새 업로드 생성 → Location 헤더에서 URL 획득
        TusUploader uploader = client.resumeOrCreateUpload(upload);
        uploader.setChunkSize(tusClientProperties.getChunkSize());

        if (isResume) {
            log.info("=== [RESUME] 이전 업로드 이어받기 시도 완료 ===");
        }

        // 6. 청크 업로드 루프
        long totalBytes = upload.getSize();
        int chunkResult;

        do {
            chunkResult = uploader.uploadChunk();
            // chunckSize만큼 데이터를 PATCH로 서버에 전송함. >> 파일을 다 읽으면 -1 반환하여 업로드 완료

            long uploadedBytes = uploader.getOffset();
            double progress = (totalBytes > 0) ? (double) uploadedBytes / totalBytes * 100 : 100.0;
            log.info("=== [청크 전송] {}/{} bytes ({}%) ===", uploadedBytes, totalBytes, String.format("%.1f", progress));
        } while (chunkResult > -1);

        // 7. 업로드 완료 처리
        uploader.finish();

        // 업로드 URL에서 파일 ID 추출
        String uploadUrl = uploader.getUploadURL().toString();
        String fileId = extractFileId(uploadUrl);

        log.info("=== [업로드 완료] url={}, fileId={} ===", uploadUrl, fileId);

        return UploadResponse.builder()
                .fileId(fileId)
                .fileName(file.getName())
                .totalSize(totalBytes)
                .status("COMPLETED")
                .message("업로드 완료")
                .checksum(checksum) //파일 무결성 검증용 해시값
                .build();
    }

    /**
     * 업로드 URL에서 파일 ID를 추출
     * 예: http://localhost:8086/files/abcdef1234 → abcdef1234
     *
     * @param uploadUrl TUS 서버가 발급한 업로드 URL
     * @return 파일 ID
     */
    private String extractFileId(String uploadUrl) {
        if (uploadUrl == null || !uploadUrl.contains("/")) {
            return uploadUrl;
        }
        return uploadUrl.substring(uploadUrl.lastIndexOf('/') + 1);
    }
}
