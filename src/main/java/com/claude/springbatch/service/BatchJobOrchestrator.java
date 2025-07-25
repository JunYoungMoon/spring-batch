package com.claude.springbatch.service;

import com.claude.springbatch.entity.Advertiser;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 배치 작업 오케스트레이터 - 다중 광고주 CDP 배치 처리 조정자
 * 
 * 핵심 역할:
 * 1. 스케줄링: 정기적으로 (30초마다) 다음 광고주를 선택하여 배치 작업 실행
 * 2. 비동기 처리: CompletableFuture를 사용하여 배치 작업을 병렬로 실행
 * 3. 타임아웃 관리: 배치 작업이 5분을 초과하면 강제 종료
 * 4. 상태 관리: 성공/실패 상태를 RotationService에 기록
 * 5. 헬스 체크: 30분 이상 실행되는 스테일 작업 정리
 * 
 * 장애 격리 전략:
 * - 각 광고주별로 독립적인 배치 작업 실행
 * - 한 광고주의 실패가 다른 광고주에게 영향을 주지 않음
 * - 타임아웃으로 무한 대기 방지
 * - 실패한 광고주는 자동으로 순환에서 제외
 */
@Service
@Slf4j
public class BatchJobOrchestrator {
    
    private final JobLauncher jobLauncher;                    // Spring Batch 작업 실행기
    private final Job advertiserSpecificCdpJob;              // 광고주별 CDP 배치 작업 정의
    private final AdvertiserRotationService rotationService; // 라운드로빈 순환 서비스
    
    /**
     * 생성자 주입을 통한 의존성 주입
     */
    public BatchJobOrchestrator(JobLauncher jobLauncher,
                              Job advertiserSpecificCdpJob,
                              AdvertiserRotationService rotationService) {
        this.jobLauncher = jobLauncher;
        this.advertiserSpecificCdpJob = advertiserSpecificCdpJob;
        this.rotationService = rotationService;
    }
    
    /**
     * 배치 작업 오케스트레이션 메인 루프
     * 
     * 스케줄링: @Scheduled(fixedDelay = 30000)
     * - 이전 실행이 완료된 후 30초 대기 후 다음 실행
     * - fixedRate가 아닌 fixedDelay를 사용하여 오버랩 방지
     * 
     * 전체 처리 흐름:
     * 1. RotationService에서 다음 처리할 광고주 선택 (라운드로빈)
     * 2. 선택된 광고주가 없으면 현재 사이클 종료
     * 3. CompletableFuture로 비동기 배치 작업 시작
     * 4. 5분 타임아웃 설정으로 무한 대기 방지
     * 5. 성공/실패 상태를 RotationService에 기록
     * 
     * 장애 격리 보장:
     * - try-catch로 예외가 전체 스케줄링을 중단하지 않도록 방지
     * - 개별 광고주 실패가 다른 광고주에게 영향 없음
     * - 타임아웃으로 멈춘 작업이 시스템 리소스를 계속 점유하지 않도록 처리
     */
    @Scheduled(fixedDelay = 30000) // 30초마다 실행 (이전 실행 완료 후 대기)
    public void orchestrateBatchJobs() {
        log.debug("배치 작업 오케스트레이션 사이클 시작");
        
        try {
            // 1단계: 라운드로빈 방식으로 다음 광고주 선택
            Optional<Advertiser> nextAdvertiser = rotationService.getNextAdvertiserForBatch();
            
            // 2단계: 처리 가능한 광고주가 없으면 현재 사이클 종료
            if (nextAdvertiser.isEmpty()) {
                log.debug("배치 처리 가능한 광고주가 없어서 현재 사이클 건너뜀");
                return;
            }
            
            Advertiser advertiser = nextAdvertiser.get();
            log.info("광고주 {} 배치 작업 시작", advertiser.getAdvertiserId());
            
            // 3단계: 비동기 배치 작업 실행 (CompletableFuture 사용)
            // 비동기 처리로 스케줄러 스레드가 블록되지 않도록 함
            CompletableFuture<Void> batchExecution = CompletableFuture.runAsync(() -> {
                try {
                    executeBatchJob(advertiser);
                } catch (Exception e) {
                    log.error("광고주 {} 비동기 배치 실행 실패", advertiser.getAdvertiserId(), e);
                    // 실패 시 즉시 RotationService에 기록하여 다음 순환에서 제외
                    rotationService.recordBatchFailure(advertiser.getAdvertiserId(), e);
                }
            });
            
            // 4단계: 5분 타임아웃 설정으로 무한 대기 방지
            // 배치 작업이 너무 오래 걸리면 강제 종료하여 시스템 안정성 확보
            try {
                batchExecution.get(5, TimeUnit.MINUTES);
                log.debug("광고주 {} 배치 작업이 타임아웃 내에 완료됨", advertiser.getAdvertiserId());
            } catch (Exception e) {
                log.error("광고주 {} 배치 작업 타임아웃 또는 실패", advertiser.getAdvertiserId(), e);
                // 타임아웃도 실패로 간주하여 실패 횟수에 포함
                rotationService.recordBatchFailure(advertiser.getAdvertiserId(), e);
                // 진행중인 작업 강제 취소
                batchExecution.cancel(true);
            }
            
        } catch (Exception e) {
            // 5단계: 전체 오케스트레이션 오류 처리
            // 어떤 예외도 스케줄러 자체를 중단시키지 않도록 보호
            log.error("배치 작업 오케스트레이션 중 오류 발생", e);
        }
        
        log.debug("배치 작업 오케스트레이션 사이클 완료");
    }
    
    private void executeBatchJob(Advertiser advertiser) {
        String advertiserId = advertiser.getAdvertiserId();
        
        try {
            // Create job parameters with advertiser ID and timestamp
            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("advertiserId", advertiserId)
                    .addLong("timestamp", System.currentTimeMillis())
                    .addString("jobId", "advertiser-" + advertiserId + "-" + System.currentTimeMillis())
                    .toJobParameters();
            
            log.info("Executing batch job for advertiser: {} with parameters: {}", 
                     advertiserId, jobParameters);
            
            // Launch the job
            JobExecution jobExecution = jobLauncher.run(advertiserSpecificCdpJob, jobParameters);
            
            // Check job execution status
            if (jobExecution.getStatus().isUnsuccessful()) {
                String errorMessage = "Job execution failed with status: " + jobExecution.getStatus();
                log.error("Batch job failed for advertiser: {} - {}", advertiserId, errorMessage);
                rotationService.recordBatchFailure(advertiserId, new RuntimeException(errorMessage));
            } else {
                log.info("Batch job completed successfully for advertiser: {} with status: {}", 
                         advertiserId, jobExecution.getStatus());
                rotationService.recordBatchSuccess(advertiserId);
            }
            
        } catch (Exception e) {
            log.error("Exception during batch job execution for advertiser: {}", advertiserId, e);
            rotationService.recordBatchFailure(advertiserId, e);
        }
    }
    
    // Manual trigger for testing or admin purposes
    public void triggerBatchForAdvertiser(String advertiserId) {
        Optional<Advertiser> advertiser = rotationService.getAdvertiserById(advertiserId);
        
        if (advertiser.isEmpty()) {
            log.warn("Advertiser not found for manual trigger: {}", advertiserId);
            return;
        }
        
        if (!advertiser.get().isEligibleForBatch()) {
            log.warn("Advertiser is not eligible for batch processing: {}", advertiserId);
            return;
        }
        
        log.info("Manual batch job trigger for advertiser: {}", advertiserId);
        
        CompletableFuture.runAsync(() -> {
            try {
                advertiser.get().recordRunning();
                executeBatchJob(advertiser.get());
            } catch (Exception e) {
                log.error("Manual batch execution failed for advertiser: {}", advertiserId, e);
                rotationService.recordBatchFailure(advertiserId, e);
            }
        });
    }
    
    // Health check and cleanup
    @Scheduled(fixedDelay = 300000) // Run every 5 minutes
    public void healthCheckAndCleanup() {
        log.debug("Running batch job health check and cleanup");
        
        try {
            // Check for jobs that have been running too long
            List<Advertiser> runningJobs = rotationService.getRunningBatchJobs();
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30);
            
            for (Advertiser advertiser : runningJobs) {
                if (advertiser.getLastBatchRun() != null && 
                    advertiser.getLastBatchRun().isBefore(cutoffTime)) {
                    
                    log.warn("Found stale running job for advertiser: {}, marking as failed", 
                             advertiser.getAdvertiserId());
                    
                    rotationService.recordBatchFailure(advertiser.getAdvertiserId(), 
                                                     new RuntimeException("Job timeout - marked as stale"));
                }
            }
            
            // Refresh rotation index to ensure fair distribution
            rotationService.refreshRotationIndex();
            
        } catch (Exception e) {
            log.error("Error during health check and cleanup", e);
        }
    }
    
    // Get system status
    public BatchSystemStatus getSystemStatus() {
        List<Advertiser> eligible = rotationService.getEligibleAdvertisers();
        List<Advertiser> running = rotationService.getRunningBatchJobs();
        
        return new BatchSystemStatus(
            eligible.size(),
            running.size(),
            LocalDateTime.now()
        );
    }
    
    public static class BatchSystemStatus {
        public final int eligibleAdvertisers;
        public final int runningJobs;
        public final LocalDateTime statusTime;
        
        public BatchSystemStatus(int eligibleAdvertisers, int runningJobs, LocalDateTime statusTime) {
            this.eligibleAdvertisers = eligibleAdvertisers;
            this.runningJobs = runningJobs;
            this.statusTime = statusTime;
        }
    }
}