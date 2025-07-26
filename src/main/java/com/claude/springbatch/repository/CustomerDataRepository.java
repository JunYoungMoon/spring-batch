package com.claude.springbatch.repository;

import com.claude.springbatch.entity.CustomerData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CustomerDataRepository extends JpaRepository<CustomerData, Long> {
    
    List<CustomerData> findByCustomerId(Long customerId);
    
    List<CustomerData> findByEventType(String eventType);
    
    List<CustomerData> findByEventTimestampBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    List<CustomerData> findByProcessedForAi(Boolean processedForAi);
    
    @Query("SELECT cd FROM CustomerData cd WHERE cd.processedForAi = false " +
           "AND cd.eventTimestamp >= :since ORDER BY cd.eventTimestamp ASC")
    List<CustomerData> findUnprocessedDataSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT cd FROM CustomerData cd WHERE cd.customer.customerId = :customerId " +
           "AND cd.eventType IN :eventTypes AND cd.eventTimestamp >= :since")
    List<CustomerData> findByCustomerIdAndEventTypesAndTimestamp(
            @Param("customerId") String customerId,
            @Param("eventTypes") List<String> eventTypes,
            @Param("since") LocalDateTime since);
    
    @Query("SELECT cd FROM CustomerData cd WHERE cd.channel = :channel " +
           "AND cd.eventTimestamp BETWEEN :startDate AND :endDate")
    List<CustomerData> findByChannelAndDateRange(@Param("channel") String channel,
                                               @Param("startDate") LocalDateTime startDate,
                                               @Param("endDate") LocalDateTime endDate);
    
    // 병렬 처리를 위한 새로운 메서드들
    @Query("SELECT cd FROM CustomerData cd WHERE cd.processedForAi = false " +
           "AND cd.eventTimestamp BETWEEN :startDate AND :endDate " +
           "ORDER BY cd.eventTimestamp ASC")
    List<CustomerData> findUnprocessedDataBetween(@Param("startDate") LocalDateTime startDate,
                                                  @Param("endDate") LocalDateTime endDate);
}