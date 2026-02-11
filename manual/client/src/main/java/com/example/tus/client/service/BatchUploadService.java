package com.example.tus.client.service;

import com.example.tus.client.config.UploadProperties;
import com.example.tus.client.dto.BatchUploadResponse;
import com.example.tus.client.dto.UploadResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

/**
 * 배치(다중 파일) 업로드 서비스
 *
 * <p>여러 파일을 동시에 업로드할 때 동시성을 제어하며 병렬 처리합니다.</p>
 *
 * <h3>동시성 제어 메커니즘:</h3>
 * <pre>
 * ┌──────────────────────────────────────────────────────────────┐
 * │  Semaphore (max-concurrent = 3)                             │
 * │                                                             │
 * │  요청: [파일1] [파일2] [파일3] [파일4] [파일5]                  │
 * │                                                             │
 * │  ┌─────────┐  ┌─────────┐  ┌─────────┐                     │
 * │  │ Thread 1│  │ Thread 2│  │ Thread 3│  ← 동시 실행 (3개)    │
 * │  │ 파일1   │  │ 파일2   │  │ 파일3   │                       │
 * │  └────┬────┘  └────┬────┘  └────┬────┘                     │
 * │       │            │            │                           │
 * │  ┌────▼────┐  ┌────▼────┐                                  │
 * │  │ Thread 4│  │ Thread 5│  ← 앞선 작업 완료 후 시작           │
 * │  │ 파일4   │  │ 파일5   │                                   │
 * │  └─────────┘  └─────────┘                                  │
 * └──────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p><b>CompletableFuture + Semaphore 조합:</b></p>
 * <ul>
 *   <li>CompletableFuture: 각 파일 업로드를 비동기로 실행</li>
 *   <li>Semaphore: 동시에 실행 가능한 업로드 수를 제한 (서버 과부하 방지)</li>
 *   <li>uploadExecutor: 커스텀 스레드 풀에서 작업 실행 (AsyncConfig에서 정의)</li>
 * </ul>
 */
@Slf4j
@Service
public class BatchUploadService {

    private final TusClientService tusClientService;
    private final UploadProperties uploadProperties;
    private final Executor uploadExecutor;

    /**
     * 생성자 주입
     *
     * @param tusClientService TUS 업로드 핵심 서비스
     * @param uploadProperties 업로드 동시성 설정
     * @param uploadExecutor   AsyncConfig에서 생성한 "uploadExecutor" 스레드 풀
     */
    public BatchUploadService(
            TusClientService tusClientService,
            UploadProperties uploadProperties,
            @Qualifier("uploadExecutor") Executor uploadExecutor) {
        this.tusClientService = tusClientService;
        this.uploadProperties = uploadProperties;
        this.uploadExecutor = uploadExecutor;
    }

    /**
     * 여러 파일을 동시에 업로드합니다.
     *
     * <p>동작 과정:</p>
     * <ol>
     *   <li>Semaphore를 생성하여 동시 업로드 수를 제한</li>
     *   <li>각 파일에 대해 CompletableFuture를 생성하여 비동기 실행</li>
     *   <li>각 CompletableFuture 내에서:
     *     <ul>
     *       <li>Semaphore.acquire()로 슬롯 확보 (슬롯이 없으면 대기)</li>
     *       <li>TusClientService.uploadFile() 호출</li>
     *       <li>finally 블록에서 Semaphore.release()로 슬롯 반환</li>
     *     </ul>
     *   </li>
     *   <li>CompletableFuture.allOf()로 모든 작업 완료 대기</li>
     *   <li>결과를 집계하여 BatchUploadResponse 반환</li>
     * </ol>
     *
     * @param filePaths 업로드할 파일 경로 목록
     * @return BatchUploadResponse 전체 업로드 결과 (성공/실패 수 포함)
     */
    public BatchUploadResponse uploadFiles(List<String> filePaths) {
        int fileCount = filePaths.size();
        log.info("=== [BATCH START] files={}, maxConcurrent={} ===",
                fileCount, uploadProperties.getMaxConcurrent());

        // === Semaphore: 동시 업로드 수 제한 ===
        // 예: maxConcurrent=3이면 동시에 최대 3개의 파일만 업로드 진행
        // 4번째 파일은 앞선 파일 중 하나가 완료될 때까지 대기
        Semaphore semaphore = new Semaphore(uploadProperties.getMaxConcurrent());

        // 각 파일에 대한 비동기 업로드 작업 생성
        List<CompletableFuture<UploadResponse>> futures = new ArrayList<>();

        for (int i = 0; i < filePaths.size(); i++) {
            final String filePath = filePaths.get(i);
            final int fileIndex = i + 1;

            // === CompletableFuture.supplyAsync: 비동기 작업 생성 ===
            // uploadExecutor 스레드 풀에서 실행됩니다.
            CompletableFuture<UploadResponse> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // Semaphore 슬롯 확보 (사용 가능한 슬롯이 없으면 대기)
                    log.debug("=== [BATCH] 파일 {}/{} Semaphore 슬롯 대기 중: {} ===",
                            fileIndex, fileCount, filePath);
                    semaphore.acquire();

                    log.info("=== [BATCH] 파일 {}/{} 업로드 시작: {} ===",
                            fileIndex, fileCount, filePath);

                    // TUS 프로토콜을 사용하여 파일 업로드 실행
                    return tusClientService.uploadFile(filePath);

                } catch (InterruptedException e) {
                    // Semaphore 대기 중 인터럽트 발생
                    Thread.currentThread().interrupt();
                    log.error("=== [BATCH ERROR] 파일 {}/{} 인터럽트 발생: {} ===",
                            fileIndex, fileCount, filePath, e);

                    return UploadResponse.builder()
                            .fileName(filePath)
                            .status("FAILED")
                            .message("업로드 중 인터럽트 발생: " + e.getMessage())
                            .build();
                } catch (Exception e) {
                    log.error("=== [BATCH ERROR] 파일 {}/{} 업로드 실패: {} ===",
                            fileIndex, fileCount, filePath, e);

                    return UploadResponse.builder()
                            .fileName(filePath)
                            .status("FAILED")
                            .message("업로드 실패: " + e.getMessage())
                            .build();
                } finally {
                    // === 반드시 Semaphore 슬롯 반환 ===
                    // finally 블록에서 release()를 호출하여 예외 발생 시에도 슬롯이 반환되도록 보장
                    semaphore.release();
                    log.debug("=== [BATCH] 파일 {}/{} Semaphore 슬롯 반환: {} ===",
                            fileIndex, fileCount, filePath);
                }
            }, uploadExecutor);

            futures.add(future);
        }

        // === 모든 비동기 작업 완료 대기 ===
        // CompletableFuture.allOf()는 모든 future가 완료될 때까지 블로킹합니다.
        log.info("=== [BATCH] 모든 파일 업로드 작업 대기 중... ===");
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // === 결과 집계 ===
        List<UploadResponse> results = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (CompletableFuture<UploadResponse> future : futures) {
            try {
                UploadResponse response = future.get();
                results.add(response);

                if ("COMPLETED".equals(response.getStatus())) {
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                // CompletableFuture.get() 실행 중 예외 발생 (일반적으로 발생하지 않음)
                failCount++;
                results.add(UploadResponse.builder()
                        .status("FAILED")
                        .message("결과 조회 실패: " + e.getMessage())
                        .build());
            }
        }

        log.info("=== [BATCH COMPLETE] success={}, fail={}, total={} ===",
                successCount, failCount, fileCount);

        return BatchUploadResponse.builder()
                .results(results)
                .successCount(successCount)
                .failCount(failCount)
                .build();
    }
}
