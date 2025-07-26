package com.claude.springbatch.service;

import com.claude.springbatch.entity.Advertiser;
import com.claude.springbatch.entity.AdvertiserProcessingState;
import com.claude.springbatch.repository.AdvertiserRepository;
import com.claude.springbatch.repository.AdvertiserProcessingStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 광고주 관리 서비스
 * 
 * 광고주 생성, 수정, 삭제 및 백필 관리 기능 제공
 * 신규 광고주 추가 시 자동으로 2년 백필 설정
 */
@Service
@Slf4j
public class AdvertiserManagementService {
    
    private final AdvertiserRepository advertiserRepository;
    private final AdvertiserProcessingStateRepository stateRepository;
    
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    
    public AdvertiserManagementService(
            AdvertiserRepository advertiserRepository,
            AdvertiserProcessingStateRepository stateRepository) {
        this.advertiserRepository = advertiserRepository;
        this.stateRepository = stateRepository;
    }
    
    /**
     * 신규 광고주 생성
     * 
     * 신규 광고주는 자동으로 2년 전부터 현재까지의 백필 처리가 설정됩니다.
     * 
     * @param advertiser 생성할 광고주 정보
     * @return 생성된 광고주
     */
    @Transactional
    public Advertiser createNewAdvertiser(Advertiser advertiser) {
        log.info("신규 광고주 생성 시작: {}", advertiser.getAdvertiserId());
        
        // 신규 광고주로 초기화
        advertiser.initializeAsNewAdvertiser();
        
        // 저장
        Advertiser savedAdvertiser = advertiserRepository.save(advertiser);
        
        // 백필 처리 상태 초기화
        initializeBackfillStates(savedAdvertiser);
        
        log.info("신규 광고주 생성 완료: {} - 백필 기간: {}부터 현재까지", 
            savedAdvertiser.getAdvertiserId(), savedAdvertiser.getBackfillStartMonth());
        
        return savedAdvertiser;
    }
    
    /**
     * 기존 광고주를 신규 광고주로 변환 (백필 처리 필요)
     * 
     * @param advertiserId 광고주 ID
     * @return 변환된 광고주
     */
    @Transactional
    public Optional<Advertiser> convertToNewAdvertiser(String advertiserId) {
        return advertiserRepository.findByAdvertiserId(advertiserId)
            .map(advertiser -> {
                log.info("광고주 {}를 신규 광고주로 변환 중", advertiserId);
                
                advertiser.initializeAsNewAdvertiser();
                Advertiser savedAdvertiser = advertiserRepository.save(advertiser);
                
                // 기존 처리 상태 삭제
                deleteExistingProcessingStates(advertiserId);
                
                // 백필 처리 상태 초기화
                initializeBackfillStates(savedAdvertiser);
                
                log.info("광고주 {} 신규 광고주 변환 완료", advertiserId);
                return savedAdvertiser;
            });
    }
    
    /**
     * 백필 처리 상태 초기화
     * 
     * 2년 전부터 현재까지의 각 월에 대해 PENDING 상태의 처리 상태를 생성
     * 
     * @param advertiser 신규 광고주
     */
    private void initializeBackfillStates(Advertiser advertiser) {
        String advertiserId = advertiser.getAdvertiserId();
        
        // 백필 시작 월부터 현재 월까지 순회
        LocalDate startDate = LocalDate.parse(advertiser.getBackfillStartMonth() + "-01");
        LocalDate currentDate = LocalDate.now().withDayOfMonth(1);
        
        LocalDate processDate = startDate;
        int monthCount = 0;
        
        while (!processDate.isAfter(currentDate)) {
            String monthStr = processDate.format(MONTH_FORMATTER);
            
            // 해당 월의 처리 상태가 이미 존재하는지 확인
            Optional<AdvertiserProcessingState> existingState = 
                stateRepository.findByAdvertiserIdAndProcessingMonth(advertiserId, monthStr);
            
            if (existingState.isEmpty()) {
                // 새로운 처리 상태 생성
                AdvertiserProcessingState state = new AdvertiserProcessingState();
                state.setAdvertiserId(advertiserId);
                state.setProcessingMonth(monthStr);
                state.setStatus(AdvertiserProcessingState.ProcessingStatus.PENDING);
                
                stateRepository.save(state);
                monthCount++;
            }
            
            processDate = processDate.plusMonths(1);
        }
        
        log.info("광고주 {} 백필 상태 초기화 완료 - 총 {}개월", advertiserId, monthCount);
    }
    
    /**
     * 기존 처리 상태 삭제
     * 
     * @param advertiserId 광고주 ID
     */
    private void deleteExistingProcessingStates(String advertiserId) {
        List<AdvertiserProcessingState> existingStates = 
            stateRepository.findByAdvertiserIdOrderByProcessingMonthDesc(advertiserId);
        
        if (!existingStates.isEmpty()) {
            stateRepository.deleteAll(existingStates);
            log.info("광고주 {} 기존 처리 상태 {}개 삭제", advertiserId, existingStates.size());
        }
    }
    
    /**
     * 광고주 백필 진행 상태 조회
     * 
     * @param advertiserId 광고주 ID
     * @return 백필 진행 상태 정보
     */
    @Transactional(readOnly = true)
    public BackfillProgress getBackfillProgress(String advertiserId) {
        Optional<Advertiser> advertiserOpt = advertiserRepository.findByAdvertiserId(advertiserId);
        if (advertiserOpt.isEmpty()) {
            return null;
        }
        
        Advertiser advertiser = advertiserOpt.get();
        if (!advertiser.isNewAdvertiser()) {
            return new BackfillProgress(advertiserId, false, 100.0, 0, 0, 0, 0);
        }
        
        List<AdvertiserProcessingState> allStates = 
            stateRepository.findByAdvertiserIdOrderByProcessingMonthDesc(advertiserId);
        
        int totalMonths = allStates.size();
        long completedMonths = allStates.stream()
            .mapToLong(state -> state.isCompleted() ? 1 : 0)
            .sum();
        long failedMonths = allStates.stream()
            .mapToLong(state -> state.isFailed() ? 1 : 0)
            .sum();
        long runningMonths = allStates.stream()
            .mapToLong(state -> state.isProcessing() ? 1 : 0)
            .sum();
        
        double progressPercentage = totalMonths > 0 ? (double) completedMonths / totalMonths * 100.0 : 0.0;
        
        return new BackfillProgress(
            advertiserId,
            !advertiser.needsBackfillProcessing(),
            progressPercentage,
            totalMonths,
            (int) completedMonths,
            (int) failedMonths,
            (int) runningMonths
        );
    }
    
    /**
     * 실패한 월 재시도
     * 
     * @param advertiserId 광고주 ID
     * @param processingMonth 재시도할 월
     * @return 재시도 성공 여부
     */
    @Transactional
    public boolean retryFailedMonth(String advertiserId, String processingMonth) {
        Optional<AdvertiserProcessingState> stateOpt = 
            stateRepository.findByAdvertiserIdAndProcessingMonth(advertiserId, processingMonth);
        
        if (stateOpt.isEmpty()) {
            log.warn("재시도할 처리 상태를 찾을 수 없음: {} - {}", advertiserId, processingMonth);
            return false;
        }
        
        AdvertiserProcessingState state = stateOpt.get();
        if (!state.canRetry()) {
            log.warn("재시도 불가능한 상태: {} - {} (상태: {}, 재시도 횟수: {}/{})", 
                advertiserId, processingMonth, state.getStatus(), 
                state.getRetryCount(), state.getMaxRetries());
            return false;
        }
        
        state.prepareRetry();
        stateRepository.save(state);
        
        log.info("월 {} 재시도 준비 완료: {}", processingMonth, advertiserId);
        return true;
    }
    
    /**
     * 광고주 비활성화
     * 
     * @param advertiserId 광고주 ID
     */
    @Transactional
    public void deactivateAdvertiser(String advertiserId) {
        advertiserRepository.findByAdvertiserId(advertiserId)
            .ifPresentOrElse(
                advertiser -> {
                    advertiser.setStatus(Advertiser.AdvertiserStatus.INACTIVE);
                    advertiser.setBatchEnabled(false);
                    advertiserRepository.save(advertiser);
                    log.info("광고주 {} 비활성화 완료", advertiserId);
                },
                () -> log.warn("비활성화할 광고주 {}를 찾을 수 없음", advertiserId)
            );
    }
    
    /**
     * 광고주 활성화
     * 
     * @param advertiserId 광고주 ID
     */
    @Transactional
    public void activateAdvertiser(String advertiserId) {
        advertiserRepository.findByAdvertiserId(advertiserId)
            .ifPresentOrElse(
                advertiser -> {
                    advertiser.setStatus(Advertiser.AdvertiserStatus.ACTIVE);
                    advertiser.setBatchEnabled(true);
                    advertiser.setFailureCount(0);
                    advertiserRepository.save(advertiser);
                    log.info("광고주 {} 활성화 완료", advertiserId);
                },
                () -> log.warn("활성화할 광고주 {}를 찾을 수 없음", advertiserId)
            );
    }
    
    /**
     * 시스템 전체 통계 조회
     * 
     * @return 시스템 통계 정보
     */
    @Transactional(readOnly = true)
    public SystemStats getSystemStats() {
        List<Advertiser> newAdvertisers = advertiserRepository.findNewAdvertisersNeedingBackfill();
        List<Advertiser> existingAdvertisers = advertiserRepository.findExistingAdvertisers();
        
        Object[] stateStats = stateRepository.findSystemStats();
        
        // [totalAdvertisers, totalJobs, completedJobs, failedJobs, runningJobs]
        int totalAdvertisers = ((Number) stateStats[0]).intValue();
        int totalJobs = ((Number) stateStats[1]).intValue();
        int completedJobs = ((Number) stateStats[2]).intValue();
        int failedJobs = ((Number) stateStats[3]).intValue();
        int runningJobs = ((Number) stateStats[4]).intValue();
        
        return new SystemStats(
            newAdvertisers.size(),
            existingAdvertisers.size(),
            totalJobs,
            completedJobs,
            failedJobs,
            runningJobs
        );
    }
    
    /**
     * 백필 진행 상태 정보 클래스
     */
    public static class BackfillProgress {
        public final String advertiserId;
        public final boolean completed;
        public final double progressPercentage;
        public final int totalMonths;
        public final int completedMonths;
        public final int failedMonths;
        public final int runningMonths;
        
        public BackfillProgress(String advertiserId, boolean completed, double progressPercentage,
                              int totalMonths, int completedMonths, int failedMonths, int runningMonths) {
            this.advertiserId = advertiserId;
            this.completed = completed;
            this.progressPercentage = progressPercentage;
            this.totalMonths = totalMonths;
            this.completedMonths = completedMonths;
            this.failedMonths = failedMonths;
            this.runningMonths = runningMonths;
        }
    }
    
    /**
     * 시스템 통계 정보 클래스
     */
    public static class SystemStats {
        public final int newAdvertisers;
        public final int existingAdvertisers;
        public final int totalJobs;
        public final int completedJobs;
        public final int failedJobs;
        public final int runningJobs;
        
        public SystemStats(int newAdvertisers, int existingAdvertisers, int totalJobs,
                          int completedJobs, int failedJobs, int runningJobs) {
            this.newAdvertisers = newAdvertisers;
            this.existingAdvertisers = existingAdvertisers;
            this.totalJobs = totalJobs;
            this.completedJobs = completedJobs;
            this.failedJobs = failedJobs;
            this.runningJobs = runningJobs;
        }
    }
}