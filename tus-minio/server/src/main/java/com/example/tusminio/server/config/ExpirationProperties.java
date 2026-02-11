package com.example.tusminio.server.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 업로드 만료 정책 설정.
 * 일정 시간이 지나도 완료되지 않은 업로드를 자동으로 정리
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "tus.expiration")
public class ExpirationProperties {

    /** 만료 정책 활성화 여부 */
    private boolean enabled = true;

    /** 업로드 만료 시간. 마지막 청크 수신 이후 이 시간이 지나면 만료 처리 (기본 24시간) */
    private Duration timeout = Duration.ofHours(24);

    /** 만료 업로드 정리 주기 (기본 1시간) */
    private Duration cleanupInterval = Duration.ofHours(1);
}
