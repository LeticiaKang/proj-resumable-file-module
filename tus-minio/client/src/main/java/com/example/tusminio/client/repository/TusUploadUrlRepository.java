package com.example.tusminio.client.repository;

import com.example.tusminio.client.entity.TusUploadUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TusUploadUrlRepository extends JpaRepository<TusUploadUrl, String> {
}
