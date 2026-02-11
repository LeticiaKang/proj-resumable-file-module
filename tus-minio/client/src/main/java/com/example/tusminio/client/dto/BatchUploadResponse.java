package com.example.tusminio.client.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 배치 업로드 응답 DTO
 *
 * 여러 파일의 업로드 결과를 종합하여 반환한다.
 * 각 파일별 결과(UploadResponse)와 함께 성공/실패 건수를 제공한다.
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
