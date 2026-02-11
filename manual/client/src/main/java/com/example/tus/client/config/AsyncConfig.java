package com.example.tus.client.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 처리 설정 클래스
 *
 * <p>배치(다중 파일) 업로드 시 비동기 처리를 위한 스레드 풀을 구성합니다.</p>
 *
 * <h3>스레드 풀 구성:</h3>
 * <ul>
 *   <li><b>corePoolSize:</b> 기본적으로 유지되는 스레드 수 (tus.upload.thread-pool-size)</li>
 *   <li><b>maxPoolSize:</b> 최대 스레드 수 (corePoolSize * 2) - 큐가 가득 차면 이 수까지 확장</li>
 *   <li><b>queueCapacity:</b> 100 - 코어 스레드가 모두 사용 중일 때 대기할 수 있는 작업 수</li>
 *   <li><b>threadNamePrefix:</b> "tus-upload-" - 로그에서 업로드 스레드를 쉽게 식별 가능</li>
 * </ul>
 *
 * <p><b>동작 순서:</b></p>
 * <ol>
 *   <li>새 작업이 제출되면 corePoolSize 이하인 경우 새 스레드를 생성</li>
 *   <li>corePoolSize에 도달하면 큐에 작업을 추가 (최대 100개)</li>
 *   <li>큐도 가득 차면 maxPoolSize까지 스레드를 추가 생성</li>
 *   <li>maxPoolSize도 초과하면 RejectedExecutionException 발생</li>
 * </ol>
 */
@Slf4j
@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AsyncConfig {

    private final UploadProperties uploadProperties;

    /**
     * 업로드 전용 스레드 풀 Executor 빈 생성
     *
     * <p>빈 이름이 "uploadExecutor"이므로, 서비스에서
     * {@code @Async("uploadExecutor")} 또는
     * {@code @Qualifier("uploadExecutor")}로 주입받아 사용합니다.</p>
     *
     * @return 구성된 ThreadPoolTaskExecutor (Executor 타입으로 반환)
     */
    @Bean(name = "uploadExecutor")
    public Executor uploadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 코어 스레드 수: 기본적으로 유지되는 스레드 수
        executor.setCorePoolSize(uploadProperties.getThreadPoolSize());

        // 최대 스레드 수: 부하가 높을 때까지 확장할 수 있는 최대 스레드 수
        executor.setMaxPoolSize(uploadProperties.getThreadPoolSize() * 2);

        // 큐 용량: 코어 스레드가 모두 사용 중일 때 대기할 수 있는 작업 수
        executor.setQueueCapacity(100);

        // 스레드 이름 접두사: 로그에서 업로드 관련 스레드를 쉽게 식별
        executor.setThreadNamePrefix("tus-upload-");

        // 스레드 풀 초기화
        executor.initialize();

        log.info("=== [ASYNC CONFIG] uploadExecutor 초기화 완료 - core={}, max={}, queue={} ===",
                uploadProperties.getThreadPoolSize(),
                uploadProperties.getThreadPoolSize() * 2,
                100);

        return executor;
    }
}
