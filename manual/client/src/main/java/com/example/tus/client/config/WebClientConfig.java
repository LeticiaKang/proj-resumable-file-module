package com.example.tus.client.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * WebClient 설정 클래스
 *
 * <p>TUS 서버와의 HTTP 통신을 위한 WebClient를 구성합니다.</p>
 *
 * <h3>주요 설정:</h3>
 * <ul>
 *   <li><b>버퍼 크기:</b> 16MB - 대용량 청크 전송을 위해 기본값보다 큰 버퍼 할당</li>
 *   <li><b>기본 헤더:</b> Tus-Resumable: 1.0.0 - TUS 프로토콜 버전 명시 (모든 요청에 자동 포함)</li>
 *   <li><b>Base URL:</b> TUS 서버의 파일 엔드포인트 (예: http://localhost:8082/files)</li>
 * </ul>
 *
 * <p><b>TUS 프로토콜에서 Tus-Resumable 헤더:</b></p>
 * <p>모든 TUS 요청에는 반드시 Tus-Resumable 헤더가 포함되어야 합니다.
 * 이 헤더는 클라이언트가 지원하는 TUS 프로토콜 버전을 서버에 알려줍니다.
 * 현재 최신 버전은 1.0.0입니다.</p>
 *
 * <p>이 클래스는 또한 {@link EnableConfigurationProperties}를 통해
 * 모든 커스텀 프로퍼티 클래스를 등록하는 중앙 설정 역할을 합니다.</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({
        TusClientProperties.class,
        RetryProperties.class,
        UploadProperties.class
})
public class WebClientConfig {

    private final TusClientProperties tusClientProperties;

    /**
     * TUS 서버와 통신하기 위한 WebClient 빈 생성
     *
     * <p>설정 내역:</p>
     * <ol>
     *   <li>코덱 커스터마이저: 최대 메모리 버퍼 크기를 16MB로 설정</li>
     *   <li>기본 URL: TUS 서버 엔드포인트 (application.yml의 tus.server-url)</li>
     *   <li>기본 헤더: Tus-Resumable = 1.0.0 (TUS 프로토콜 필수 헤더)</li>
     *   <li>요청/응답 로깅 필터 추가</li>
     *   <li>연결 타임아웃: 30초</li>
     * </ol>
     *
     * @return 구성 완료된 WebClient 인스턴스
     */
    @Bean
    public WebClient webClient() {
        // === [WebClient 설정] 16MB 메모리 버퍼 제한 설정 ===
        // 대용량 청크를 전송하기 위해 기본 256KB보다 큰 버퍼가 필요합니다.
        int bufferSize = 16 * 1024 * 1024; // 16MB
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(bufferSize))
                .build();

        // === [WebClient 설정] Reactor Netty HTTP 클라이언트 (타임아웃 설정) ===
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(30));

        log.info("=== [WEBCLIENT CONFIG] baseUrl={}, bufferSize={}MB ===",
                tusClientProperties.getServerUrl(), bufferSize / (1024 * 1024));

        return WebClient.builder()
                // TUS 서버 Base URL 설정
                .baseUrl(tusClientProperties.getServerUrl())
                // TUS 프로토콜 필수 헤더: 모든 요청에 자동으로 포함됨
                .defaultHeader("Tus-Resumable", "1.0.0")
                // 16MB 메모리 버퍼 전략 적용
                .exchangeStrategies(strategies)
                // Reactor Netty 커넥터 (타임아웃 포함)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // 요청 로깅 필터
                .filter(logRequest())
                // 응답 로깅 필터
                .filter(logResponse())
                .build();
    }

    /**
     * HTTP 요청 로깅 필터
     * 모든 나가는 요청의 메서드와 URL을 DEBUG 레벨로 기록합니다.
     */
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.debug("=== [HTTP REQUEST] {} {} ===", clientRequest.method(), clientRequest.url());
            clientRequest.headers().forEach((name, values) ->
                    values.forEach(value -> log.debug("  >> Header: {}={}", name, value)));
            return Mono.just(clientRequest);
        });
    }

    /**
     * HTTP 응답 로깅 필터
     * 모든 응답의 상태 코드를 DEBUG 레벨로 기록합니다.
     */
    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            log.debug("=== [HTTP RESPONSE] status={} ===", clientResponse.statusCode());
            return Mono.just(clientResponse);
        });
    }
}
