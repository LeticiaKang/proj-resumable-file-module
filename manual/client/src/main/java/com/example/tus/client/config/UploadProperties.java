package com.example.tus.client.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 업로드 동시성 설정 프로퍼티
 *
 * <p>application.yml의 "tus.upload" 접두사 아래 설정을 바인딩합니다.</p>
 *
 * <pre>
 * tus.upload:
 *   max-concurrent: 3      # 동시 업로드 최대 수
 *   thread-pool-size: 5    # 업로드 스레드 풀 코어 크기
 * </pre>
 *
 * <p><b>동시성 제어 설명:</b></p>
 * <ul>
 *   <li>max-concurrent: Semaphore를 사용하여 동시에 진행할 수 있는 업로드 수를 제한합니다.
 *       서버의 부하를 적절히 분산시키기 위한 설정입니다.</li>
 *   <li>thread-pool-size: 업로드 작업을 비동기로 처리하는 스레드 풀의 기본 크기입니다.
 *       최대 풀 크기는 이 값의 2배로 설정됩니다.</li>
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "tus.upload")
public class UploadProperties {

    /**
     * 동시에 업로드할 수 있는 최대 파일 수
     * Semaphore로 동시성을 제어합니다.
     */
    private int maxConcurrent = 3;

    /**
     * 업로드 작업 처리 스레드 풀의 코어 크기
     * 최대 풀 크기는 이 값의 2배로 자동 설정됩니다.
     */
    private int threadPoolSize = 5;
}
