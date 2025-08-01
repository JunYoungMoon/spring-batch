package com.claude.springbatch.repository;

import com.claude.springbatch.entity.Advertiser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AdvertiserRepository extends JpaRepository<Advertiser, Long> {
    
    Optional<Advertiser> findByAdvertiserId(String advertiserId);
    
    @Query("SELECT a FROM Advertiser a WHERE a.batchEnabled = true " +
           "AND a.status = 'ACTIVE' " +
           "AND a.failureCount < a.maxFailures " +
           "AND a.lastBatchStatus != 'RUNNING' " +
           "ORDER BY a.rotationPriority ASC, a.lastBatchRun ASC NULLS FIRST")
    List<Advertiser> findEligibleForBatch();
    
    @Query("SELECT a FROM Advertiser a WHERE a.lastBatchStatus = 'RUNNING'")
    List<Advertiser> findRunningBatchJobs();
    
    List<Advertiser> findByStatus(Advertiser.AdvertiserStatus status);
    
    List<Advertiser> findByBatchEnabledTrue();
    
    @Query("SELECT a FROM Advertiser a WHERE a.failureCount >= a.maxFailures")
    List<Advertiser> findFailedAdvertisers();
    
    // 병렬 처리를 위한 새로운 메서드들
    List<Advertiser> findByStatusAndBatchEnabled(Advertiser.AdvertiserStatus status, Boolean batchEnabled);
    
    @Query("SELECT a FROM Advertiser a WHERE a.advertiserType = 'NEW' AND a.backfillCompleted = false")
    List<Advertiser> findNewAdvertisersNeedingBackfill();
    
    @Query("SELECT a FROM Advertiser a WHERE a.advertiserType = 'EXISTING'")
    List<Advertiser> findExistingAdvertisers();
}