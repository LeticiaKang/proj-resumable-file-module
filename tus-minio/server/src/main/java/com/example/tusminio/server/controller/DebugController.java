package com.example.tusminio.server.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 디버그/테스트용 컨트롤러.
 * <p>
 * 라이브 데모에서 네트워크 장애를 시뮬레이션하기 위한 엔드포인트입니다.
 * TUS 프로토콜의 재개(resume) 기능을 테스트할 때 사용합니다.
 * </p>
 *
 * <h3>사용 시나리오:</h3>
 * <ol>
 *   <li>POST /api/debug/fail-next-patch 호출하여 실패 시뮬레이션 활성화</li>
 *   <li>다음 PATCH 요청 시 TusFilter가 500 에러 반환</li>
 *   <li>클라이언트가 HEAD로 오프셋 확인 후 재개(resume)</li>
 *   <li>실패 플래그는 자동으로 해제됨 (1회성)</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/api/debug")
public class DebugController {

    /**
     * 다음 PATCH 요청 실패 여부 플래그.
     * AtomicBoolean으로 스레드 안전성을 보장합니다.
     * compareAndSet으로 1회성 동작을 보장합니다.
     */
    private static final AtomicBoolean failNextPatch = new AtomicBoolean(false);

    /**
     * 다음 PATCH 요청을 실패시킬지 확인하고, 플래그를 자동 해제합니다.
     * TusFilter에서 PATCH 처리 시 호출됩니다.
     *
     * @return true면 500 에러 반환해야 함 (1회성, 호출 즉시 해제)
     */
    public static boolean shouldFailNextPatch() {
        return failNextPatch.compareAndSet(true, false);
    }

    /**
     * 다음 PATCH 요청에 대한 실패 시뮬레이션을 활성화합니다.
     * 1회성으로 동작하며, 다음 PATCH 요청 후 자동 해제됩니다.
     *
     * @return 활성화 상태 메시지
     */
    @PostMapping("/fail-next-patch")
    public ResponseEntity<Map<String, Object>> enableFailNextPatch() {
        failNextPatch.set(true);
        log.info("=== [DEBUG] 다음 PATCH 실패 시뮬레이션 활성화 ===");
        return ResponseEntity.ok(Map.of(
                "failNextPatch", true,
                "message", "다음 PATCH 요청이 500 에러를 반환합니다 (1회성)"
        ));
    }

    /**
     * 현재 실패 시뮬레이션 상태를 조회합니다.
     *
     * @return 현재 failNextPatch 플래그 상태
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "failNextPatch", failNextPatch.get()
        ));
    }
}
