package com.example.tusminio.server.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 업로드 완료 콜백(웹훅) 설정.
 * <p>
 * 파일 업로드가 완료되고 MinIO로 전송된 후 지정된 URL로 완료 알림을 전송합니다.
 * 외부 시스템과의 연동에 활용됩니다.
 * </p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "tus.callback")
public class CallbackProperties {

    /** 콜백 활성화 여부 */
    private boolean enabled = false;

    /** 업로드 완료 시 POST 요청을 보낼 웹훅 URL */
    private String url;
}
