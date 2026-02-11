package com.example.tusminio.client.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 단일 파일 업로드 요청 DTO
 */
@Data
public class UploadRequest {

    /** 업로드할 파일의 로컬 절대 경로 */
    @NotBlank(message = "파일 경로는 필수입니다")
    private String filePath;
}
