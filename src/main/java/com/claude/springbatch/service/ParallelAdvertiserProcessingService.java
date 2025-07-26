package com.claude.springbatch.service;

import com.claude.springbatch.entity.Advertiser;
import com.claude.springbatch.entity.AdvertiserProcessingState;
import com.claude.springbatch.repository.AdvertiserRepository;
import com.claude.springbatch.repository.AdvertiserProcessingStateRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 병렬 광고주 처리 서비스
 * 
 * 핵심 기능:
 * 1. 모든 광고주의 배치 작업을 동시에 병렬 실행
 * 2. 기존 광고주: 현재 월 데이터만 처리
 * 3. 신규 광고주: 2년 전부터 현재까지 순차적 백필 처리
 * 4. 각 광고주별 독립적인 상태 관리 및 진행률 추적
 * 5. 중복 처리 방지 및 실패 복구 메커니즘
 * 
 * 처리 로직:
 * - 기존 광고주: 매월 현재 월 데이터만 처리 (예: 2024-12)
 * - 신규 광고주: 2023-01부터 시작하여 2024-12까지 순차 처리
 * - 각 광고주는 독립적으로 처리되므로 한 광고주의 실패가 다른 광고주에 영향 없음
 */
@Service
@Slf4j
public class ParallelAdvertiserProcessingService {
    
    private final AdvertiserRepository advertiserRepository;
    private final AdvertiserProcessingStateRepository stateRepository;
    private final JobLauncher jobLauncher;
    private final Job advertiserSpecificCdpJob;
    
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    
    public ParallelAdvertiserProcessingService(
            AdvertiserRepository advertiserRepository,
            AdvertiserProcessingStateRepository stateRepository,
            JobLauncher jobLauncher,
            Job advertiserSpecificCdpJob) {
        this.advertiserRepository = advertiserRepository;
        this.stateRepository = stateRepository;
        this.jobLauncher = jobLauncher;
        this.advertiserSpecificCdpJob = advertiserSpecificCdpJob;
    }
    
    /**
     * 메인 스케줄러: 모든 광고주의 배치 작업을 병렬로 시작
     * 
     * 실행 주기: 매 5분마다 (fixedDelay = 300000ms)
     * 이전 실행이 완료된 후 5분 대기하여 시스템 부하 조절
     * 
     * 처리 과정:
     * 1. 모든 활성 광고주 조회
     * 2. 각 광고주별로 다음 처리할 월 결정
     * 3. 병렬로 배치 작업 시작 (CompletableFuture 활용)
     * 4. 중복 처리 방지를 위한 상태 체크
     */
    @Scheduled(fixedDelay = 300000) // 5분마다 실행
    public void orchestrateParallelBatchJobs() {
        log.info("=== 병렬 배치 작업 오케스트레이션 시작 ===");
        
        try {
            // 1단계: 모든 활성 광고주 조회
            List<Advertiser> activeAdvertisers = advertiserRepository.findByStatusAndBatchEnabled(
                Advertiser.AdvertiserStatus.ACTIVE, true);
            
            if (activeAdvertisers.isEmpty()) {
                log.info("활성 광고주가 없어서 배치 작업을 건너뜁니다.");
                return;
            }
            
            log.info("총 {}명의 활성 광고주에 대해 병렬 배치 처리를 시작합니다.", activeAdvertisers.size());
            
            // 2단계: 각 광고주별로 병렬 처리 시작
            List<CompletableFuture<Void>> futures = activeAdvertisers.stream()
                .map(advertiser -> processAdvertiserAsync(advertiser))
                .collect(Collectors.toList());
            
            // 3단계: 모든 비동기 작업 완료 대기 (논블로킹)
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> log.info("=== 모든 광고주 배치 작업 완료 ==="))
                .exceptionally(throwable -> {
                    log.error("일부 광고주 배치 작업에서 오류 발생", throwable);
                    return null;
                });
                
        } catch (Exception e) {
            log.error("병렬 배치 작업 오케스트레이션 중 오류 발생", e);
        }
    }
    
    /**
     * 개별 광고주에 대한 비동기 처리
     * 
     * @param advertiser 처리할 광고주
     * @return CompletableFuture<Void> 비동기 작업 결과
     */
    @Async("batchTaskExecutor")
    public CompletableFuture<Void> processAdvertiserAsync(Advertiser advertiser) {
        return CompletableFuture.runAsync(() -> {
            try {
                processAdvertiser(advertiser);
            } catch (Exception e) {
                log.error("광고주 {} 처리 중 오류 발생", advertiser.getAdvertiserId(), e);
            }
        });
    }
    
    /**
     * 개별 광고주 처리 로직
     * 
     * 광고주 유형에 따른 처리 분기:
     * - 기존 광고주: 현재 월만 처리
     * - 신규 광고주: 백필 처리 (2년 전부터 순차적)
     * 
     * @param advertiser 처리할 광고주
     */
    @Transactional
    public void processAdvertiser(Advertiser advertiser) {
        String advertiserId = advertiser.getAdvertiserId();
        log.info("[{}] 광고주 처리 시작 - 유형: {}", advertiserId, advertiser.getAdvertiserType());
        
        try {
            if (advertiser.isNewAdvertiser() && advertiser.needsBackfillProcessing()) {
                // 신규 광고주: 백필 처리
                processNewAdvertiserBackfill(advertiser);
            } else {
                // 기존 광고주: 현재 월 처리
                processExistingAdvertiserCurrentMonth(advertiser);
            }
            
        } catch (Exception e) {
            log.error("[{}] 광고주 처리 중 오류 발생", advertiserId, e);
            advertiser.recordFailure();
            advertiserRepository.save(advertiser);
        }
    }
    
    /**
     * 기존 광고주 현재 월 처리
     * 
     * 처리 로직:
     * 1. 현재 월(YYYY-MM) 계산
     * 2. 이미 처리 중이거나 완료된 경우 건너뛰기
     * 3. 배치 작업 실행
     * 
     * @param advertiser 기존 광고주
     */
    private void processExistingAdvertiserCurrentMonth(Advertiser advertiser) {
        String advertiserId = advertiser.getAdvertiserId();
        String currentMonth = LocalDate.now().format(MONTH_FORMATTER);
        
        log.info("[{}] 기존 광고주 현재 월 처리: {}", advertiserId, currentMonth);
        
        // 중복 처리 방지 체크
        Optional<AdvertiserProcessingState> existingState = 
            stateRepository.findByAdvertiserIdAndProcessingMonth(advertiserId, currentMonth);
        
        if (existingState.isPresent()) {
            AdvertiserProcessingState state = existingState.get();
            if (state.isProcessing()) {
                log.info("[{}] 현재 월 {} 이미 처리 중이므로 건너뜁니다.", advertiserId, currentMonth);
                return;
            }
            if (state.isCompleted()) {
                log.info("[{}] 현재 월 {} 이미 처리 완료되었습니다.", advertiserId, currentMonth);
                return;
            }
            if (state.isFailed() && state.canRetry()) {
                log.info("[{}] 현재 월 {} 재시도 처리를 시작합니다.", advertiserId, currentMonth);
                state.prepareRetry();
                stateRepository.save(state);
            }
        }
        
        // 새로운 처리 상태 생성 또는 기존 상태 업데이트
        AdvertiserProcessingState processingState = existingState.orElse(
            new AdvertiserProcessingState());
        processingState.setAdvertiserId(advertiserId);
        processingState.setProcessingMonth(currentMonth);
        processingState.startProcessing();
        
        processingState = stateRepository.save(processingState);
        
        // 배치 작업 실행
        executeBatchJob(advertiser, currentMonth, processingState);
    }
    
    /**
     * 신규 광고주 백필 처리
     * 
     * 처리 로직:
     * 1. 다음 처리할 월 결정 (백필 순서에 따라)
     * 2. 백필 완료 조건 체크
     * 3. 순차적 월별 배치 작업 실행
     * 
     * @param advertiser 신규 광고주
     */
    private void processNewAdvertiserBackfill(Advertiser advertiser) {
        String advertiserId = advertiser.getAdvertiserId();
        log.info("[{}] 신규 광고주 백필 처리 시작", advertiserId);
        
        // 다음 처리할 월 계산
        String nextMonth = determineNextBackfillMonth(advertiser);
        if (nextMonth == null) {
            // 백필 완료
            completeBackfill(advertiser);
            return;
        }
        
        log.info("[{}] 백필 처리 월: {}", advertiserId, nextMonth);
        
        // 중복 처리 방지 체크
        Optional<AdvertiserProcessingState> existingState = 
            stateRepository.findByAdvertiserIdAndProcessingMonth(advertiserId, nextMonth);
        
        if (existingState.isPresent()) {
            AdvertiserProcessingState state = existingState.get();
            if (state.isProcessing()) {
                log.info("[{}] 백필 월 {} 이미 처리 중이므로 건너뜁니다.", advertiserId, nextMonth);
                return;
            }
            if (state.isCompleted()) {
                log.info("[{}] 백필 월 {} 이미 완료, 다음 월로 진행합니다.", advertiserId, nextMonth);
                advertiser.recordMonthCompleted(nextMonth);
                advertiserRepository.save(advertiser);
                return;
            }
        }
        
        // 새로운 처리 상태 생성
        AdvertiserProcessingState processingState = existingState.orElse(
            new AdvertiserProcessingState());
        processingState.setAdvertiserId(advertiserId);
        processingState.setProcessingMonth(nextMonth);
        processingState.startProcessing();
        
        processingState = stateRepository.save(processingState);
        
        // 배치 작업 실행
        executeBatchJob(advertiser, nextMonth, processingState);
    }
    
    /**
     * 다음 백필 처리할 월 결정
     * 
     * @param advertiser 신규 광고주
     * @return 다음 처리할 월 (YYYY-MM) 또는 null (백필 완료)
     */
    private String determineNextBackfillMonth(Advertiser advertiser) {
        String advertiserId = advertiser.getAdvertiserId();
        String currentMonth = LocalDate.now().format(MONTH_FORMATTER);
        
        // 백필 시작 월부터 현재 월까지 순차 확인
        LocalDate backfillStart = LocalDate.parse(advertiser.getBackfillStartMonth() + "-01");
        LocalDate currentDate = LocalDate.now().withDayOfMonth(1);
        
        LocalDate checkDate = backfillStart;
        while (!checkDate.isAfter(currentDate)) {
            String checkMonth = checkDate.format(MONTH_FORMATTER);
            
            // 해당 월이 완료되었는지 확인
            Optional<AdvertiserProcessingState> state = 
                stateRepository.findByAdvertiserIdAndProcessingMonth(advertiserId, checkMonth);
            
            if (state.isEmpty() || !state.get().isCompleted()) {
                return checkMonth; // 미완료 월 발견
            }
            
            checkDate = checkDate.plusMonths(1);
        }
        
        return null; // 모든 월 완료
    }
    
    /**
     * 백필 완료 처리
     * 
     * @param advertiser 백필을 완료한 신규 광고주
     */
    private void completeBackfill(Advertiser advertiser) {
        log.info("[{}] 신규 광고주 백필 처리 완료", advertiser.getAdvertiserId());
        
        advertiser.setBackfillCompleted(true);
        advertiser.setAdvertiserType(Advertiser.AdvertiserType.EXISTING);
        advertiserRepository.save(advertiser);
        
        log.info("[{}] 광고주가 기존 광고주로 전환되었습니다.", advertiser.getAdvertiserId());
    }
    
    /**
     * 실제 배치 작업 실행
     * 
     * @param advertiser 처리할 광고주
     * @param processingMonth 처리할 월
     * @param processingState 처리 상태 추적 객체
     */
    private void executeBatchJob(Advertiser advertiser, String processingMonth, 
                                AdvertiserProcessingState processingState) {
        String advertiserId = advertiser.getAdvertiserId();
        
        try {
            log.info("[{}] 배치 작업 실행 시작 - 월: {}", advertiserId, processingMonth);
            
            // Job Parameters 생성
            JobParameters jobParameters = new JobParametersBuilder()
                .addString("advertiserId", advertiserId)
                .addString("processingMonth", processingMonth)
                .addLong("timestamp", System.currentTimeMillis())
                .addString("jobId", String.format("advertiser-%s-%s-%d", 
                    advertiserId, processingMonth, System.currentTimeMillis()))
                .toJobParameters();
            
            // 배치 작업 실행
            JobExecution jobExecution = jobLauncher.run(advertiserSpecificCdpJob, jobParameters);
            
            // 실행 결과 처리
            if (jobExecution.getStatus().isUnsuccessful()) {
                handleJobFailure(advertiser, processingMonth, processingState, jobExecution);
            } else {
                handleJobSuccess(advertiser, processingMonth, processingState, jobExecution);
            }
            
        } catch (Exception e) {
            log.error("[{}] 배치 작업 실행 중 예외 발생 - 월: {}", advertiserId, processingMonth, e);
            handleJobException(advertiser, processingMonth, processingState, e);
        }
    }
    
    /**
     * 배치 작업 성공 처리
     */
    private void handleJobSuccess(Advertiser advertiser, String processingMonth, 
                                 AdvertiserProcessingState processingState, JobExecution jobExecution) {
        String advertiserId = advertiser.getAdvertiserId();
        
        log.info("[{}] 배치 작업 성공 - 월: {}, 상태: {}", 
            advertiserId, processingMonth, jobExecution.getStatus());
        
        // 처리 상태 업데이트
        long processedCount = jobExecution.getStepExecutions().stream()
            .mapToLong(step -> step.getWriteCount())
            .sum();
        
        processingState.completeProcessing(processedCount, 0L);
        stateRepository.save(processingState);
        
        // 광고주 상태 업데이트
        advertiser.recordSuccess();
        
        // 신규 광고주인 경우 월 완료 기록
        if (advertiser.isNewAdvertiser()) {
            advertiser.recordMonthCompleted(processingMonth);
        }
        
        advertiserRepository.save(advertiser);
        
        log.info("[{}] 월 {} 처리 완료 - 처리된 레코드: {}개", 
            advertiserId, processingMonth, processedCount);
    }
    
    /**
     * 배치 작업 실패 처리
     */
    private void handleJobFailure(Advertiser advertiser, String processingMonth, 
                                 AdvertiserProcessingState processingState, JobExecution jobExecution) {
        String advertiserId = advertiser.getAdvertiserId();
        String errorMessage = "Job execution failed with status: " + jobExecution.getStatus();
        
        log.error("[{}] 배치 작업 실패 - 월: {}, 오류: {}", advertiserId, processingMonth, errorMessage);
        
        // 처리 상태 업데이트
        long processedCount = jobExecution.getStepExecutions().stream()
            .mapToLong(step -> step.getWriteCount())
            .sum();
        long failedCount = jobExecution.getStepExecutions().stream()
            .mapToLong(step -> step.getSkipCount())
            .sum();
        
        processingState.failProcessing(errorMessage, processedCount, failedCount);
        stateRepository.save(processingState);
        
        // 광고주 실패 기록
        advertiser.recordFailure();
        advertiserRepository.save(advertiser);
    }
    
    /**
     * 배치 작업 예외 처리
     */
    private void handleJobException(Advertiser advertiser, String processingMonth, 
                                   AdvertiserProcessingState processingState, Exception exception) {
        String advertiserId = advertiser.getAdvertiserId();
        
        log.error("[{}] 배치 작업 예외 - 월: {}", advertiserId, processingMonth, exception);
        
        processingState.failProcessing(exception.getMessage(), 0L, 0L);
        stateRepository.save(processingState);
        
        advertiser.recordFailure();
        advertiserRepository.save(advertiser);
    }
    
    /**
     * 스테일 작업 정리 (30분 이상 실행 중인 작업들)
     */
    @Scheduled(fixedDelay = 600000) // 10분마다 실행
    public void cleanupStaleJobs() {
        log.debug("스테일 배치 작업 정리 시작");
        
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30);
        List<AdvertiserProcessingState> staleJobs = stateRepository.findStaleRunningJobs(cutoffTime);
        
        for (AdvertiserProcessingState staleJob : staleJobs) {
            log.warn("스테일 작업 감지 - 광고주: {}, 월: {}, 시작시간: {}", 
                staleJob.getAdvertiserId(), staleJob.getProcessingMonth(), staleJob.getStartedAt());
            
            staleJob.failProcessing("작업 타임아웃 - 스테일 작업으로 분류됨", 
                staleJob.getProcessedRecords(), staleJob.getFailedRecords());
            stateRepository.save(staleJob);
            
            // 광고주 상태도 실패로 업데이트
            advertiserRepository.findByAdvertiserId(staleJob.getAdvertiserId())
                .ifPresent(advertiser -> {
                    advertiser.recordFailure();
                    advertiserRepository.save(advertiser);
                });
        }
        
        log.debug("스테일 배치 작업 정리 완료 - 정리된 작업 수: {}", staleJobs.size());
    }
}