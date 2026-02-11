package com.example.tus.server.service;

import com.example.tus.server.config.CallbackProperties;
import com.example.tus.server.entity.FileInfo;
import com.example.tus.server.repository.FileInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 업로드 완료 콜백(웹훅) 서비스.
 * <p>
 * 파일 업로드가 완료되면 외부 시스템에 알림을 전송합니다.
 * 콜백 URL로 POST 요청을 보내며, 실패해도 업로드 자체는 실패하지 않습니다.
 * </p>
 * <p>
 * 콜백 전송 내용:
 * - fileId: 업로드 세션 ID
 * - fileName: 원본 파일명
 * - totalSize: 파일 전체 크기
 * - checksum: SHA-256 체크섬
 * - completedAt: 완료 시각
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackService {

    private final CallbackProperties callbackProperties;
    private final FileInfoRepository fileInfoRepository;
    private final RestTemplate restTemplate;

    /**
     * 업로드 완료 알림을 콜백 URL로 전송합니다.
     * <p>
     * tus.callback.enabled=true인 경우에만 동작합니다.
     * 전송 실패 시 경고 로그만 남기고, 업로드 결과에는 영향을 주지 않습니다.
     * </p>
     *
     * @param fileInfo 완료된 파일 정보
     */
    public void notifyCompletion(FileInfo fileInfo) {
        if (!callbackProperties.isEnabled()) {
            log.debug("=== [CALLBACK] 콜백 비활성화 상태, 전송 생략 fileId={} ===",
                    fileInfo.getFileId());
            return;
        }

        String callbackUrl = callbackProperties.getUrl();
        log.info("=== [CALLBACK] 완료 알림 전송 fileId={}, url={} ===",
                fileInfo.getFileId(), callbackUrl);

        try {
            // 콜백 요청 본문 구성
            Map<String, Object> payload = new HashMap<>();
            payload.put("fileId", fileInfo.getFileId());
            payload.put("fileName", fileInfo.getFileName());
            payload.put("totalSize", fileInfo.getTotalSize());
            payload.put("checksum", fileInfo.getChecksum());
            payload.put("completedAt", LocalDateTime.now().toString());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            // 콜백 URL로 POST 전송
            restTemplate.postForEntity(callbackUrl, request, String.class);

            // 전송 성공 기록
            fileInfo.setCallbackSent(true);
            fileInfoRepository.save(fileInfo);
            log.info("=== [CALLBACK] 완료 알림 전송 성공 fileId={} ===", fileInfo.getFileId());

        } catch (Exception e) {
            // 콜백 실패는 업로드 성공에 영향을 주지 않음 (경고 로그만 남김)
            log.warn("=== [CALLBACK] 완료 알림 전송 실패 fileId={}, url={}, error={} ===",
                    fileInfo.getFileId(), callbackUrl, e.getMessage());
        }
    }
}
