package com.claude.springbatch.service;

import com.claude.springbatch.entity.Advertiser;
import com.claude.springbatch.repository.AdvertiserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/**
 * 광고주 순환 배치 처리 서비스
 * 
 * 핵심 기능:
 * 1. 라운드로빈 방식으로 광고주들 사이에서 배치 작업을 순환 처리
 * 2. 실패한 광고주를 격리하여 다른 광고주 처리에 영향을 주지 않음
 * 3. 실패 횟수 추적 및 자동 복구 메커니즘 제공
 * 4. 동시성 안전성을 위한 AtomicInteger와 ConcurrentHashMap 사용
 * 
 * 동작 원리:
 * - currentIndex: 현재 처리할 광고주의 인덱스 (원자적 연산으로 스레드 안전)
 * - advertiserFailureCount: 광고주별 실패 횟수를 메모리에 캐시하여 빠른 접근
 * - DB 트랜잭션과 메모리 캐시를 조합하여 성능과 일관성 모두 확보
 */
@Service
@Slf4j
public class AdvertiserRotationService {
    
    private final AdvertiserRepository advertiserRepository;
    
    // 현재 처리중인 광고주의 인덱스 (스레드 안전한 원자적 정수)
    // 라운드로빈 순환을 위해 계속 증가하며, 광고주 수로 나누어 순환
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    
    // 광고주별 실패 횟수를 메모리에 캐시 (동시 접근 안전한 해시맵)
    // DB 조회 없이 빠른 실패 횟수 체크가 가능하여 성능 향상
    private final ConcurrentHashMap<String, Integer> advertiserFailureCount = new ConcurrentHashMap<>();
    
    /**
     * 생성자 주입을 통한 의존성 주입
     * @param advertiserRepository 광고주 데이터 접근을 위한 레포지토리
     */
    public AdvertiserRotationService(AdvertiserRepository advertiserRepository) {
        this.advertiserRepository = advertiserRepository;
    }
    
    /**
     * 라운드로빈 방식으로 다음 배치 처리할 광고주를 선택
     * 
     * 동작 순서:
     * 1. DB에서 배치 처리 가능한 광고주 목록 조회 (ACTIVE, 실패 한도 미달, 실행중 아님)
     * 2. 가능한 광고주가 없으면 Optional.empty() 반환
     * 3. 원자적 연산으로 다음 인덱스 계산 (currentIndex를 1 증가 후 광고주 수로 모듈로 연산)
     * 4. 선택된 광고주를 RUNNING 상태로 변경하여 중복 실행 방지
     * 5. DB에 상태 저장 후 선택된 광고주 반환
     * 
     * 스레드 안전성:
     * - AtomicInteger.getAndUpdate()로 인덱스 변경이 원자적으로 수행
     * - @Transactional로 DB 상태 변경의 일관성 보장
     * - 동시에 여러 스레드가 호출해도 안전하게 다른 광고주 선택
     */
    @Transactional
    public Optional<Advertiser> getNextAdvertiserForBatch() {
        // 1단계: 배치 처리 가능한 광고주 목록 조회
        // SQL 조건: batchEnabled=true AND status='ACTIVE' AND failureCount < maxFailures AND lastBatchStatus != 'RUNNING'
        // 정렬 순서: rotationPriority ASC, lastBatchRun ASC (오래된 것부터)
        List<Advertiser> eligibleAdvertisers = advertiserRepository.findEligibleForBatch();
        
        // 2단계: 가능한 광고주가 없는 경우 처리
        if (eligibleAdvertisers.isEmpty()) {
            log.warn("배치 처리 가능한 광고주가 없습니다 - 모든 광고주가 비활성화되었거나 실패 한도에 도달");
            return Optional.empty();
        }
        
        log.info("배치 처리 가능한 광고주 {}명 발견", eligibleAdvertisers.size());
        
        // 3단계: 라운드로빈 순환 선택 구현
        // getAndUpdate()는 현재 값을 반환하고 원자적으로 업데이트
        // (i + 1) % size로 0, 1, 2, ..., size-1, 0, 1, ... 순환
        int nextIndex = currentIndex.getAndUpdate(i -> (i + 1) % eligibleAdvertisers.size());
        Advertiser selectedAdvertiser = eligibleAdvertisers.get(nextIndex);
        
        // 4단계: 선택된 광고주를 RUNNING 상태로 변경하여 중복 실행 방지
        // 이는 다른 스케줄러 스레드가 동시에 같은 광고주를 선택하는 것을 방지
        selectedAdvertiser.recordRunning();
        advertiserRepository.save(selectedAdvertiser);
        
        log.info("라운드로빈으로 광고주 {} 선택됨 (인덱스: {}/{})", 
                selectedAdvertiser.getAdvertiserId(), nextIndex, eligibleAdvertisers.size());
        
        // 5단계: 선택된 광고주 반환
        return Optional.of(selectedAdvertiser);
    }
    
    /**
     * 배치 작업 성공 기록
     * 
     * 성공 처리 과정:
     * 1. 광고주의 lastBatchStatus를 SUCCESS로 변경
     * 2. lastBatchRun을 현재 시간으로 업데이트
     * 3. failureCount를 0으로 초기화 (연속 실패 카운터 리셋)
     * 4. 메모리 캐시에서 실패 횟수 정보 제거
     * 
     * 이 메서드는 광고주가 배치 처리에 성공했을 때 호출되어
     * 해당 광고주를 다시 정상 순환에 포함시킵니다.
     */
    @Transactional
    public void recordBatchSuccess(String advertiserId) {
        advertiserRepository.findByAdvertiserId(advertiserId)
                .ifPresentOrElse(
                    advertiser -> {
                        // 성공 상태 기록: 시간 업데이트, 실패 카운터 리셋
                        advertiser.recordSuccess();
                        advertiserRepository.save(advertiser);
                        
                        // 메모리 캐시에서 실패 기록 삭제 (성공으로 복구됨)
                        advertiserFailureCount.remove(advertiserId);
                        
                        log.info("광고주 {} 배치 작업 성공 기록 완료", advertiserId);
                    },
                    () -> log.warn("성공 기록할 광고주 {}를 찾을 수 없음", advertiserId)
                );
    }
    
    /**
     * 배치 작업 실패 기록 및 격리 처리
     * 
     * 실패 처리 과정:
     * 1. 광고주의 lastBatchStatus를 FAILED로 변경
     * 2. failureCount 증가
     * 3. lastBatchRun을 현재 시간으로 업데이트
     * 4. 메모리 캐시에 실패 횟수 업데이트
     * 5. 최대 실패 횟수 초과 시 자동 비활성화
     * 
     * 격리 메커니즘:
     * - 실패한 광고주는 FAILED 상태가 되어 즉시 순환에서 제외
     * - 다른 광고주들은 영향받지 않고 계속 처리됨
     * - 최대 실패 횟수(기본 3회) 초과 시 batchEnabled=false로 완전 비활성화
     * 
     * @param advertiserId 실패한 광고주 ID
     * @param exception 발생한 예외 (로깅용)
     */
    @Transactional
    public void recordBatchFailure(String advertiserId, Exception exception) {
        advertiserRepository.findByAdvertiserId(advertiserId)
                .ifPresentOrElse(
                    advertiser -> {
                        // 실패 상태 기록: 시간 업데이트, 실패 카운터 증가
                        advertiser.recordFailure();
                        advertiserRepository.save(advertiser);
                        
                        // 메모리 캐시에 실패 횟수 업데이트 (원자적 증가)
                        int failureCount = advertiserFailureCount.merge(advertiserId, 1, Integer::sum);
                        
                        log.error("광고주 {} 배치 작업 실패 기록 (실패 횟수: {}). 오류: {}", 
                                advertiserId, failureCount, exception.getMessage());
                        
                        // 최대 실패 횟수 초과 시 경고 및 자동 비활성화
                        if (advertiser.getFailureCount() >= advertiser.getMaxFailures()) {
                            log.warn("광고주 {}가 최대 실패 횟수를 초과하여 자동 비활성화됨", advertiserId);
                        }
                    },
                    () -> log.warn("실패 기록할 광고주 {}를 찾을 수 없음", advertiserId)
                );
    }
    
    @Transactional(readOnly = true)
    public List<Advertiser> getEligibleAdvertisers() {
        return advertiserRepository.findEligibleForBatch();
    }
    
    @Transactional(readOnly = true)
    public List<Advertiser> getRunningBatchJobs() {
        return advertiserRepository.findRunningBatchJobs();
    }
    
    @Transactional
    public void resetFailedAdvertiser(String advertiserId) {
        advertiserRepository.findByAdvertiserId(advertiserId)
                .ifPresentOrElse(
                    advertiser -> {
                        advertiser.setFailureCount(0);
                        advertiser.setBatchEnabled(true);
                        advertiser.setLastBatchStatus(Advertiser.BatchStatus.PENDING);
                        advertiserRepository.save(advertiser);
                        advertiserFailureCount.remove(advertiserId);
                        log.info("Reset failure count for advertiser: {}", advertiserId);
                    },
                    () -> log.warn("Advertiser not found for reset: {}", advertiserId)
                );
    }
    
    @Transactional(readOnly = true)
    public Optional<Advertiser> getAdvertiserById(String advertiserId) {
        return advertiserRepository.findByAdvertiserId(advertiserId);
    }
    
    public void refreshRotationIndex() {
        List<Advertiser> eligibleAdvertisers = advertiserRepository.findEligibleForBatch();
        if (!eligibleAdvertisers.isEmpty()) {
            currentIndex.set(0);
            log.info("Refreshed rotation index. {} eligible advertisers available", eligibleAdvertisers.size());
        }
    }
}