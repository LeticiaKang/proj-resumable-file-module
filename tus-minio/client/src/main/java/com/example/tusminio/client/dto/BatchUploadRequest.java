package com.example.tusminio.client.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 배치(다중) 파일 업로드 요청 DTO
 *
 * 여러 파일을 동시에 업로드할 때 사용한다.
 * BatchUploadService가 Semaphore로 동시성을 제어하면서
 * 각 파일을 CompletableFuture로 비동기 업로드한다.
 */
@Data
public class BatchUploadRequest {

    /** 업로드할 파일들의 로컬 절대 경로 목록 */
    @NotEmpty(message = "파일 경로 목록은 비어있을 수 없습니다")
    private List<String> filePaths;
}
