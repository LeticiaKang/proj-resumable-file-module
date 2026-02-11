package com.example.tusminio.client.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 업로드 스레드 풀 설정
 * - 파일을 여러 개 동시에 업로드할 때, 메인 스레드 하나로 순차 처리하면 느리니까 별도의 작업 스레드를 만들기 위한 설정
 * 
 * 1. UploadProperties에서 YAML의 tus.upload.thread-pool-size: 5 값을 주입받음
 * 2. 그 값으로 ThreadPoolTaskExecutor를 생성해서 Spring 빈으로 등록
 * 3. 이후 서비스 레이어에서 @Qualifier("uploadExecutor")로 이 빈을 가져다 CompletableFuture 등에 넘겨서 비동기 업로드 실행
 */
@Configuration
@RequiredArgsConstructor
public class AsyncConfig {

    // UploadProperties 설정값 
    private final UploadProperties uploadProperties;

    /**
     * 업로드 전용 스레드 풀 Executor
     */
    @Bean(name = "uploadExecutor")
    public Executor uploadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(uploadProperties.getThreadPoolSize()); // 코어 스레드 수
        executor.setMaxPoolSize(uploadProperties.getThreadPoolSize()); // 최대 스레드 수
        executor.setQueueCapacity(100); // 대기열 크기
        executor.setThreadNamePrefix("tus-upload-");
        executor.initialize();
        return executor;
    }
}
