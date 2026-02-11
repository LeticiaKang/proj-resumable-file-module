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
 * TTusClient.enableResuming(TusURLStore)
 *
 * 1. 새 업로드 생성 시: set(fingerprint, uploadUrl) 호출 → DB에 URL 저장
 * 2. 이어받기 시도 시: get(fingerprint) 호출 → DB에서 URL 조회
 *    - URL이 존재하면 HEAD 요청으로 서버의 현재 offset을 확인하고 이어받기
 *    - URL이 없으면 새 업로드를 생성
 * 3. 업로드 완료 시: remove(fingerprint) 호출 → DB에서 URL 삭제 (선택적)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TusURLDatabaseStore implements TusURLStore {

    private final TusUploadUrlRepository repository;

    /**
     * 업로드 URL을 DB에 저장 또는 갱신
     * 흐름 :
     * 1. POST /files → 서버가 201 Created + Location URL 반환
     * 2. 서버가 이 업로드 전용 URL을 발급해주면, set(fingerprint, locationUrl) → DB에 저장
     * 3. 이후 클라이언트는 이 URL로 PATCH 요청을 보내서 청크 단위로 파일 전송
     */
    @Override
    @Transactional
    public void set(String fingerprint, URL url) {
        log.debug("=== [URL STORE] SET fingerprint={}, url={} ===", fingerprint, url);

        Optional<TusUploadUrl> existing = repository.findById(fingerprint);

        TusUploadUrl entity = repository.findById(fingerprint)
                .map(existing -> {
                    existing.updateUploadUrl(url.toString());
                    return existing;
                })
                .orElse(TusUploadUrl.builder()
                        .fingerprint(fingerprint)
                        .uploadUrl(url.toString())
                        .build());

        repository.save(entity);
        log.debug("=== [URL STORE] URL 저장/갱신 완료 ===");
    }

    /**
     * fingerprint로 저장된 업로드 URL 조회(이어받기 시)
     * URL이 존재하면 HEAD 요청으로 서버의 Upload-Offset을 확인하여 이어받음

     * @return 저장된 업로드 URL, 없으면 null
     */
    @Override
    @Transactional(readOnly = true)
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
     */
    @Override
    @Transactional
    public void remove(String fingerprint) {
        log.debug("=== [URL STORE] 삭제 fingerprint={} ===", fingerprint);
        repository.deleteById(fingerprint);
        log.debug("=== [URL STORE] URL 삭제 완료 ===");
    }
}
