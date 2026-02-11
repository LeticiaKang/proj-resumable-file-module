package com.example.tusminio.server.repository;

import com.example.tusminio.server.entity.FileInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileInfoRepository extends JpaRepository<FileInfo, Long> {

    Optional<FileInfo> findByUploadUri(String uploadUri);

    List<FileInfo> findByStatus(String status);

    List<FileInfo> findByStatusAndUpdDateBefore(String status, LocalDateTime before);
}
