package com.example.tusminio.client.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * TUS 서버 연결 설정
 * - 전달할 서버 URL(TUS 서버의 업로드 생성 엔드포인트)과 청크 전송 크기를 관리
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "tus") // application.yml의 tus.* 설정 매핑
public class TusClientProperties {

    /** TUS 서버 업로드 엔드포인트 URL */
    private String serverUrl;

    /** 청크 전송 크기 (바이트 단위, 기본값: 3MB) */
    private int chunkSize = 3 * 1024 * 1024;
}
