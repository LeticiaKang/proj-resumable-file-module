package com.example.tusminio.server.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 업로드 완료 콜백(웹훅) 설정.
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
