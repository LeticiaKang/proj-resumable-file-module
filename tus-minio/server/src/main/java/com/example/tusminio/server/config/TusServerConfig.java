package com.example.tusminio.server.config;

import com.example.tusminio.server.config.ValidationProperties;
import com.example.tusminio.server.filter.TusFilter;
import com.example.tusminio.server.repository.FileInfoRepository;
import com.example.tusminio.server.service.CallbackService;
import com.example.tusminio.server.service.ChecksumService;
import com.example.tusminio.server.service.MinioStorageService;
import com.example.tusminio.server.service.UploadProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.desair.tus.server.TusFileUploadService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * TUS 서버 핵심 빈 설정.
 * <p>
 * tus-java-server 라이브러리의 {@link TusFileUploadService}를 Spring 빈으로 등록합니다.
 * 이 서비스가 TUS 프로토콜의 모든 요청(POST, PATCH, HEAD, DELETE, OPTIONS)을 처리합니다.
 * </p>
 *
 * <h3>tus-java-server 라이브러리 연동:</h3>
 * <ul>
 *   <li>withStoragePath(): 업로드 청크가 저장될 로컬 디스크 경로</li>
 *   <li>withMaxUploadSize(): 허용되는 최대 업로드 크기</li>
 *   <li>withUploadExpirationPeriod(): 업로드 세션 만료 시간 (밀리초)</li>
 * </ul>
 *
 * <h3>TusFilter 등록:</h3>
 * <p>
 * TusFilter를 /files/* 경로에 매핑하여, 해당 경로로 들어오는 모든 요청을
 * tus-java-server 라이브러리가 처리하도록 합니다.
 * 중요: TusFilter는 chain.doFilter()를 호출하지 않습니다.
 * tus-java-server가 응답을 직접 완성하기 때문입니다.
 * </p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class TusServerConfig {

    private final TusServerProperties tusServerProperties;
    private final ExpirationProperties expirationProperties;

    /**
     * tus-java-server 라이브러리의 핵심 서비스 빈.
     * TUS 프로토콜 요청을 처리하고, 업로드 파일을 로컬 디스크에 관리
     */
    @Bean
    public TusFileUploadService tusFileUploadService() {
        return new TusFileUploadService()
                .withStoragePath(tusServerProperties.getStoragePath())
                .withMaxUploadSize(tusServerProperties.getMaxUploadSize())
                .withUploadUri("/files")
                .withUploadExpirationPeriod(expirationProperties.getTimeout().toMillis());
    }

    /**
     * TusFilter를 Bean으로 직접 생성 (FilterRegistrationBean에서만 등록하여 이중 등록 방지)
     */
    @Bean
    public TusFilter tusFilter(TusFileUploadService tusFileUploadService,
                                FileInfoRepository fileInfoRepository,
                                ValidationProperties validationProperties,
                                ChecksumService checksumService,
                                MinioStorageService minioStorageService,
                                CallbackService callbackService,
                                UploadProgressService uploadProgressService) {
        return new TusFilter(tusFileUploadService, fileInfoRepository,
                validationProperties, checksumService, minioStorageService,
                callbackService, uploadProgressService, tusServerProperties);
    }

    /**
     * FilterRegistrationBean을 사용하여 특정 URL 패턴에만 필터를 적용
     */
    @Bean
    public FilterRegistrationBean<TusFilter> tusFilterRegistration(TusFilter tusFilter) {
        FilterRegistrationBean<TusFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(tusFilter);
        registration.addUrlPatterns("/files", "/files/*");
        registration.setName("tusFilter");
        registration.setOrder(1);
        return registration;
    }
}
