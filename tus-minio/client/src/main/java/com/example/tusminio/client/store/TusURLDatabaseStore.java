package com.example.tusminio.client.store;

import com.example.tusminio.client.entity.TusUploadUrl;
import com.example.tusminio.client.repository.TusUploadUrlRepository;
import io.tus.java.client.TusURLStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

/**
 * TUS 업로드 URL을 PostgreSQL에 저장하는 TusURLStore 구현체
 *
 * tus-java-client의 TusClient.enableResuming(TusURLStore) 메서드에 이 구현체를 전달하면,
 * 라이브러리가 내부적으로 다음과 같이 동작한다:
 *
 * 1. 새 업로드 생성 시: set(fingerprint, uploadUrl) 호출 → DB에 URL 저장
 * 2. 이어받기 시도 시: get(fingerprint) 호출 → DB에서 URL 조회
 *    - URL이 존재하면 HEAD 요청으로 서버의 현재 offset을 확인하고 이어받기
 *    - URL이 없으면 새 업로드를 생성
 * 3. 업로드 완료 시: remove(fingerprint) 호출 → DB에서 URL 삭제 (선택적)
 *
 * 이렇게 DB에 저장하면 애플리케이션 재시작 후에도 이어받기가 가능하다.
 * (기본 TusURLMemoryStore는 메모리에만 저장하므로 재시작 시 유실됨)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TusURLDatabaseStore implements TusURLStore {

    private final TusUploadUrlRepository repository;

    /**
     * 업로드 URL을 DB에 저장 또는 갱신
     *
     * tus-java-client가 TUS 서버로부터 Creation 응답(201 + Location 헤더)을
     * 받은 직후 이 메서드를 호출하여 업로드 URL을 영속화한다.
     *
     * @param fingerprint 파일 고유 식별자 (파일 경로 + 크기 기반)
     * @param url         TUS 서버가 반환한 업로드 URL
     */
    @Override
    public void set(String fingerprint, URL url) {
        log.debug("=== [URL STORE] SET fingerprint={}, url={} ===", fingerprint, url);

        Optional<TusUploadUrl> existing = repository.findById(fingerprint);

        if (existing.isPresent()) {
            // 기존 레코드가 있으면 URL만 갱신
            TusUploadUrl entity = existing.get();
            entity.setUploadUrl(url.toString());
            repository.save(entity);
            log.debug("=== [URL STORE] 기존 URL 갱신 완료 ===");
        } else {
            // 새 레코드 생성
            TusUploadUrl entity = TusUploadUrl.builder()
                    .fingerprint(fingerprint)
                    .uploadUrl(url.toString())
                    .build();
            repository.save(entity);
            log.debug("=== [URL STORE] 신규 URL 저장 완료 ===");
        }
    }

    /**
     * fingerprint로 저장된 업로드 URL 조회
     *
     * tus-java-client가 이어받기를 시도할 때 이 메서드를 호출한다.
     * URL이 존재하면 HEAD 요청으로 서버의 Upload-Offset을 확인하고,
     * 해당 offset 이후부터 청크를 전송한다.
     *
     * @param fingerprint 파일 고유 식별자
     * @return 저장된 업로드 URL, 없으면 null
     */
    @Override
    public URL get(String fingerprint) {
        log.debug("=== [URL STORE] GET fingerprint={} ===", fingerprint);

        Optional<TusUploadUrl> entity = repository.findById(fingerprint);

        if (entity.isPresent()) {
            try {
                URL url = new URL(entity.get().getUploadUrl());
                log.debug("=== [URL STORE] 저장된 URL 발견: {} ===", url);
                return url;
            } catch (MalformedURLException e) {
                log.error("=== [URL STORE] 잘못된 URL 형식: {} ===", entity.get().getUploadUrl(), e);
                // 잘못된 URL은 삭제하고 null 반환 (새 업로드 생성 유도)
                repository.deleteById(fingerprint);
                return null;
            }
        }

        log.debug("=== [URL STORE] 저장된 URL 없음 (신규 업로드 필요) ===");
        return null;
    }

    /**
     * fingerprint에 해당하는 업로드 URL 삭제
     *
     * 업로드 완료 후 더 이상 이어받기가 필요 없을 때 호출된다.
     * DB에서 해당 레코드를 삭제하여 불필요한 데이터 축적을 방지한다.
     *
     * @param fingerprint 파일 고유 식별자
     */
    @Override
    public void remove(String fingerprint) {
        log.debug("=== [URL STORE] REMOVE fingerprint={} ===", fingerprint);

        if (repository.existsById(fingerprint)) {
            repository.deleteById(fingerprint);
            log.debug("=== [URL STORE] URL 삭제 완료 ===");
        } else {
            log.debug("=== [URL STORE] 삭제할 URL 없음 (이미 삭제되었거나 존재하지 않음) ===");
        }
    }
}
