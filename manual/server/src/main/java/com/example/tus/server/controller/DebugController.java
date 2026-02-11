package com.example.tus.server.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 디버그/시연용 장애 시뮬레이션 컨트롤러.
 * <p>
 * 발표 시연 시 TUS 프로토콜의 재개(resume) 기능을 보여주기 위해
 * 의도적으로 PATCH 요청을 실패시킬 수 있습니다.
 * </p>
 * <p>
 * 사용 시나리오:
 * 1. POST /api/debug/fail-next-patch → 다음 PATCH 요청 1회 실패 예약
 * 2. 클라이언트가 PATCH 전송 → 서버가 500 에러 반환
 * 3. 클라이언트가 HEAD로 현재 오프셋 확인 → 이어서 PATCH 재전송
 * 이 과정을 통해 TUS의 재개 기능을 시각적으로 확인할 수 있습니다.
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/debug")
public class DebugController {

    /**
     * 다음 PATCH 요청 실패 여부 플래그.
     * AtomicBoolean을 사용하여 스레드 안전하게 관리합니다.
     */
    private static final AtomicBoolean failNextPatch = new AtomicBoolean(false);

    /**
     * 다음 PATCH 요청을 실패시키도록 예약합니다.
     * 한 번만 실패하고 자동으로 해제됩니다.
     *
     * @return 설정 결과 메시지
     */
    @PostMapping("/fail-next-patch")
    public ResponseEntity<String> setFailNextPatch() {
        failNextPatch.set(true);
        log.warn("=== [DEBUG] 다음 PATCH 요청 실패 예정! ===");
        return ResponseEntity.ok("다음 PATCH 요청 실패 예정");
    }

    /**
     * 장애 시뮬레이션 플래그를 초기화합니다.
     *
     * @return 초기화 결과 메시지
     */
    @PostMapping("/reset")
    public ResponseEntity<String> reset() {
        failNextPatch.set(false);
        log.info("=== [DEBUG] 장애 시뮬레이션 플래그 초기화 ===");
        return ResponseEntity.ok("디버그 상태 초기화 완료");
    }

    /**
     * 현재 디버그 상태를 조회합니다.
     *
     * @return 현재 디버그 플래그 상태 JSON
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("failNextPatch", failNextPatch.get());
        log.debug("=== [DEBUG] 디버그 상태 조회: failNextPatch={} ===", failNextPatch.get());
        return ResponseEntity.ok(status);
    }

    /**
     * 다음 PATCH 요청을 실패시켜야 하는지 확인합니다.
     * <p>
     * compareAndSet을 사용하여 true인 경우 한 번만 true를 반환하고
     * 자동으로 false로 리셋됩니다. (1회성 실패 시뮬레이션)
     * </p>
     *
     * @return true이면 PATCH 요청을 실패시켜야 함
     */
    public static boolean shouldFailNextPatch() {
        // compareAndSet: 현재 true이면 false로 변경하고 true 반환 (1회성)
        return failNextPatch.compareAndSet(true, false);
    }
}
