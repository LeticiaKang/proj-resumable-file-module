package com.example.tusminio.client.repository;

import com.example.tusminio.client.entity.TusUploadUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * TUS 업로드 URL 저장소
 *
 * TusURLDatabaseStore에서 사용하며, fingerprint를 키로
 * 업로드 URL의 CRUD 작업을 처리한다.
 */
@Repository
public interface TusUploadUrlRepository extends JpaRepository<TusUploadUrl, String> {
}
