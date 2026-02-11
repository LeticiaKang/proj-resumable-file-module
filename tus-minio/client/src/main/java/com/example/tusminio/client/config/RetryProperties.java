package com.example.tusminio.client.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 업로드 재시도 정책 설정
 * - 네트워크 오류 등으로 업로드가 실패했을 때 재시도 정책
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "tus.retry")
public class RetryProperties {

    /** 최대 재시도 횟수 */
    private int maxAttempts = 3;

    /** 첫 번째 재시도 대기 시간 (밀리초) */
    private long initialDelayMs = 1000;

    /** 최대 대기 시간 (밀리초) */
    private long maxDelayMs = 30000;

    /** 지수 백오프 배수 >> 서버 부하를 줄이기 위함과 재기동 시간 확보를 위함. */
    private double multiplier = 2.0;
}
