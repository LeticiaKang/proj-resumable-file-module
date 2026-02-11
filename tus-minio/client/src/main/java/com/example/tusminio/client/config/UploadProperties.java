package com.example.tusminio.client.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 업로드 동시성 설정
 * - 배치 업로드 시 동시에 실행할 수 있는 업로드 수와 비동기 작업을 처리할 스레드 풀 크기를 관리를 위한 설정 파일
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "tus.upload") // application.yml의 tus.upload.* 설정 매핑
public class UploadProperties {

    /** 동시 업로드 최대 수 (Semaphore permits) */
    private int maxConcurrent = 3;

    /** 비동기 작업 스레드 풀 크기 */
    private int threadPoolSize = 5;
}
