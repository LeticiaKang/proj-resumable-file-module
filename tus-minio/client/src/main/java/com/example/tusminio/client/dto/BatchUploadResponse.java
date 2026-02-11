package com.example.tusminio.client.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 배치 업로드 응답 DTO
 */
@Data
@Builder
public class BatchUploadResponse {

    /** 각 파일별 업로드 결과 목록 */
    private List<UploadResponse> results;

    /** 업로드 성공 건수 */
    private int successCount;

    /** 업로드 실패 건수 */
    private int failCount;
}
