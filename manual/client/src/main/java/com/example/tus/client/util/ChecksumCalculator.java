package com.example.tus.client.util;

import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 파일 체크섬(해시) 계산 유틸리티
 *
 * <p>SHA-256 해시 알고리즘을 사용하여 파일의 무결성 검증용 체크섬을 계산합니다.</p>
 *
 * <h3>체크섬의 역할 (TUS 프로토콜에서):</h3>
 * <ul>
 *   <li>클라이언트가 파일의 SHA-256 체크섬을 계산하여 서버에 메타데이터로 전송</li>
 *   <li>서버는 모든 청크를 수신한 후 동일한 알고리즘으로 체크섬을 계산</li>
 *   <li>두 체크섬이 일치하면 파일이 손상 없이 전송된 것을 보장</li>
 *   <li>불일치 시 파일 손상으로 판단하고 재업로드를 요청</li>
 * </ul>
 *
 * <p><b>성능 고려:</b> 8KB 버퍼를 사용하여 대용량 파일도 메모리 부담 없이 처리합니다.</p>
 */
@Slf4j
public class ChecksumCalculator {

    /**
     * 파일 읽기 버퍼 크기: 8KB
     * 너무 작으면 I/O 호출이 많아지고, 너무 크면 메모리를 낭비합니다.
     */
    private static final int BUFFER_SIZE = 8192; // 8KB

    /**
     * 인스턴스 생성 방지 (유틸리티 클래스)
     */
    private ChecksumCalculator() {
        throw new UnsupportedOperationException("유틸리티 클래스는 인스턴스를 생성할 수 없습니다.");
    }

    /**
     * 지정된 파일의 SHA-256 체크섬을 계산합니다.
     *
     * <p>동작 과정:</p>
     * <ol>
     *   <li>파일을 8KB 버퍼 단위로 읽어들임</li>
     *   <li>MessageDigest에 누적하여 SHA-256 해시 계산</li>
     *   <li>최종 해시를 16진수(hex) 문자열로 변환하여 반환</li>
     * </ol>
     *
     * @param filePath 체크섬을 계산할 파일의 절대 경로
     * @return SHA-256 해시의 16진수 문자열 (64자)
     * @throws RuntimeException 파일 읽기 실패 또는 SHA-256 알고리즘을 사용할 수 없는 경우
     */
    public static String calculateSha256(String filePath) {
        log.debug("=== [CHECKSUM] 체크섬 계산 시작: file={} ===", filePath);

        try {
            // SHA-256 해시 알고리즘 인스턴스 생성
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // 파일을 8KB 버퍼로 읽으면서 해시 누적 계산
            try (FileInputStream fis = new FileInputStream(filePath)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;

                // 파일 끝까지 반복하여 읽기
                while ((bytesRead = fis.read(buffer)) != -1) {
                    // 읽은 바이트를 해시 계산에 추가
                    digest.update(buffer, 0, bytesRead);
                }
            }

            // 최종 해시 값을 바이트 배열로 얻기
            byte[] hashBytes = digest.digest();

            // 바이트 배열을 16진수 문자열로 변환
            String hexHash = bytesToHex(hashBytes);

            // 파일 이름 추출 (경로에서 마지막 부분)
            Path path = Paths.get(filePath);
            String fileName = path.getFileName().toString();

            log.info("=== [CHECKSUM] file={}, sha256={} ===", fileName, hexHash);

            return hexHash;

        } catch (NoSuchAlgorithmException e) {
            // SHA-256 알고리즘이 JVM에서 지원되지 않는 경우 (일반적으로 발생하지 않음)
            log.error("=== [CHECKSUM ERROR] SHA-256 알고리즘을 사용할 수 없습니다 ===", e);
            throw new RuntimeException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        } catch (IOException e) {
            // 파일 읽기 실패
            log.error("=== [CHECKSUM ERROR] 파일 읽기 실패: file={} ===", filePath, e);
            throw new RuntimeException("체크섬 계산 중 파일 읽기 실패: " + filePath, e);
        }
    }

    /**
     * 바이트 배열을 16진수(hex) 문자열로 변환합니다.
     *
     * <p>예: [0xE3, 0xB0, 0xC4] -> "e3b0c4"</p>
     *
     * @param bytes 변환할 바이트 배열
     * @return 16진수 문자열 (소문자)
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            // 각 바이트를 2자리 16진수로 변환
            String hex = String.format("%02x", b);
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
