package com.example.tusminio.server.filter;

import com.example.tusminio.server.config.ValidationProperties;
import com.example.tusminio.server.controller.DebugController;
import com.example.tusminio.server.entity.FileInfo;
import com.example.tusminio.server.repository.FileInfoRepository;
import com.example.tusminio.server.service.CallbackService;
import com.example.tusminio.server.service.ChecksumService;
import com.example.tusminio.server.service.MinioStorageService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.desair.tus.server.TusFileUploadService;
import me.desair.tus.server.upload.UploadInfo;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * TUS 프로토콜 요청을 처리하는 서블릿 필터.
 * <p>
 * /files/* 경로로 들어오는 모든 요청을 가로채서 tus-java-server 라이브러리에 위임합니다.
 * tus-java-server가 TUS 프로토콜(POST, PATCH, HEAD, DELETE, OPTIONS)을 완전히 처리하므로
 * chain.doFilter()를 호출하지 않습니다.
 * </p>
 *
 * <h3>처리 흐름:</h3>
 * <ol>
 *   <li>POST 요청 전: 파일 확장자 검증 (허용되지 않으면 422 반환)</li>
 *   <li>tus-java-server 라이브러리로 요청 위임 (tusFileUploadService.process())</li>
 *   <li>POST 성공 후: DB에 FileInfo 레코드 생성</li>
 *   <li>PATCH 성공 후: DB 오프셋 갱신, 업로드 완료 시 체크섬 검증 → MinIO 전송 → 콜백</li>
 * </ol>
 *
 * <h3>중요 - tus-java-server 라이브러리 연동 포인트:</h3>
 * <ul>
 *   <li>tusFileUploadService.process(request, response): TUS 프로토콜 요청 처리</li>
 *   <li>tusFileUploadService.getUploadInfo(requestURI): 업로드 상태 조회</li>
 *   <li>응답은 tus-java-server가 직접 작성하므로 chain.doFilter() 호출 불필요</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TusFilter extends OncePerRequestFilter {

    private final TusFileUploadService tusFileUploadService;
    private final FileInfoRepository fileInfoRepository;
    private final ValidationProperties validationProperties;
    private final ChecksumService checksumService;
    private final MinioStorageService minioStorageService;
    private final CallbackService callbackService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String method = request.getMethod();
        String requestURI = request.getRequestURI();

        // === POST 요청 시: 파일 확장자 검증 (tus-java-server 처리 전에 선행 검증) ===
        if ("POST".equalsIgnoreCase(method)) {
            String metadata = request.getHeader("Upload-Metadata");
            String fileName = extractFileNameFromMetadata(metadata);

            if (fileName != null && !isAllowedExtension(fileName)) {
                log.warn("=== [TUS FILTER] 허용되지 않은 확장자: {} ===", fileName);
                response.setStatus(422);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"error\":\"허용되지 않은 파일 확장자입니다: " + fileName + "\"}"
                );
                return;
            }
        }

        // === tus-java-server 라이브러리로 요청 처리 위임 ===
        log.info("=== [TUS FILTER] {} {} ===", method, requestURI);
        tusFileUploadService.process(request, response);

        // === POST 성공 후 처리 ===
        if ("POST".equalsIgnoreCase(method) && isSuccessStatus(response.getStatus())) {
            // Location 헤더가 있으면 새 업로드 세션 생성 (POST /files)
            if (response.getHeader("Location") != null) {
                handlePostComplete(request, response);
            }
            // POST로 청크가 전송된 경우에도 완료 체크 (creation-with-upload 확장)
            // tus-java-client는 PATCH 대신 POST /files/{id}로 청크를 보낼 수 있음
            handlePatchComplete(request, response, requestURI);
        }

        // === PATCH 성공 후: 오프셋 갱신 및 업로드 완료 처리 ===
        if ("PATCH".equalsIgnoreCase(method) && isSuccessStatus(response.getStatus())) {
            handlePatchComplete(request, response, requestURI);
        }

        // 중요: chain.doFilter()를 호출하지 않음
        // tus-java-server 라이브러리가 응답을 직접 완성하기 때문
    }

    /**
     * POST 요청 처리 후: DB에 FileInfo 레코드를 생성합니다.
     * tus-java-server가 응답 Location 헤더에 업로드 URI를 설정합니다.
     */
    private void handlePostComplete(HttpServletRequest request, HttpServletResponse response) {
        try {
            // tus-java-server가 Location 헤더에 업로드 URI를 반환
            String locationHeader = response.getHeader("Location");
            if (locationHeader == null) {
                log.warn("=== [TUS POST] Location 헤더 없음, DB 레코드 생성 건너뜀 ===");
                return;
            }

            // Location 헤더에서 uploadUri 추출 (예: http://host/files/abc → /files/abc)
            String uploadUri = extractUploadUri(locationHeader);

            // Upload-Metadata 헤더에서 파일 정보 파싱
            String metadata = request.getHeader("Upload-Metadata");
            String fileName = extractFileNameFromMetadata(metadata);
            String checksum = extractChecksumFromMetadata(metadata);

            // Upload-Length 헤더에서 전체 파일 크기 파싱
            long totalSize = 0;
            String uploadLength = request.getHeader("Upload-Length");
            if (uploadLength != null) {
                totalSize = Long.parseLong(uploadLength);
            }

            // DB에 FileInfo 레코드 생성
            FileInfo fileInfo = FileInfo.builder()
                    .uploadUri(uploadUri)
                    .fileName(fileName)
                    .totalSize(totalSize)
                    .offset(0)
                    .status("uploading")
                    .checksum(checksum)
                    .checksumVerified(false)
                    .callbackSent(false)
                    .build();

            fileInfoRepository.save(fileInfo);
            log.info("=== [TUS POST] 세션 생성 uri={}, fileName={} ===", uploadUri, fileName);

        } catch (Exception e) {
            log.error("=== [TUS POST] DB 레코드 생성 실패: {} ===", e.getMessage(), e);
        }
    }

    /**
     * PATCH 요청 처리 후: 업로드 진행 상태를 확인하고 완료 시 후속 처리를 수행합니다.
     * tus-java-server의 UploadInfo에서 현재 오프셋과 전체 크기를 조회합니다.
     */
    private void handlePatchComplete(HttpServletRequest request,
                                     HttpServletResponse response,
                                     String requestURI) {
        try {
            // tus-java-server에서 업로드 정보 조회
            UploadInfo uploadInfo = tusFileUploadService.getUploadInfo(requestURI);
            if (uploadInfo == null) {
                log.warn("=== [TUS PATCH] UploadInfo를 찾을 수 없음: {} ===", requestURI);
                return;
            }

            long currentOffset = uploadInfo.getOffset();
            long totalLength = uploadInfo.getLength();

            // DB에서 FileInfo 레코드 조회 및 오프셋 갱신
            FileInfo fileInfo = fileInfoRepository.findByUploadUri(requestURI).orElse(null);
            if (fileInfo == null) {
                log.warn("=== [TUS PATCH] DB에 FileInfo 없음: {} ===", requestURI);
                return;
            }

            fileInfo.setOffset(currentOffset);
            fileInfoRepository.save(fileInfo);

            log.info("=== [TUS PATCH] 청크 수신 offset={}/{} ===", currentOffset, totalLength);

            // === 실패 시뮬레이션 체크 (디버깅용) ===
            if (DebugController.shouldFailNextPatch()) {
                log.warn("=== [TUS PATCH] 실패 시뮬레이션 활성화 - 500 응답 반환 ===");
                response.setStatus(500);
                return;
            }

            // === 업로드 완료 체크: 오프셋이 전체 크기와 같으면 완료 ===
            if (currentOffset == totalLength) {
                log.info("=== [UPLOAD COMPLETE] uri={}, fileName={}, size={} ===",
                        requestURI, fileInfo.getFileName(), totalLength);

                fileInfo.setStatus("completed");
                fileInfoRepository.save(fileInfo);

                // 1. 체크섬 검증 (클라이언트가 체크섬을 제공한 경우)
                checksumService.verify(requestURI, fileInfo);

                // 2. 로컬 파일을 MinIO로 전송
                minioStorageService.transferToMinio(requestURI, fileInfo);

                // 3. 업로드 완료 콜백 전송
                callbackService.notifyCompletion(fileInfo);
            }

        } catch (Exception e) {
            log.error("=== [TUS PATCH] 후처리 실패: {} ===", e.getMessage(), e);
        }
    }

    /**
     * Upload-Metadata 헤더에서 filename 값을 추출합니다.
     * <p>
     * TUS 프로토콜에서 Upload-Metadata는 "key base64value, key base64value" 형식입니다.
     * 예: "filename dGVzdC50eHQ=, checksum YWJjMTIz"
     * </p>
     */
    private String extractFileNameFromMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return null;
        }

        // Upload-Metadata 형식: "key1 base64val1,key2 base64val2"
        String[] pairs = metadata.split(",");
        for (String pair : pairs) {
            String[] kv = pair.trim().split("\\s+", 2);
            if (kv.length == 2 && "filename".equalsIgnoreCase(kv[0])) {
                return new String(Base64.getDecoder().decode(kv[1]), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /**
     * Upload-Metadata 헤더에서 checksum 값을 추출합니다.
     */
    private String extractChecksumFromMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return null;
        }

        String[] pairs = metadata.split(",");
        for (String pair : pairs) {
            String[] kv = pair.trim().split("\\s+", 2);
            if (kv.length == 2 && "checksum".equalsIgnoreCase(kv[0])) {
                return new String(Base64.getDecoder().decode(kv[1]), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /**
     * Location 헤더에서 업로드 URI 경로를 추출합니다.
     * 전체 URL (http://host/files/abc)에서 경로 부분(/files/abc)만 추출합니다.
     */
    private String extractUploadUri(String locationHeader) {
        if (locationHeader == null) {
            return null;
        }

        // 전체 URL인 경우 경로 부분만 추출
        if (locationHeader.startsWith("http://") || locationHeader.startsWith("https://")) {
            try {
                java.net.URI uri = java.net.URI.create(locationHeader);
                return uri.getPath();
            } catch (Exception e) {
                log.warn("=== [TUS FILTER] Location URI 파싱 실패: {} ===", locationHeader);
            }
        }

        // 이미 경로인 경우 그대로 반환
        return locationHeader;
    }

    /**
     * 파일 확장자가 허용 목록에 포함되는지 검사합니다.
     * 허용 목록이 비어있으면 모든 확장자를 허용합니다.
     */
    private boolean isAllowedExtension(String fileName) {
        List<String> allowed = validationProperties.getAllowedExtensions();
        if (allowed == null || allowed.isEmpty()) {
            return true; // 허용 목록이 비어있으면 모든 확장자 허용
        }

        String lowerFileName = fileName.toLowerCase();
        return allowed.stream()
                .anyMatch(ext -> lowerFileName.endsWith(ext.toLowerCase()));
    }

    /**
     * HTTP 응답 상태 코드가 2xx(성공) 범위인지 확인합니다.
     */
    private boolean isSuccessStatus(int status) {
        return status >= 200 && status < 300;
    }
}
