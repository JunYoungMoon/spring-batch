package com.claude.springbatch.repository;

import com.claude.springbatch.entity.AdvertiserProcessingState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 광고주 처리 상태 레포지토리
 * 
 * 광고주별 월별 처리 상태를 관리하고 조회하는 기능 제공
 */
@Repository
public interface AdvertiserProcessingStateRepository extends JpaRepository<AdvertiserProcessingState, Long> {
    
    /**
     * 특정 광고주의 특정 월 처리 상태 조회
     */
    Optional<AdvertiserProcessingState> findByAdvertiserIdAndProcessingMonth(
        String advertiserId, String processingMonth);
    
    /**
     * 특정 광고주의 모든 처리 상태 조회 (최신순)
     */
    @Query("SELECT aps FROM AdvertiserProcessingState aps " +
           "WHERE aps.advertiserId = :advertiserId " +
           "ORDER BY aps.processingMonth DESC")
    List<AdvertiserProcessingState> findByAdvertiserIdOrderByProcessingMonthDesc(
        @Param("advertiserId") String advertiserId);
    
    /**
     * 특정 광고주의 완료된 처리 상태들 조회
     */
    @Query("SELECT aps FROM AdvertiserProcessingState aps " +
           "WHERE aps.advertiserId = :advertiserId " +
           "AND aps.status = 'COMPLETED' " +
           "ORDER BY aps.processingMonth ASC")
    List<AdvertiserProcessingState> findCompletedByAdvertiserId(
        @Param("advertiserId") String advertiserId);
    
    /**
     * 특정 광고주의 실패한 처리 상태들 조회 (재시도 가능한 것들)
     */
    @Query("SELECT aps FROM AdvertiserProcessingState aps " +
           "WHERE aps.advertiserId = :advertiserId " +
           "AND aps.status = 'FAILED' " +
           "AND aps.retryCount < aps.maxRetries " +
           "ORDER BY aps.processingMonth ASC")
    List<AdvertiserProcessingState> findRetryableFailedByAdvertiserId(
        @Param("advertiserId") String advertiserId);
    
    /**
     * 현재 실행 중인 모든 처리 작업 조회
     */
    @Query("SELECT aps FROM AdvertiserProcessingState aps " +
           "WHERE aps.status IN ('RUNNING', 'RETRYING') " +
           "ORDER BY aps.startedAt ASC")
    List<AdvertiserProcessingState> findAllRunning();
    
    /**
     * 특정 시간 이전에 시작되어 아직 실행 중인 작업들 조회 (스테일 작업 감지용)
     */
    @Query("SELECT aps FROM AdvertiserProcessingState aps " +
           "WHERE aps.status IN ('RUNNING', 'RETRYING') " +
           "AND aps.startedAt < :cutoffTime " +
           "ORDER BY aps.startedAt ASC")
    List<AdvertiserProcessingState> findStaleRunningJobs(
        @Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * 특정 월에 처리 중인 광고주들 조회 (중복 처리 방지용)
     */
    @Query("SELECT aps FROM AdvertiserProcessingState aps " +
           "WHERE aps.processingMonth = :month " +
           "AND aps.status IN ('RUNNING', 'RETRYING') " +
           "ORDER BY aps.advertiserId ASC")
    List<AdvertiserProcessingState> findRunningJobsForMonth(
        @Param("month") String month);
    
    /**
     * 특정 광고주의 마지막 완료된 월 조회
     */
    @Query("SELECT aps.processingMonth FROM AdvertiserProcessingState aps " +
           "WHERE aps.advertiserId = :advertiserId " +
           "AND aps.status = 'COMPLETED' " +
           "ORDER BY aps.processingMonth DESC " +
           "LIMIT 1")
    Optional<String> findLastCompletedMonth(@Param("advertiserId") String advertiserId);
    
    /**
     * 특정 광고주의 처리 진행률 통계 조회
     */
    @Query("SELECT COUNT(aps) as total, " +
           "SUM(CASE WHEN aps.status = 'COMPLETED' THEN 1 ELSE 0 END) as completed, " +
           "SUM(CASE WHEN aps.status = 'FAILED' THEN 1 ELSE 0 END) as failed, " +
           "SUM(CASE WHEN aps.status IN ('RUNNING', 'RETRYING') THEN 1 ELSE 0 END) as running " +
           "FROM AdvertiserProcessingState aps " +
           "WHERE aps.advertiserId = :advertiserId")
    Object[] findProcessingStatsForAdvertiser(@Param("advertiserId") String advertiserId);
    
    /**
     * 전체 시스템 처리 통계 조회
     */
    @Query("SELECT COUNT(DISTINCT aps.advertiserId) as totalAdvertisers, " +
           "COUNT(aps) as totalJobs, " +
           "SUM(CASE WHEN aps.status = 'COMPLETED' THEN 1 ELSE 0 END) as completedJobs, " +
           "SUM(CASE WHEN aps.status = 'FAILED' THEN 1 ELSE 0 END) as failedJobs, " +
           "SUM(CASE WHEN aps.status IN ('RUNNING', 'RETRYING') THEN 1 ELSE 0 END) as runningJobs " +
           "FROM AdvertiserProcessingState aps")
    Object[] findSystemStats();
    
    /**
     * 특정 기간 내 처리된 작업들 조회
     */
    @Query("SELECT aps FROM AdvertiserProcessingState aps " +
           "WHERE aps.completedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY aps.completedAt DESC")
    List<AdvertiserProcessingState> findCompletedJobsBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    /**
     * 특정 광고주의 완료되지 않은 월들 조회 (백필용)
     * Java 서비스 레이어에서 누락된 월을 계산하는 것으로 변경
     */
    @Query("SELECT aps.processingMonth FROM AdvertiserProcessingState aps " +
           "WHERE aps.advertiserId = :advertiserId " +
           "AND aps.status = 'COMPLETED' " +
           "ORDER BY aps.processingMonth ASC")
    List<String> findCompletedMonthsForAdvertiser(@Param("advertiserId") String advertiserId);
}