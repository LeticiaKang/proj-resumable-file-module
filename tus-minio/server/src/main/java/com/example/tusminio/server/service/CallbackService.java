package com.example.tusminio.server.service;

import com.example.tusminio.server.config.CallbackProperties;
import com.example.tusminio.server.entity.FileInfo;
import com.example.tusminio.server.repository.FileInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 업로드 완료 콜백(웹훅) 서비스.
 * <p>
 * 파일 업로드가 완료되고 MinIO로 전송된 후, 지정된 웹훅 URL로 완료 알림을 전송합니다.
 * 콜백은 tus.callback.enabled가 true일 때만 동작합니다.
 * 외부 시스템(예: 파일 처리 파이프라인, 알림 서비스)과의 연동에 활용됩니다.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackService {

    private final CallbackProperties callbackProperties;
    private final FileInfoRepository fileInfoRepository;

    /** RestTemplate으로 웹훅 POST 요청 전송 */
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 업로드 완료 콜백을 전송합니다.
     * <p>
     * 설정에서 콜백이 비활성화되어 있으면 건너뜁니다.
     * 콜백 전송 성공 시 callbackSent를 true로 갱신합니다.
     * </p>
     *
     * @param fileInfo 업로드 완료된 파일 정보
     */
    public void notifyCompletion(FileInfo fileInfo) {
        // 콜백이 비활성화되어 있으면 건너뜀
        if (!callbackProperties.isEnabled()) {
            log.debug("=== [CALLBACK] 콜백 비활성화 상태, 건너뜀 ===");
            return;
        }

        String callbackUrl = callbackProperties.getUrl();
        if (callbackUrl == null || callbackUrl.isBlank()) {
            log.warn("=== [CALLBACK] 콜백 URL이 설정되지 않음 ===");
            return;
        }

        try {
            // 콜백 페이로드 구성
            Map<String, Object> payload = new HashMap<>();
            payload.put("uploadUri", fileInfo.getUploadUri());
            payload.put("fileName", fileInfo.getFileName());
            payload.put("totalSize", fileInfo.getTotalSize());
            payload.put("status", fileInfo.getStatus());
            payload.put("minioObjectKey", fileInfo.getMinioObjectKey());
            payload.put("checksumVerified", fileInfo.isChecksumVerified());

            // HTTP POST 요청 전송
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(callbackUrl, request, String.class);

            // 콜백 전송 성공 기록
            fileInfo.setCallbackSent(true);
            fileInfoRepository.save(fileInfo);

            log.info("=== [CALLBACK] 콜백 전송 완료: url={}, fileName={} ===",
                    callbackUrl, fileInfo.getFileName());

        } catch (Exception e) {
            log.error("=== [CALLBACK] 콜백 전송 실패: url={}, error={} ===",
                    callbackUrl, e.getMessage());
        }
    }
}
