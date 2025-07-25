package com.claude.springbatch.repository;

import com.claude.springbatch.entity.AdvertiserRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AdvertiserRequestRepository extends JpaRepository<AdvertiserRequest, Long> {
    
    List<AdvertiserRequest> findByAdvertiserId(String advertiserId);
    
    List<AdvertiserRequest> findByStatus(AdvertiserRequest.RequestStatus status);
    
    List<AdvertiserRequest> findByRequestType(String requestType);
    
    @Query("SELECT ar FROM AdvertiserRequest ar WHERE ar.status = :status " +
           "AND ar.requestTime >= :since ORDER BY ar.requestTime ASC")
    List<AdvertiserRequest> findByStatusAndRequestTimeSince(
            @Param("status") AdvertiserRequest.RequestStatus status,
            @Param("since") LocalDateTime since);
    
    @Query("SELECT ar FROM AdvertiserRequest ar WHERE ar.advertiserId = :advertiserId " +
           "AND ar.requestTime BETWEEN :startDate AND :endDate")
    List<AdvertiserRequest> findByAdvertiserIdAndDateRange(
            @Param("advertiserId") String advertiserId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    List<AdvertiserRequest> findByAiEnhanced(Boolean aiEnhanced);
}