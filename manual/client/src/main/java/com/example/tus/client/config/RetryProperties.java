package com.example.tus.client.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 재시도 정책 설정 프로퍼티
 *
 * <p>application.yml의 "tus.retry" 접두사 아래 설정을 바인딩합니다.</p>
 *
 * <pre>
 * tus.retry:
 *   max-attempts: 3            # 최대 재시도 횟수
 *   initial-delay-ms: 1000     # 첫 재시도 대기 시간 (1초)
 *   max-delay-ms: 30000        # 최대 대기 시간 (30초)
 *   multiplier: 2.0            # 지수 백오프 배율
 * </pre>
 *
 * <p><b>지수 백오프(Exponential Backoff) 전략:</b></p>
 * <ul>
 *   <li>1차 재시도: 1000ms 대기</li>
 *   <li>2차 재시도: 1000ms * 2.0 = 2000ms 대기</li>
 *   <li>3차 재시도: 2000ms * 2.0 = 4000ms 대기</li>
 *   <li>단, max-delay-ms(30초)를 초과하지 않음</li>
 * </ul>
 *
 * <p>네트워크 일시적 장애 시 서버에 과도한 부하를 주지 않으면서
 * 자동으로 복구할 수 있게 해줍니다.</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "tus.retry")
public class RetryProperties {

    /**
     * 최대 재시도 횟수
     * 이 횟수를 초과하면 업로드 실패로 처리됩니다.
     */
    private int maxAttempts = 3;

    /**
     * 첫 번째 재시도까지의 대기 시간 (밀리초)
     */
    private long initialDelayMs = 1000;

    /**
     * 재시도 대기 시간의 상한값 (밀리초)
     * 지수 백오프로 계산된 대기 시간이 이 값을 초과하지 않습니다.
     */
    private long maxDelayMs = 30000;

    /**
     * 지수 백오프 배율
     * 매 재시도마다 이전 대기 시간에 이 값을 곱합니다.
     */
    private double multiplier = 2.0;
}
