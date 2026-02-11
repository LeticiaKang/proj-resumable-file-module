package com.example.tus.client.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 단일 파일 업로드 응답 DTO
 *
 * <p>업로드 완료 또는 실패 시 클라이언트에게 반환되는 응답 정보입니다.</p>
 *
 * <pre>
 * 성공 응답 예시:
 * {
 *   "fileId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
 *   "fileName": "example-file.zip",
 *   "totalSize": 15728640,
 *   "status": "COMPLETED",
 *   "message": "업로드가 성공적으로 완료되었습니다.",
 *   "checksum": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
 * }
 * </pre>
 */
@Data
@Builder
public class UploadResponse {

    /**
     * TUS 서버에서 발급한 고유 파일 식별자
     * 서버의 Location 헤더에서 추출됩니다.
     */
    private String fileId;

    /**
     * 업로드된 파일의 원본 이름
     */
    private String fileName;

    /**
     * 파일의 전체 크기 (바이트 단위)
     */
    private long totalSize;

    /**
     * 업로드 상태
     * COMPLETED: 성공, FAILED: 실패, RESUMED: 이어받기 완료
     */
    private String status;

    /**
     * 상태에 대한 상세 메시지
     */
    private String message;

    /**
     * 파일의 SHA-256 체크섬
     * 서버 측에서 무결성 검증에 사용됩니다.
     */
    private String checksum;
}
