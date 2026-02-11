package com.example.tus.client.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 단일 파일 업로드 요청 DTO
 *
 * <p>클라이언트가 업로드할 파일의 경로를 담아 전송합니다.</p>
 *
 * <pre>
 * 요청 예시:
 * POST /api/tus/upload
 * {
 *   "filePath": "C:/uploads/example-file.zip"
 * }
 * </pre>
 */
@Data
public class UploadRequest {

    /**
     * 업로드할 파일의 절대 경로
     * 예: "C:/uploads/example-file.zip" 또는 "/home/user/documents/report.pdf"
     */
    @NotBlank(message = "파일 경로는 필수입니다.")
    private String filePath;
}
