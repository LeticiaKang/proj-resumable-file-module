package com.example.tus.client.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 배치(다중) 파일 업로드 요청 DTO
 *
 * <p>여러 파일을 동시에 업로드하기 위한 파일 경로 목록을 담아 전송합니다.</p>
 *
 * <pre>
 * 요청 예시:
 * POST /api/tus/upload/batch
 * {
 *   "filePaths": [
 *     "C:/uploads/file1.zip",
 *     "C:/uploads/file2.pdf",
 *     "C:/uploads/file3.mp4"
 *   ]
 * }
 * </pre>
 *
 * <p><b>동시성 제한:</b> tus.upload.max-concurrent 설정값에 따라
 * 동시에 업로드되는 파일 수가 제한됩니다.</p>
 */
@Data
public class BatchUploadRequest {

    /**
     * 업로드할 파일 경로 목록
     * 최소 1개 이상의 파일 경로가 포함되어야 합니다.
     */
    @NotEmpty(message = "파일 경로 목록은 비어있을 수 없습니다.")
    private List<String> filePaths;
}
