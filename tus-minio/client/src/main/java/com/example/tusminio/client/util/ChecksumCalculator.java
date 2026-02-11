package com.example.tusminio.client.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 체크섬 계산 유틸리티
 *
 * 업로드 전 파일의 SHA-256 해시를 계산하여 TUS 서버에 메타데이터로 전달한다.
 * 서버 측에서 수신된 파일의 무결성을 검증하는 데 사용된다.
 */
public class ChecksumCalculator {

    private static final int BUFFER_SIZE = 8192;

    private ChecksumCalculator() {
        // 유틸리티 클래스 - 인스턴스 생성 방지
    }

    /**
     * 파일의 SHA-256 체크섬을 계산하여 16진수 문자열로 반환
     *
     * @param filePath 체크섬을 계산할 파일의 절대 경로
     * @return SHA-256 해시값 (64자리 16진수 문자열)
     * @throws IOException              파일 읽기 오류
     * @throws NoSuchAlgorithmException SHA-256 알고리즘 미지원 (일반적으로 발생하지 않음)
     */
    public static String calculateSha256(String filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (FileInputStream fis = new FileInputStream(filePath)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        // 바이트 배열을 16진수 문자열로 변환
        byte[] hashBytes = digest.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
