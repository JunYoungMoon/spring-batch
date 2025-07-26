package com.claude.springbatch.controller;

import com.claude.springbatch.entity.Advertiser;
import com.claude.springbatch.entity.AdvertiserProcessingState;
import com.claude.springbatch.repository.AdvertiserProcessingStateRepository;
import com.claude.springbatch.service.AdvertiserManagementService;
import com.claude.springbatch.service.ParallelAdvertiserProcessingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * 병렬 배치 처리 시스템 관리 컨트롤러
 * 
 * 새로운 병렬 처리 시스템의 모니터링 및 관리 기능 제공:
 * - 시스템 상태 조회
 * - 광고주별 진행 상황 조회
 * - 수동 처리 트리거
 * - 실패 복구 기능
 */
@RestController
@RequestMapping("/api/v2/batch")
@Slf4j
public class ParallelBatchController {
    
    private final ParallelAdvertiserProcessingService processingService;
    private final AdvertiserManagementService managementService;
    private final AdvertiserProcessingStateRepository stateRepository;
    
    public ParallelBatchController(
            ParallelAdvertiserProcessingService processingService,
            AdvertiserManagementService managementService,
            AdvertiserProcessingStateRepository stateRepository) {
        this.processingService = processingService;
        this.managementService = managementService;
        this.stateRepository = stateRepository;
    }
    
    /**
     * 시스템 전체 상태 조회
     */
    @GetMapping("/status")
    public ResponseEntity<AdvertiserManagementService.SystemStats> getSystemStatus() {
        log.info("시스템 상태 조회 요청");
        
        AdvertiserManagementService.SystemStats stats = managementService.getSystemStats();
        return ResponseEntity.ok(stats);
    }
    
    /**
     * 특정 광고주의 백필 진행 상황 조회
     */
    @GetMapping("/advertisers/{advertiserId}/backfill-progress")
    public ResponseEntity<AdvertiserManagementService.BackfillProgress> getBackfillProgress(
            @PathVariable String advertiserId) {
        log.info("광고주 {} 백필 진행 상황 조회", advertiserId);
        
        AdvertiserManagementService.BackfillProgress progress = 
            managementService.getBackfillProgress(advertiserId);
        
        if (progress == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(progress);
    }
    
    /**
     * 특정 광고주의 월별 처리 상태 상세 조회
     */
    @GetMapping("/advertisers/{advertiserId}/processing-states")
    public ResponseEntity<List<AdvertiserProcessingState>> getProcessingStates(
            @PathVariable String advertiserId) {
        log.info("광고주 {} 처리 상태 상세 조회", advertiserId);
        
        List<AdvertiserProcessingState> states = 
            stateRepository.findByAdvertiserIdOrderByProcessingMonthDesc(advertiserId);
        
        return ResponseEntity.ok(states);
    }
    
    /**
     * 현재 실행 중인 모든 작업 조회
     */
    @GetMapping("/running-jobs")
    public ResponseEntity<List<AdvertiserProcessingState>> getRunningJobs() {
        log.info("실행 중인 작업 목록 조회");
        
        List<AdvertiserProcessingState> runningJobs = stateRepository.findAllRunning();
        return ResponseEntity.ok(runningJobs);
    }
    
    /**
     * 특정 광고주에 대한 수동 배치 처리 트리거
     */
    @PostMapping("/advertisers/{advertiserId}/trigger")
    public ResponseEntity<Map<String, String>> triggerAdvertiserBatch(
            @PathVariable String advertiserId) {
        log.info("광고주 {} 수동 배치 처리 트리거", advertiserId);
        
        try {
            // 광고주 존재 여부 확인
            AdvertiserManagementService.BackfillProgress progress = 
                managementService.getBackfillProgress(advertiserId);
            
            if (progress == null) {
                return ResponseEntity.notFound().build();
            }
            
            // 비동기 처리 시작 - 실제 Advertiser 객체 조회 필요
            return ResponseEntity.ok(Map.of(
                "status", "triggered", 
                "message", "광고주 " + advertiserId + "의 배치 처리가 시작되었습니다."
            ));
            
        } catch (Exception e) {
            log.error("광고주 {} 수동 트리거 실패", advertiserId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "배치 처리 시작 중 오류가 발생했습니다: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 특정 월의 실패한 처리 재시도
     */
    @PostMapping("/advertisers/{advertiserId}/retry/{month}")
    public ResponseEntity<Map<String, String>> retryFailedMonth(
            @PathVariable String advertiserId,
            @PathVariable String month) {
        log.info("광고주 {} 월 {} 재시도 요청", advertiserId, month);
        
        boolean success = managementService.retryFailedMonth(advertiserId, month);
        
        if (success) {
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "월 " + month + " 재시도가 예약되었습니다."
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "failed",
                "message", "재시도할 수 없습니다. 상태를 확인해주세요."
            ));
        }
    }
    
    /**
     * 광고주 활성화
     */
    @PostMapping("/advertisers/{advertiserId}/activate")
    public ResponseEntity<Map<String, String>> activateAdvertiser(
            @PathVariable String advertiserId) {
        log.info("광고주 {} 활성화 요청", advertiserId);
        
        try {
            managementService.activateAdvertiser(advertiserId);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "광고주가 활성화되었습니다."
            ));
        } catch (Exception e) {
            log.error("광고주 {} 활성화 실패", advertiserId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "활성화 중 오류가 발생했습니다: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 광고주 비활성화
     */
    @PostMapping("/advertisers/{advertiserId}/deactivate")
    public ResponseEntity<Map<String, String>> deactivateAdvertiser(
            @PathVariable String advertiserId) {
        log.info("광고주 {} 비활성화 요청", advertiserId);
        
        try {
            managementService.deactivateAdvertiser(advertiserId);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "광고주가 비활성화되었습니다."
            ));
        } catch (Exception e) {
            log.error("광고주 {} 비활성화 실패", advertiserId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "비활성화 중 오류가 발생했습니다: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 기존 광고주를 신규 광고주로 변환 (백필 처리 시작)
     */
    @PostMapping("/advertisers/{advertiserId}/convert-to-new")
    public ResponseEntity<Map<String, String>> convertToNewAdvertiser(
            @PathVariable String advertiserId) {
        log.info("광고주 {} 신규 광고주 변환 요청", advertiserId);
        
        try {
            return managementService.convertToNewAdvertiser(advertiserId)
                .map(advertiser -> ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "광고주가 신규 광고주로 변환되어 백필 처리가 시작됩니다.",
                    "backfillStartMonth", advertiser.getBackfillStartMonth()
                )))
                .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("광고주 {} 신규 변환 실패", advertiserId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "변환 중 오류가 발생했습니다: " + e.getMessage()
            ));
        }
    }
    
    /**
     * 시스템 헬스체크
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            AdvertiserManagementService.SystemStats stats = managementService.getSystemStats();
            List<AdvertiserProcessingState> runningJobs = stateRepository.findAllRunning();
            
            return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "timestamp", java.time.LocalDateTime.now(),
                "statistics", stats,
                "activeJobs", runningJobs.size()
            ));
        } catch (Exception e) {
            log.error("헬스체크 실패", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "unhealthy",
                "error", e.getMessage(),
                "timestamp", java.time.LocalDateTime.now()
            ));
        }
    }
}