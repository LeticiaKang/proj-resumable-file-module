package com.example.tusminio.client.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 배치(다중) 파일 업로드 요청 DTO
 */
@Data
public class BatchUploadRequest {

    /** 업로드할 파일들의 로컬 절대 경로 목록 */
    @NotEmpty(message = "파일 경로 목록은 비어있을 수 없습니다")
    private List<String> filePaths;
}
