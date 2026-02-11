package com.example.tus.server.service;

import com.example.tus.server.config.TusServerProperties;
import com.example.tus.server.entity.FileInfo;
import com.example.tus.server.repository.FileInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TUS 업로드 핵심 비즈니스 로직 서비스.
 * <p>
 * TUS 프로토콜의 핵심 동작을 구현합니다:
 * - 업로드 세션 생성 (POST /files에 대응)
 * - 청크 수신 및 파일 이어쓰기 (PATCH /files/{id}에 대응)
 * - 업로드 삭제 (DELETE /files/{id}에 대응)
 * - Upload-Metadata 파싱 (Base64 디코딩)
 * </p>
 * <p>
 * 파일 쓰기 시 FileChannel.lock()을 사용하여 동시 접근 안전성을 보장합니다.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TusUploadService {

    private final TusServerProperties tusProperties;
    private final FileInfoRepository fileInfoRepository;

    /**
     * 새 업로드 세션을 생성합니다.
     * TUS POST 요청에 대응하며, UUID 기반 fileId를 생성하고
     * 디스크에 빈 파일을 생성한 뒤 DB에 초기 상태를 저장합니다.
     *
     * @param totalSize 업로드할 파일의 전체 크기 (Upload-Length 헤더 값)
     * @param metadata  Upload-Metadata 헤더 원본 문자열
     * @return 생성된 FileInfo 엔티티
     */
    @Transactional
    public FileInfo createUpload(long totalSize, String metadata) throws IOException {
        String fileId = UUID.randomUUID().toString();

        // Upload-Metadata 파싱: "filename base64val,checksum base64val" 형식
        Map<String, String> meta = parseMetadata(metadata);
        String fileName = meta.getOrDefault("filename", "unknown");
        String checksum = meta.getOrDefault("checksum", null);

        log.info("=== [TUS SERVICE] 업로드 생성 시작 fileId={}, fileName={}, totalSize={} ===",
                fileId, fileName, totalSize);

        // 디스크에 빈 파일 생성 (이후 PATCH에서 청크를 이어붙임)
        Path filePath = getFilePath(fileId);
        Files.createFile(filePath);
        log.debug("=== [TUS SERVICE] 빈 파일 생성 완료: {} ===", filePath);

        // DB에 업로드 정보 저장
        FileInfo fileInfo = FileInfo.builder()
                .fileId(fileId)
                .fileName(fileName)
                .totalSize(totalSize)
                .offset(0)
                .status("uploading")
                .checksum(checksum)
                .checksumVerified(false)
                .callbackSent(false)
                .build();

        fileInfoRepository.save(fileInfo);
        log.info("=== [TUS SERVICE] 업로드 세션 DB 저장 완료 fileId={} ===", fileId);

        return fileInfo;
    }

    /**
     * 파일 정보를 조회합니다.
     *
     * @param fileId 업로드 세션 ID
     * @return FileInfo 엔티티
     * @throws IllegalArgumentException 해당 fileId가 존재하지 않는 경우
     */
    public FileInfo getFileInfo(String fileId) {
        return fileInfoRepository.findByFileId(fileId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "업로드 정보를 찾을 수 없습니다: fileId=" + fileId));
    }

    /**
     * 청크 데이터를 수신하여 파일에 이어씁니다.
     * TUS PATCH 요청에 대응하며, 클라이언트가 보낸 오프셋과 서버 오프셋이 일치하는지 검증합니다.
     * <p>
     * FileChannel.lock()을 사용하여 파일 쓰기 중 동시 접근을 방지합니다.
     * </p>
     *
     * @param fileId       업로드 세션 ID
     * @param clientOffset 클라이언트가 보낸 Upload-Offset 헤더 값
     * @param data         청크 데이터 InputStream
     * @return 갱신된 FileInfo 엔티티
     * @throws IllegalStateException 오프셋 불일치 시 (409 Conflict에 해당)
     */
    @Transactional
    public FileInfo receiveChunk(String fileId, long clientOffset, InputStream data) throws IOException {
        FileInfo fileInfo = getFileInfo(fileId);

        // === TUS 프로토콜 핵심: 오프셋 검증 ===
        // 클라이언트가 보낸 Upload-Offset이 서버의 현재 오프셋과 정확히 일치해야 합니다.
        // 불일치 시 409 Conflict를 반환하여 클라이언트가 HEAD 요청으로 올바른 오프셋을 재확인하도록 합니다.
        if (clientOffset != fileInfo.getOffset()) {
            log.warn("=== [TUS SERVICE] 오프셋 불일치! fileId={}, client={}, server={} ===",
                    fileId, clientOffset, fileInfo.getOffset());
            throw new IllegalStateException(
                    String.format("오프셋 불일치: client=%d, server=%d", clientOffset, fileInfo.getOffset()));
        }

        Path filePath = getFilePath(fileId);
        long bytesWritten = 0;

        // FileChannel.lock()을 사용한 안전한 파일 이어쓰기
        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "rw");
             FileChannel fileChannel = raf.getChannel()) {

            // 파일 잠금 획득 (동시 쓰기 방지)
            FileLock lock = fileChannel.lock();
            try {
                // 현재 오프셋 위치로 이동하여 이어쓰기
                fileChannel.position(fileInfo.getOffset());

                ReadableByteChannel inputChannel = Channels.newChannel(data);
                ByteBuffer buffer = ByteBuffer.allocate(8192); // 8KB 버퍼

                while (inputChannel.read(buffer) != -1) {
                    buffer.flip();
                    bytesWritten += fileChannel.write(buffer);
                    buffer.clear();
                }
            } finally {
                lock.release();
            }
        }

        // DB 오프셋 갱신
        long newOffset = fileInfo.getOffset() + bytesWritten;
        fileInfo.setOffset(newOffset);

        log.info("=== [TUS SERVICE] 청크 기록 완료 fileId={}, 기록량={}bytes, 새오프셋={}/{} ===",
                fileId, bytesWritten, newOffset, fileInfo.getTotalSize());

        // 업로드 완료 여부 확인
        if (newOffset >= fileInfo.getTotalSize()) {
            fileInfo.setStatus("completed");
            log.info("=== [TUS SERVICE] 파일 수신 완료! fileId={}, totalSize={} ===",
                    fileId, fileInfo.getTotalSize());
        }

        fileInfoRepository.save(fileInfo);
        return fileInfo;
    }

    /**
     * 업로드를 삭제합니다.
     * TUS DELETE 요청에 대응하며, 디스크의 파일과 DB 레코드를 모두 제거합니다.
     *
     * @param fileId 업로드 세션 ID
     */
    @Transactional
    public void deleteUpload(String fileId) throws IOException {
        FileInfo fileInfo = getFileInfo(fileId);

        // 디스크 파일 삭제
        Path filePath = getFilePath(fileId);
        if (Files.exists(filePath)) {
            Files.delete(filePath);
            log.info("=== [TUS SERVICE] 디스크 파일 삭제: {} ===", filePath);
        }

        // DB 레코드 삭제
        fileInfoRepository.delete(fileInfo);
        log.info("=== [TUS SERVICE] DB 레코드 삭제 완료 fileId={} ===", fileId);
    }

    /**
     * TUS Upload-Metadata 헤더를 파싱합니다.
     * <p>
     * 형식: "key1 base64value1,key2 base64value2"
     * 각 key-value 쌍은 쉼표로 구분되고, key와 Base64 인코딩된 value는 공백으로 구분됩니다.
     * </p>
     *
     * @param metadata Upload-Metadata 헤더 원본 문자열
     * @return 디코딩된 key-value Map
     */
    public Map<String, String> parseMetadata(String metadata) {
        Map<String, String> result = new HashMap<>();
        if (metadata == null || metadata.isBlank()) {
            return result;
        }

        // 쉼표로 분리하여 각 key-value 쌍 처리
        String[] pairs = metadata.split(",");
        for (String pair : pairs) {
            String trimmed = pair.trim();
            String[] parts = trimmed.split("\\s+", 2);
            if (parts.length == 2) {
                String key = parts[0].trim();
                String base64Value = parts[1].trim();
                try {
                    // Base64 디코딩하여 실제 값 복원
                    String decodedValue = new String(Base64.getDecoder().decode(base64Value));
                    result.put(key, decodedValue);
                    log.debug("=== [METADATA] {}={} (base64: {}) ===", key, decodedValue, base64Value);
                } catch (IllegalArgumentException e) {
                    log.warn("=== [METADATA] Base64 디코딩 실패: key={}, value={} ===", key, base64Value);
                    result.put(key, base64Value); // 디코딩 실패 시 원본 저장
                }
            } else if (parts.length == 1) {
                // value가 없는 key만 있는 경우
                result.put(parts[0].trim(), "");
            }
        }

        log.debug("=== [METADATA] 파싱 결과: {} ===", result);
        return result;
    }

    /**
     * fileId에 해당하는 디스크 파일 경로를 반환합니다.
     *
     * @param fileId 업로드 세션 ID
     * @return 파일의 절대 경로
     */
    public Path getFilePath(String fileId) {
        return Paths.get(tusProperties.getStoragePath()).resolve(fileId);
    }
}
