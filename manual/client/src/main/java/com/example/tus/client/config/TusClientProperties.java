package com.example.tus.client.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * TUS 클라이언트 기본 설정 프로퍼티
 *
 * <p>application.yml의 "tus" 접두사 아래 설정을 바인딩합니다.</p>
 *
 * <pre>
 * tus:
 *   server-url: http://localhost:8082/files   # TUS 서버 업로드 엔드포인트
 *   chunk-size: 3145728                        # 청크 크기 (3MB)
 * </pre>
 *
 * <p><b>chunk-size 설명:</b></p>
 * <ul>
 *   <li>TUS 프로토콜에서는 대용량 파일을 작은 청크(chunk)로 나누어 전송합니다.</li>
 *   <li>청크 크기가 너무 작으면 HTTP 요청 오버헤드가 증가합니다.</li>
 *   <li>청크 크기가 너무 크면 네트워크 오류 시 재전송 비용이 커집니다.</li>
 *   <li>일반적으로 1MB ~ 10MB 사이가 적절합니다.</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "tus")
public class TusClientProperties {

    /**
     * TUS 서버의 파일 업로드 엔드포인트 URL
     * 예: http://localhost:8082/files
     */
    private String serverUrl = "http://localhost:8082/files";

    /**
     * 한 번에 전송할 청크 크기 (바이트 단위)
     * 기본값: 3MB (3 * 1024 * 1024 = 3,145,728 바이트)
     */
    private int chunkSize = 3145728;
}
