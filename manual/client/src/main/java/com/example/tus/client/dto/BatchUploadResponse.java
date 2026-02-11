package com.example.tus.client.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 배치(다중) 파일 업로드 응답 DTO
 *
 * <p>여러 파일의 업로드 결과를 종합하여 반환합니다.</p>
 *
 * <pre>
 * 응답 예시:
 * {
 *   "results": [ ... ],    // 각 파일별 UploadResponse 목록
 *   "successCount": 2,     // 성공한 업로드 수
 *   "failCount": 1         // 실패한 업로드 수
 * }
 * </pre>
 */
@Data
@Builder
public class BatchUploadResponse {

    /**
     * 각 파일별 업로드 결과 목록
     * 성공/실패에 관계없이 모든 파일의 결과가 포함됩니다.
     */
    private List<UploadResponse> results;

    /**
     * 성공적으로 업로드된 파일 수
     */
    private int successCount;

    /**
     * 업로드에 실패한 파일 수
     */
    private int failCount;
}
