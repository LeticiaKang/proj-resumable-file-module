package com.example.tus.client.service;

import com.example.tus.client.config.RetryProperties;
import com.example.tus.client.config.TusClientProperties;
import com.example.tus.client.dto.UploadResponse;
import com.example.tus.client.util.ChecksumCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * TUS 프로토콜 수동 구현 핵심 서비스
 *
 * <p>WebClient를 사용하여 TUS 프로토콜의 세 가지 핵심 HTTP 메서드를 직접 구현합니다:</p>
 *
 * <h3>TUS 프로토콜 핵심 흐름:</h3>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  1. POST /files                                                       │
 * │     → 업로드 세션 생성                                                  │
 * │     → Upload-Length: 파일 전체 크기                                      │
 * │     → Upload-Metadata: filename(Base64), checksum(Base64)             │
 * │     ← Location: /files/{fileId}  (서버가 발급한 고유 ID)                 │
 * │                                                                       │
 * │  2. PATCH /files/{fileId}  (반복)                                      │
 * │     → Upload-Offset: 현재까지 전송된 바이트 수                            │
 * │     → Content-Type: application/offset+octet-stream                   │
 * │     → Body: 청크 데이터 (바이너리)                                       │
 * │     ← Upload-Offset: 서버가 확인한 새로운 오프셋                          │
 * │                                                                       │
 * │  3. HEAD /files/{fileId}  (이어받기 시)                                 │
 * │     → 서버의 현재 업로드 오프셋 조회                                      │
 * │     ← Upload-Offset: 서버에 저장된 바이트 수                              │
 * └─────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p><b>재시도 전략:</b> 지수 백오프(Exponential Backoff)를 사용하여
 * 네트워크 오류 시 자동으로 재시도합니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TusClientService {

    private final WebClient webClient;
    private final TusClientProperties tusProperties;
    private final RetryProperties retryProperties;

    /**
     * 파일을 TUS 서버에 업로드합니다.
     *
     * <p>전체 업로드 흐름:</p>
     * <ol>
     *   <li>SHA-256 체크섬 계산 (무결성 검증용)</li>
     *   <li>POST 요청으로 업로드 세션 생성 (서버에서 fileId 발급)</li>
     *   <li>PATCH 요청으로 청크 단위 데이터 전송 (offset 0부터 시작)</li>
     *   <li>모든 청크 전송 완료 후 결과 반환</li>
     * </ol>
     *
     * @param filePath 업로드할 파일의 절대 경로
     * @return UploadResponse 업로드 결과 정보
     */
    public UploadResponse uploadFile(String filePath) {
        log.info("=== [UPLOAD START] file={} ===", filePath);

        try {
            // === 1단계: 파일 정보 확인 ===
            File file = new File(filePath);
            if (!file.exists()) {
                log.error("=== [UPLOAD ERROR] 파일이 존재하지 않습니다: {} ===", filePath);
                return UploadResponse.builder()
                        .fileName(Paths.get(filePath).getFileName().toString())
                        .status("FAILED")
                        .message("파일이 존재하지 않습니다: " + filePath)
                        .build();
            }

            String fileName = file.getName();
            long totalSize = file.length();
            log.info("=== [FILE INFO] name={}, size={} bytes ({} MB) ===",
                    fileName, totalSize, String.format("%.2f", totalSize / (1024.0 * 1024.0)));

            // === 2단계: SHA-256 체크섬 계산 ===
            // TUS 프로토콜에서 파일 무결성을 보장하기 위해 체크섬을 계산하여 서버에 전송합니다.
            String checksum = ChecksumCalculator.calculateSha256(filePath);

            // === 3단계: 업로드 세션 생성 (POST 요청) ===
            // TUS 프로토콜의 Creation 확장: POST 요청으로 새로운 업로드 리소스를 생성합니다.
            String fileId = createUploadSession(fileName, totalSize, checksum);
            log.info("=== [SESSION CREATED] fileId={} ===", fileId);

            // === 4단계: 청크 단위 데이터 전송 (PATCH 요청 반복) ===
            // offset 0부터 시작하여 파일 전체를 청크 단위로 전송합니다.
            sendChunks(fileId, filePath, 0L, totalSize);

            log.info("=== [UPLOAD COMPLETE] fileId={}, fileName={}, totalSize={} ===",
                    fileId, fileName, totalSize);

            return UploadResponse.builder()
                    .fileId(fileId)
                    .fileName(fileName)
                    .totalSize(totalSize)
                    .status("COMPLETED")
                    .message("업로드가 성공적으로 완료되었습니다.")
                    .checksum(checksum)
                    .build();

        } catch (Exception e) {
            log.error("=== [UPLOAD FAILED] file={}, error={} ===", filePath, e.getMessage(), e);
            return UploadResponse.builder()
                    .fileName(Paths.get(filePath).getFileName().toString())
                    .status("FAILED")
                    .message("업로드 실패: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 중단된 업로드를 이어서 진행합니다 (Resume).
     *
     * <p>TUS 프로토콜의 핵심 기능인 이어받기(Resumable Upload) 구현:</p>
     * <ol>
     *   <li>HEAD 요청으로 서버에 저장된 현재 오프셋(전송된 바이트 수)을 조회</li>
     *   <li>해당 오프셋부터 나머지 청크를 전송</li>
     * </ol>
     *
     * <p><b>사용 시나리오:</b></p>
     * <ul>
     *   <li>네트워크 중단으로 업로드가 도중에 멈춘 경우</li>
     *   <li>클라이언트 프로세스가 비정상 종료된 후 재시작한 경우</li>
     *   <li>서버 재시작 후 기존 세션을 이어서 진행하는 경우</li>
     * </ul>
     *
     * @param fileId   서버에서 발급한 파일 고유 식별자
     * @param filePath 업로드할 파일의 절대 경로
     * @return UploadResponse 업로드 결과 정보
     */
    public UploadResponse resumeUpload(String fileId, String filePath) {
        log.info("=== [RESUME START] fileId={}, file={} ===", fileId, filePath);

        try {
            File file = new File(filePath);
            if (!file.exists()) {
                log.error("=== [RESUME ERROR] 파일이 존재하지 않습니다: {} ===", filePath);
                return UploadResponse.builder()
                        .fileId(fileId)
                        .fileName(Paths.get(filePath).getFileName().toString())
                        .status("FAILED")
                        .message("파일이 존재하지 않습니다: " + filePath)
                        .build();
            }

            String fileName = file.getName();
            long totalSize = file.length();

            // === HEAD 요청: 서버의 현재 업로드 오프셋 조회 ===
            // TUS 프로토콜에서 HEAD 요청은 서버가 현재까지 수신한 바이트 수를 반환합니다.
            // Upload-Offset 헤더에 현재 오프셋 값이 담겨옵니다.
            long currentOffset = getUploadOffset(fileId);
            log.info("=== [RESUME OFFSET] fileId={}, offset={}/{} ({}%) ===",
                    fileId, currentOffset, totalSize,
                    String.format("%.1f", (currentOffset * 100.0) / totalSize));

            // 이미 업로드가 완료된 경우
            if (currentOffset >= totalSize) {
                log.info("=== [RESUME SKIP] fileId={}, 이미 업로드가 완료되었습니다 ===", fileId);
                return UploadResponse.builder()
                        .fileId(fileId)
                        .fileName(fileName)
                        .totalSize(totalSize)
                        .status("COMPLETED")
                        .message("이미 업로드가 완료된 파일입니다.")
                        .checksum(ChecksumCalculator.calculateSha256(filePath))
                        .build();
            }

            // === 나머지 청크 전송: 서버 오프셋 위치부터 이어서 전송 ===
            sendChunks(fileId, filePath, currentOffset, totalSize);

            String checksum = ChecksumCalculator.calculateSha256(filePath);

            log.info("=== [RESUME COMPLETE] fileId={}, fileName={}, totalSize={} ===",
                    fileId, fileName, totalSize);

            return UploadResponse.builder()
                    .fileId(fileId)
                    .fileName(fileName)
                    .totalSize(totalSize)
                    .status("RESUMED")
                    .message("이어받기가 성공적으로 완료되었습니다.")
                    .checksum(checksum)
                    .build();

        } catch (Exception e) {
            log.error("=== [RESUME FAILED] fileId={}, error={} ===", fileId, e.getMessage(), e);
            return UploadResponse.builder()
                    .fileId(fileId)
                    .fileName(Paths.get(filePath).getFileName().toString())
                    .status("FAILED")
                    .message("이어받기 실패: " + e.getMessage())
                    .build();
        }
    }

    /**
     * TUS 서버에 새 업로드 세션을 생성합니다 (POST 요청).
     *
     * <p><b>TUS Creation 확장 프로토콜:</b></p>
     * <pre>
     * POST /files HTTP/1.1
     * Host: localhost:8082
     * Tus-Resumable: 1.0.0
     * Upload-Length: 15728640
     * Upload-Metadata: filename dGVzdC56aXA=,checksum YTFiMmMzZDQ=
     *
     * → 응답:
     * HTTP/1.1 201 Created
     * Location: /files/a1b2c3d4-e5f6-7890-abcd-ef1234567890
     * Tus-Resumable: 1.0.0
     * </pre>
     *
     * <p><b>Upload-Metadata 헤더 형식:</b></p>
     * <ul>
     *   <li>키-값 쌍을 쉼표로 구분</li>
     *   <li>값은 Base64로 인코딩</li>
     *   <li>키와 값 사이는 공백(space)으로 구분</li>
     *   <li>예: "filename dGVzdC56aXA=,checksum YTFiMmMzZDQ="</li>
     * </ul>
     *
     * @param fileName  파일 이름
     * @param totalSize 파일 전체 크기 (바이트)
     * @param checksum  SHA-256 체크섬
     * @return 서버에서 발급한 fileId (Location 헤더에서 추출)
     */
    private String createUploadSession(String fileName, long totalSize, String checksum) {
        log.debug("=== [CREATE SESSION] fileName={}, totalSize={}, checksum={} ===",
                fileName, totalSize, checksum);

        // Upload-Metadata 헤더 값 구성
        // TUS 프로토콜 규격에 따라 파일 이름과 체크섬을 Base64 인코딩하여 전송
        String encodedFileName = Base64.getEncoder().encodeToString(
                fileName.getBytes(StandardCharsets.UTF_8));
        String encodedChecksum = Base64.getEncoder().encodeToString(
                checksum.getBytes(StandardCharsets.UTF_8));
        String metadata = "filename " + encodedFileName + ",checksum " + encodedChecksum;

        log.debug("=== [CREATE SESSION] Upload-Metadata: {} ===", metadata);

        // === POST 요청: 업로드 세션 생성 ===
        // TUS 서버에 새로운 업로드 리소스를 만들고, Location 헤더로 fileId를 수신합니다.
        String locationHeader = webClient.post()
                .header("Upload-Length", String.valueOf(totalSize))
                .header("Upload-Metadata", metadata)
                .header(HttpHeaders.CONTENT_TYPE, "application/offset+octet-stream")
                .retrieve()
                // 4xx/5xx 오류 시 예외 변환
                .onStatus(HttpStatusCode::isError, response -> {
                    log.error("=== [CREATE SESSION ERROR] status={} ===", response.statusCode());
                    return response.bodyToMono(String.class)
                            .flatMap(body -> Mono.error(new RuntimeException(
                                    "세션 생성 실패 [" + response.statusCode() + "]: " + body)));
                })
                .toBodilessEntity()
                .block()  // 동기 블로킹 호출 (WebClient는 기본적으로 비동기이지만, 여기서는 순차 처리 필요)
                .getHeaders()
                .getFirst(HttpHeaders.LOCATION);

        if (locationHeader == null || locationHeader.isBlank()) {
            throw new RuntimeException("서버 응답에 Location 헤더가 없습니다. 세션 생성 실패.");
        }

        // Location 헤더에서 fileId 추출
        // 예: "/files/a1b2c3d4-e5f6-7890" → "a1b2c3d4-e5f6-7890"
        String fileId = locationHeader.substring(locationHeader.lastIndexOf('/') + 1);

        log.debug("=== [CREATE SESSION] Location={}, fileId={} ===", locationHeader, fileId);

        return fileId;
    }

    /**
     * 서버의 현재 업로드 오프셋을 조회합니다 (HEAD 요청).
     *
     * <p><b>TUS 프로토콜 HEAD 요청:</b></p>
     * <pre>
     * HEAD /files/{fileId} HTTP/1.1
     * Host: localhost:8082
     * Tus-Resumable: 1.0.0
     *
     * → 응답:
     * HTTP/1.1 200 OK
     * Upload-Offset: 6291456
     * Upload-Length: 15728640
     * Tus-Resumable: 1.0.0
     * </pre>
     *
     * <p>이 요청은 이어받기(Resume) 시 서버가 현재까지 수신한 바이트 수를 확인하는 데 사용됩니다.</p>
     *
     * @param fileId 서버에서 발급한 파일 고유 식별자
     * @return 서버에 저장된 현재 오프셋 (바이트 단위)
     */
    private long getUploadOffset(String fileId) {
        log.debug("=== [HEAD REQUEST] fileId={} ===", fileId);

        // === HEAD 요청: 서버의 현재 오프셋 조회 ===
        String offsetHeader = webClient.head()
                .uri("/{fileId}", fileId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> {
                    log.error("=== [HEAD ERROR] fileId={}, status={} ===", fileId, response.statusCode());
                    return response.bodyToMono(String.class)
                            .flatMap(body -> Mono.error(new RuntimeException(
                                    "오프셋 조회 실패 [" + response.statusCode() + "]: " + body)));
                })
                .toBodilessEntity()
                .block()
                .getHeaders()
                .getFirst("Upload-Offset");

        if (offsetHeader == null) {
            log.warn("=== [HEAD WARNING] Upload-Offset 헤더가 없습니다. offset=0으로 설정 ===");
            return 0L;
        }

        long offset = Long.parseLong(offsetHeader);
        log.debug("=== [HEAD RESPONSE] fileId={}, offset={} ===", fileId, offset);

        return offset;
    }

    /**
     * 파일 데이터를 청크 단위로 TUS 서버에 전송합니다 (PATCH 요청 반복).
     *
     * <p><b>TUS 프로토콜 PATCH 요청:</b></p>
     * <pre>
     * PATCH /files/{fileId} HTTP/1.1
     * Host: localhost:8082
     * Tus-Resumable: 1.0.0
     * Upload-Offset: 3145728
     * Content-Type: application/offset+octet-stream
     * Content-Length: 3145728
     *
     * [바이너리 데이터...]
     *
     * → 응답:
     * HTTP/1.1 204 No Content
     * Upload-Offset: 6291456
     * Tus-Resumable: 1.0.0
     * </pre>
     *
     * <p><b>청크 전송 과정:</b></p>
     * <ol>
     *   <li>RandomAccessFile로 파일을 열고 현재 오프셋 위치로 이동</li>
     *   <li>chunkSize 만큼 읽어서 바이트 배열에 저장</li>
     *   <li>PATCH 요청으로 서버에 전송</li>
     *   <li>실패 시 지수 백오프 재시도</li>
     *   <li>파일 끝까지 반복</li>
     * </ol>
     *
     * @param fileId    서버에서 발급한 파일 고유 식별자
     * @param filePath  업로드할 파일의 절대 경로
     * @param offset    전송 시작 위치 (바이트 단위)
     * @param totalSize 파일 전체 크기 (바이트 단위)
     * @throws IOException 파일 읽기 실패 시
     */
    private void sendChunks(String fileId, String filePath, long offset, long totalSize) throws IOException {
        log.info("=== [CHUNK TRANSFER START] fileId={}, startOffset={}, totalSize={}, chunkSize={} ===",
                fileId, offset, totalSize, tusProperties.getChunkSize());

        // 전송할 총 청크 수 계산 (로깅용)
        long remainingBytes = totalSize - offset;
        int estimatedChunks = (int) Math.ceil((double) remainingBytes / tusProperties.getChunkSize());
        log.info("=== [CHUNK TRANSFER] 예상 청크 수: {} ===", estimatedChunks);

        int chunkNumber = 0;

        // === RandomAccessFile: 파일의 임의 위치부터 읽기 가능 ===
        // 이어받기 시 offset 위치부터 읽기를 시작할 수 있어 TUS 프로토콜에 적합합니다.
        try (RandomAccessFile raf = new RandomAccessFile(filePath, "r")) {
            // 현재 오프셋 위치로 파일 포인터 이동
            raf.seek(offset);

            long currentOffset = offset;

            // 파일 끝까지 청크 단위로 반복 전송
            while (currentOffset < totalSize) {
                chunkNumber++;

                // 남은 바이트와 chunkSize 중 작은 값을 실제 청크 크기로 사용
                // (마지막 청크는 chunkSize보다 작을 수 있음)
                int bytesToRead = (int) Math.min(tusProperties.getChunkSize(), totalSize - currentOffset);
                byte[] chunkData = new byte[bytesToRead];

                // 파일에서 청크 데이터 읽기
                int actualBytesRead = raf.read(chunkData);
                if (actualBytesRead <= 0) {
                    log.warn("=== [CHUNK WARNING] 읽을 데이터가 없습니다. offset={} ===", currentOffset);
                    break;
                }

                // 실제 읽은 크기가 버퍼보다 작은 경우 (파일 끝) 배열 크기 조정
                if (actualBytesRead < bytesToRead) {
                    byte[] trimmed = new byte[actualBytesRead];
                    System.arraycopy(chunkData, 0, trimmed, 0, actualBytesRead);
                    chunkData = trimmed;
                }

                log.debug("=== [CHUNK READ] chunk #{}, offset={}, size={} bytes ===",
                        chunkNumber, currentOffset, chunkData.length);

                // === 재시도 로직이 포함된 PATCH 요청 ===
                sendChunkWithRetry(fileId, currentOffset, chunkData);

                // 오프셋 업데이트
                currentOffset += chunkData.length;

                log.info("=== [CHUNK SENT] fileId={}, offset={}/{} ({}%), chunk #{}/{} ===",
                        fileId, currentOffset, totalSize,
                        String.format("%.1f", (currentOffset * 100.0) / totalSize),
                        chunkNumber, estimatedChunks);
            }
        }

        log.info("=== [CHUNK TRANSFER COMPLETE] fileId={}, totalChunks={} ===", fileId, chunkNumber);
    }

    /**
     * 단일 청크를 지수 백오프 재시도와 함께 전송합니다.
     *
     * <p><b>재시도 전략 (Exponential Backoff):</b></p>
     * <pre>
     *   시도 1: 즉시 전송
     *   실패 → 1000ms 대기 후 재시도
     *   실패 → 2000ms 대기 후 재시도
     *   실패 → 4000ms 대기 후 재시도
     *   ... (max-delay-ms 초과하지 않음)
     *   최대 재시도 횟수 초과 시 예외 발생
     * </pre>
     *
     * @param fileId    서버에서 발급한 파일 고유 식별자
     * @param offset    이 청크의 시작 오프셋
     * @param chunkData 전송할 청크 바이너리 데이터
     */
    private void sendChunkWithRetry(String fileId, long offset, byte[] chunkData) {
        int attempt = 0;
        long delayMs = retryProperties.getInitialDelayMs();

        while (true) {
            attempt++;
            try {
                // === PATCH 요청: 청크 데이터 전송 ===
                // TUS 프로토콜에서 PATCH는 파일 데이터를 실제로 전송하는 메서드입니다.
                webClient.patch()
                        .uri("/{fileId}", fileId)
                        // Upload-Offset: 이 청크가 파일의 어느 위치부터인지 알려줌
                        .header("Upload-Offset", String.valueOf(offset))
                        // Content-Type: TUS 프로토콜 규격의 바이너리 스트림 타입
                        .header(HttpHeaders.CONTENT_TYPE, "application/offset+octet-stream")
                        // Content-Length: 이 청크의 크기
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(chunkData.length))
                        // 바이너리 청크 데이터를 요청 본문에 포함
                        .bodyValue(chunkData)
                        .retrieve()
                        // 오류 응답 처리
                        .onStatus(HttpStatusCode::isError, response -> {
                            log.error("=== [PATCH ERROR] fileId={}, offset={}, status={} ===",
                                    fileId, offset, response.statusCode());
                            return response.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new RuntimeException(
                                            "청크 전송 실패 [" + response.statusCode() + "]: " + body)));
                        })
                        .toBodilessEntity()
                        .block();  // 동기 블로킹 (순차 청크 전송을 위해)

                // 전송 성공 → 반복 종료
                log.debug("=== [PATCH SUCCESS] fileId={}, offset={}, attempt={} ===",
                        fileId, offset, attempt);
                return;

            } catch (Exception e) {
                // 최대 재시도 횟수 초과 시 예외 전파
                if (attempt >= retryProperties.getMaxAttempts()) {
                    log.error("=== [RETRY EXHAUSTED] fileId={}, offset={}, attempts={}/{} ===",
                            fileId, offset, attempt, retryProperties.getMaxAttempts());
                    throw new RuntimeException(
                            "청크 전송 실패 (최대 재시도 횟수 초과): fileId=" + fileId +
                                    ", offset=" + offset + ", attempts=" + attempt, e);
                }

                // === 지수 백오프: 재시도 전 대기 시간 계산 ===
                log.warn("=== [RETRY] fileId={}, offset={}, attempt={}/{}, nextDelay={}ms, error={} ===",
                        fileId, offset, attempt, retryProperties.getMaxAttempts(), delayMs, e.getMessage());

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("재시도 대기 중 인터럽트 발생", ie);
                }

                // 다음 재시도 대기 시간 계산 (지수 백오프)
                // 현재 대기 시간 × 배율, 단 최대 대기 시간을 초과하지 않음
                delayMs = Math.min(
                        (long) (delayMs * retryProperties.getMultiplier()),
                        retryProperties.getMaxDelayMs()
                );
            }
        }
    }
}
