package com.example.tusminio.server.service;

import com.example.tusminio.server.config.MinioProperties;
import com.example.tusminio.server.entity.FileInfo;
import com.example.tusminio.server.repository.FileInfoRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.desair.tus.server.TusFileUploadService;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * MinIO 오브젝트 스토리지 전송 서비스.
 * <p>
 * tus-java-server 라이브러리가 로컬 디스크에 저장한 완료된 업로드 파일을
 * MinIO 오브젝트 스토리지 버킷으로 전송합니다.
 * </p>
 *
 * <h3>로컬 → MinIO 전송 패턴:</h3>
 * <ol>
 *   <li>tus-java-server의 getUploadedBytes(uploadUri)로 파일 InputStream 획득</li>
 *   <li>MinioClient.putObject()로 MinIO 버킷에 업로드</li>
 *   <li>전송 완료 후, 설정에 따라 tus-java-server의 deleteUpload()로 로컬 파일 삭제</li>
 *   <li>DB 상태를 "transferred"로 갱신하고 minioObjectKey 기록</li>
 * </ol>
 *
 * <h3>MinIO 오브젝트 키 규칙:</h3>
 * <p>"{uploadId}/{fileName}" 형식으로 저장하여 업로드별 고유성을 보장합니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final TusFileUploadService tusFileUploadService;
    private final FileInfoRepository fileInfoRepository;

    /**
     * 완료된 업로드 파일을 MinIO 버킷으로 전송합니다.
     *
     * @param uploadUri TUS 업로드 URI (tus-java-server에서 파일을 식별하는 키)
     * @param fileInfo  업로드 파일 정보 엔티티
     */
    public void transferToMinio(String uploadUri, FileInfo fileInfo) {
        String fileName = fileInfo.getFileName();
        log.info("=== [MINIO] 전송 시작 fileName={} ===", fileName);

        try {
            // 1. tus-java-server로부터 업로드된 파일의 InputStream 획득
            //    getUploadedBytes()는 완료된 업로드의 전체 바이트를 InputStream으로 반환
            try (InputStream uploadedBytes = tusFileUploadService.getUploadedBytes(uploadUri)) {
                if (uploadedBytes == null) {
                    log.error("=== [MINIO] 업로드 파일을 찾을 수 없음: {} ===", uploadUri);
                    fileInfo.markFailed();
                    fileInfoRepository.save(fileInfo);
                    return;
                }

                // 2. MinIO 오브젝트 키 생성: "{uploadId}/{fileName}"
                //    uploadUri에서 마지막 경로 세그먼트를 uploadId로 사용
                String uploadId = extractUploadId(uploadUri);
                String objectKey = uploadId + "/" + fileName;

                // 3. MinIO 버킷에 PutObject로 파일 업로드
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(minioProperties.getBucket())
                                .object(objectKey)
                                .stream(uploadedBytes, fileInfo.getTotalSize(), -1)
                                .build()
                );

                log.info("=== [MINIO] 전송 완료 objectKey={} ===", objectKey);

                // 4. 로컬 임시 파일은 즉시 삭제하지 않음
                //    tus-java-client가 업로드 완료 후 추가 확인 요청을 보낼 수 있으므로,
                //    즉시 삭제하면 404가 발생하여 클라이언트가 실패로 판단함.
                //    로컬 파일 정리는 ExpirationService가 스케줄링으로 처리함.
                if (minioProperties.isDeleteLocalAfterTransfer()) {
                    log.debug("=== [MINIO] 로컬 파일은 ExpirationService가 정리 예정: {} ===", uploadUri);
                }

                // 5. DB 상태 갱신: "transferred" + minioObjectKey 기록
                fileInfo.markTransferred(objectKey);
                fileInfoRepository.save(fileInfo);
            }
        } catch (Exception e) {
            log.error("=== [MINIO] 전송 실패 fileName={}: {} ===", fileName, e.getMessage(), e);
            fileInfo.markFailed();
            fileInfoRepository.save(fileInfo);
        }
    }

    /**
     * 업로드 URI에서 uploadId(마지막 경로 세그먼트)를 추출합니다.
     * 예: "/files/1a2b3c4d-5e6f-..." → "1a2b3c4d-5e6f-..."
     */
    private String extractUploadId(String uploadUri) {
        if (uploadUri == null) {
            return "unknown";
        }
        int lastSlash = uploadUri.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < uploadUri.length() - 1) {
            return uploadUri.substring(lastSlash + 1);
        }
        return uploadUri;
    }
}
