package com.example.tus.server.service;

import com.example.tus.server.entity.FileInfo;
import com.example.tus.server.repository.FileInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 파일 체크섬 검증 서비스.
 * <p>
 * 업로드 완료 후 파일 무결성을 검증합니다.
 * 클라이언트가 Upload-Metadata에 포함한 SHA-256 체크섬과
 * 서버에서 계산한 체크섬을 비교하여 파일이 정상적으로 전송되었는지 확인합니다.
 * </p>
 * <p>
 * TUS 프로토콜의 checksum 확장을 지원하는 서버 측 검증 로직입니다.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChecksumService {

    private final FileInfoRepository fileInfoRepository;
    private final TusUploadService tusUploadService;

    /**
     * 업로드 완료된 파일의 SHA-256 체크섬을 검증합니다.
     * <p>
     * 클라이언트가 제공한 체크섬이 없는 경우 검증을 건너뜁니다.
     * 체크섬이 일치하면 checksumVerified=true, 불일치하면 false를 설정합니다.
     * </p>
     *
     * @param fileInfo 검증할 파일 정보
     * @return 체크섬 일치 여부 (클라이언트 체크섬이 없으면 true)
     */
    public boolean verifyChecksum(FileInfo fileInfo) {
        String expectedChecksum = fileInfo.getChecksum();

        // 클라이언트가 체크섬을 제공하지 않은 경우 검증 건너뛰기
        if (expectedChecksum == null || expectedChecksum.isBlank()) {
            log.info("=== [CHECKSUM] 클라이언트 체크섬 미제공, 검증 생략 fileId={} ===",
                    fileInfo.getFileId());
            fileInfo.setChecksumVerified(true);
            fileInfoRepository.save(fileInfo);
            return true;
        }

        try {
            // 서버 측 SHA-256 체크섬 계산
            Path filePath = tusUploadService.getFilePath(fileInfo.getFileId());
            String actualChecksum = calculateSha256(filePath);

            // 비교 (대소문자 무시)
            boolean match = expectedChecksum.equalsIgnoreCase(actualChecksum);

            log.info("=== [CHECKSUM] 검증 fileId={}, expected={}, actual={}, match={} ===",
                    fileInfo.getFileId(), expectedChecksum, actualChecksum, match);

            fileInfo.setChecksumVerified(match);
            fileInfoRepository.save(fileInfo);

            if (!match) {
                log.warn("=== [CHECKSUM] 체크섬 불일치! 파일이 손상되었을 수 있습니다. fileId={} ===",
                        fileInfo.getFileId());
            }

            return match;

        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("=== [CHECKSUM] 검증 중 오류 발생 fileId={}: {} ===",
                    fileInfo.getFileId(), e.getMessage(), e);
            fileInfo.setChecksumVerified(false);
            fileInfoRepository.save(fileInfo);
            return false;
        }
    }

    /**
     * 파일의 SHA-256 해시를 계산합니다.
     *
     * @param filePath 해시를 계산할 파일 경로
     * @return 16진수 문자열로 표현된 SHA-256 해시
     */
    private String calculateSha256(Path filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream is = Files.newInputStream(filePath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        byte[] hashBytes = digest.digest();
        return HexFormat.of().formatHex(hashBytes);
    }
}
