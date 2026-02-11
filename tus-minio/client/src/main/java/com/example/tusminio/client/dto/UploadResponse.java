package com.example.tusminio.client.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 단일 파일 업로드 응답 DTO
 */
@Data
@Builder
public class UploadResponse {

    /** TUS 서버가 발급한 파일 ID (업로드 URL에서 추출) */
    private String fileId;

    /** 원본 파일명 */
    private String fileName;

    /** 파일 전체 크기 (바이트) */
    private long totalSize;

    /** 업로드 상태 (COMPLETED, FAILED 등) */
    private String status;

    /** 상태 메시지 (성공/실패 상세 정보) */
    private String message;

    /** 파일 SHA-256 체크섬 */
    private String checksum;
}
