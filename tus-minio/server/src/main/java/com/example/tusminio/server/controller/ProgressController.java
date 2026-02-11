package com.example.tusminio.server.controller;

import com.example.tusminio.server.entity.FileInfo;
import com.example.tusminio.server.repository.FileInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 업로드 진행률 조회 컨트롤러.
 * <p>
 * PostgreSQL에 저장된 FileInfo를 기반으로 업로드 진행 상태를 조회합니다.
 * 클라이언트가 업로드 진행률을 폴링(polling)할 때 사용합니다.
 * </p>
 *
 * <h3>엔드포인트:</h3>
 * <ul>
 *   <li>GET /api/progress/{uploadUri} - 특정 업로드의 진행률 조회</li>
 *   <li>GET /api/progress/list - 전체 업로드 목록 조회</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final FileInfoRepository fileInfoRepository;

    /**
     * 특정 업로드의 진행률을 조회합니다.
     * <p>
     * uploadUri 경로 변수는 URL 인코딩되어 전달될 수 있으므로 디코딩합니다.
     * 퍼센트 단위의 진행률과 함께 파일 정보를 반환합니다.
     * </p>
     *
     * @param uploadUri TUS 업로드 URI (URL 인코딩된 상태로 전달 가능)
     * @return 진행률 JSON (fileName, totalSize, offset, percent, status 등)
     */
    @GetMapping("/{uploadUri}")
    public ResponseEntity<Map<String, Object>> getProgress(@PathVariable String uploadUri) {
        // URL 디코딩 (경로 변수에 '/'가 포함될 수 있음)
        String decodedUri = URLDecoder.decode(uploadUri, StandardCharsets.UTF_8);

        // uploadUri가 /files/로 시작하지 않으면 접두사 추가
        if (!decodedUri.startsWith("/files/")) {
            decodedUri = "/files/" + decodedUri;
        }

        return fileInfoRepository.findByUploadUri(decodedUri)
                .map(fileInfo -> {
                    Map<String, Object> result = buildProgressMap(fileInfo);
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 전체 업로드 목록과 상태를 조회합니다.
     * 모든 업로드의 진행률을 한번에 확인할 수 있습니다.
     *
     * @return 전체 업로드 진행률 목록
     */
    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> listAll() {
        List<FileInfo> allFiles = fileInfoRepository.findAll();

        List<Map<String, Object>> result = allFiles.stream()
                .map(this::buildProgressMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * FileInfo 엔티티를 진행률 Map으로 변환합니다.
     * 퍼센트 계산: (offset / totalSize) * 100
     */
    private Map<String, Object> buildProgressMap(FileInfo fileInfo) {
        Map<String, Object> map = new HashMap<>();
        map.put("uploadUri", fileInfo.getUploadUri());
        map.put("fileName", fileInfo.getFileName());
        map.put("totalSize", fileInfo.getTotalSize());
        map.put("offset", fileInfo.getOffset());
        map.put("status", fileInfo.getStatus());
        map.put("minioObjectKey", fileInfo.getMinioObjectKey());
        map.put("checksumVerified", fileInfo.isChecksumVerified());
        map.put("callbackSent", fileInfo.isCallbackSent());

        // 퍼센트 계산 (0으로 나누기 방지)
        double percent = 0.0;
        if (fileInfo.getTotalSize() > 0) {
            percent = (double) fileInfo.getOffset() / fileInfo.getTotalSize() * 100.0;
        }
        map.put("percent", Math.round(percent * 100.0) / 100.0); // 소수점 2자리

        return map;
    }
}
