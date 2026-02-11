package com.example.tusminio.server.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 파일 검증 설정
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "tus.validation")
public class ValidationProperties {

    /** 허용되는 파일 확장자 목록 (예: .zip, .pdf, .png 등) */
    private List<String> allowedExtensions = new ArrayList<>();
}
