package com.example.tusminio.server.service;

import com.example.tusminio.server.entity.FileInfo;
import com.example.tusminio.server.repository.FileInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.desair.tus.server.TusFileUploadService;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.MessageDigest;

/**
 * 파일 체크섬(SHA-256) 검증 서비스.
 * <p>
 * 업로드 완료된 파일의 무결성을 검증합니다.
 * 클라이언트가 Upload-Metadata에 포함한 체크섬 값과
 * 서버에서 계산한 체크섬 값을 비교합니다.
 * </p>
 *
 * <h3>tus-java-server 연동:</h3>
 * <p>
 * tus-java-server의 getUploadedBytes(uploadUri)를 통해 업로드된 파일의
 * InputStream을 획득하고, SHA-256 해시를 계산합니다.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChecksumService {

    private final TusFileUploadService tusFileUploadService;
    private final FileInfoRepository fileInfoRepository;

    /**
     * 업로드된 파일의 SHA-256 체크섬을 검증합니다.
     * <p>
     * 클라이언트가 체크섬을 제공하지 않은 경우 검증을 건너뜁니다.
     * 체크섬이 일치하면 checksumVerified를 true로, 불일치하면 false로 설정합니다.
     * </p>
     *
     * @param uploadUri TUS 업로드 URI (tus-java-server에서 파일 식별)
     * @param fileInfo  파일 정보 엔티티 (checksum 필드에 기대값 포함)
     */
    public void verify(String uploadUri, FileInfo fileInfo) {
        String expectedChecksum = fileInfo.getChecksum();

        // 클라이언트가 체크섬을 제공하지 않은 경우 검증 건너뜀
        if (expectedChecksum == null || expectedChecksum.isBlank()) {
            log.debug("=== [CHECKSUM] 체크섬 미제공, 검증 건너뜀: {} ===", fileInfo.getFileName());
            return;
        }

        try {
            // tus-java-server에서 업로드된 파일의 InputStream 획득
            InputStream inputStream = tusFileUploadService.getUploadedBytes(uploadUri);
            if (inputStream == null) {
                log.warn("=== [CHECKSUM] 파일을 찾을 수 없음: {} ===", uploadUri);
                return;
            }

            // SHA-256 해시 계산
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            inputStream.close();

            // 16진수 문자열로 변환
            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            String computed = hexString.toString();

            // 체크섬 비교
            boolean match = computed.equalsIgnoreCase(expectedChecksum);
            fileInfo.setChecksumVerified(match);
            fileInfoRepository.save(fileInfo);

            if (match) {
                log.info("=== [CHECKSUM] 검증 성공: {} ===", fileInfo.getFileName());
            } else {
                log.warn("=== [CHECKSUM] 검증 실패: expected={}, computed={} ===",
                        expectedChecksum, computed);
            }

        } catch (Exception e) {
            log.error("=== [CHECKSUM] 검증 중 오류: {} ===", e.getMessage(), e);
        }
    }
}
