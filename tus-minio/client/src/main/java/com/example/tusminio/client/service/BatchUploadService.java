package com.example.tusminio.client.service;

import com.example.tusminio.client.config.UploadProperties;
import com.example.tusminio.client.dto.BatchUploadResponse;
import com.example.tusminio.client.dto.UploadResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

/**
 * 배치(다중) 파일 업로드 서비스
 *
 * 여러 파일을 동시에 업로드할 때 CompletableFuture와 Semaphore를 사용하여
 * 동시성을 제어한다.
 *
 * - Semaphore: maxConcurrent 설정값으로 동시 업로드 수를 제한
 *   → 서버 과부하 방지 및 네트워크 대역폭 분배
 * - CompletableFuture: 각 파일 업로드를 비동기로 실행
 *   → uploadExecutor 스레드 풀에서 병렬 처리
 *
 * tus-java-client가 각 업로드마다 독립적인 TusClient를 생성하므로,
 * 멀티스레드 환경에서도 안전하게 동작한다.
 */
@Slf4j
@Service
public class BatchUploadService {

    private final TusClientService tusClientService;
    private final UploadProperties uploadProperties;
    private final Executor uploadExecutor;

    public BatchUploadService(
            TusClientService tusClientService,
            UploadProperties uploadProperties,
            @Qualifier("uploadExecutor") Executor uploadExecutor) {
        this.tusClientService = tusClientService;
        this.uploadProperties = uploadProperties;
        this.uploadExecutor = uploadExecutor;
    }

    /**
     * 여러 파일을 동시에 업로드
     *
     * Semaphore로 동시 업로드 수를 제한하면서
     * CompletableFuture로 비동기 병렬 업로드를 수행한다.
     *
     * @param filePaths 업로드할 파일 경로 목록
     * @return 전체 업로드 결과 (각 파일별 결과 + 성공/실패 집계)
     */
    public BatchUploadResponse uploadBatch(List<String> filePaths) {
        log.info("=== [BATCH START] 총 {} 파일, 동시 업로드 최대 {} ===",
                filePaths.size(), uploadProperties.getMaxConcurrent());

        // 동시 업로드 수 제한을 위한 Semaphore
        Semaphore semaphore = new Semaphore(uploadProperties.getMaxConcurrent());

        // 각 파일에 대해 CompletableFuture 생성
        List<CompletableFuture<UploadResponse>> futures = new ArrayList<>();

        for (String filePath : filePaths) {
            CompletableFuture<UploadResponse> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // Semaphore 획득 (동시 업로드 수 제한)
                    semaphore.acquire();
                    log.info("=== [BATCH] 세마포어 획득, 업로드 시작: {} ===", filePath);

                    return tusClientService.uploadFile(filePath);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("=== [BATCH] 인터럽트 발생: {} ===", filePath);
                    return UploadResponse.builder()
                            .fileName(filePath)
                            .status("FAILED")
                            .message("업로드 중단됨 (인터럽트)")
                            .build();
                } finally {
                    // Semaphore 반환 (다음 대기 중인 업로드 허용)
                    semaphore.release();
                    log.info("=== [BATCH] 세마포어 반환: {} ===", filePath);
                }
            }, uploadExecutor);

            futures.add(future);
        }

        // 모든 업로드 완료 대기
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        // 결과 수집
        allFutures.join();

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
                failCount++;
                results.add(UploadResponse.builder()
                        .status("FAILED")
                        .message("결과 조회 실패: " + e.getMessage())
                        .build());
            }
        }

        log.info("=== [BATCH COMPLETE] 성공: {}, 실패: {}, 전체: {} ===",
                successCount, failCount, filePaths.size());

        return BatchUploadResponse.builder()
                .results(results)
                .successCount(successCount)
                .failCount(failCount)
                .build();
    }
}
