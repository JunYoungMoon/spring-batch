package com.claude.springbatch.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "advertisers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Advertiser {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String advertiserId;
    
    @Column(nullable = false)
    private String name;
    
    private String email;
    private String contactPerson;
    
    @Enumerated(EnumType.STRING)
    private AdvertiserStatus status;
    
    @Column(name = "batch_enabled")
    private Boolean batchEnabled = true;
    
    @Column(name = "last_batch_run")
    private LocalDateTime lastBatchRun;
    
    @Column(name = "last_batch_status")
    @Enumerated(EnumType.STRING)
    private BatchStatus lastBatchStatus;
    
    @Column(name = "failure_count")
    private Integer failureCount = 0;
    
    @Column(name = "max_failures")
    private Integer maxFailures = 3;
    
    @Column(name = "rotation_priority")
    private Integer rotationPriority = 0;
    
    // 광고주 유형 및 상태 추적을 위한 새로운 필드들
    @Column(name = "advertiser_type")
    @Enumerated(EnumType.STRING)
    private AdvertiserType advertiserType = AdvertiserType.EXISTING;
    
    // 현재 처리 중인 월 (YYYY-MM 형식)
    @Column(name = "current_processing_month")
    private String currentProcessingMonth;
    
    // 백필 처리 시작 월 (신규 광고주용)
    @Column(name = "backfill_start_month")
    private String backfillStartMonth;
    
    // 백필 처리 완료 여부
    @Column(name = "backfill_completed")
    private Boolean backfillCompleted = false;
    
    // 마지막으로 처리 완료된 월
    @Column(name = "last_completed_month")
    private String lastCompletedMonth;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public enum AdvertiserStatus {
        ACTIVE, INACTIVE, SUSPENDED
    }
    
    public enum BatchStatus {
        SUCCESS, FAILED, RUNNING, PENDING
    }
    
    public enum AdvertiserType {
        EXISTING,  // 기존 광고주 - 현재 월만 처리
        NEW        // 신규 광고주 - 2년 백필 데이터 처리 필요
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = AdvertiserStatus.ACTIVE;
        }
        if (lastBatchStatus == null) {
            lastBatchStatus = BatchStatus.PENDING;
        }
        if (advertiserType == null) {
            advertiserType = AdvertiserType.EXISTING;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public boolean isEligibleForBatch() {
        return batchEnabled && 
               status == AdvertiserStatus.ACTIVE && 
               failureCount < maxFailures &&
               lastBatchStatus != BatchStatus.RUNNING;
    }
    
    public void recordSuccess() {
        this.lastBatchRun = LocalDateTime.now();
        this.lastBatchStatus = BatchStatus.SUCCESS;
        this.failureCount = 0;
    }
    
    public void recordFailure() {
        this.lastBatchRun = LocalDateTime.now();
        this.lastBatchStatus = BatchStatus.FAILED;
        this.failureCount++;
        
        if (this.failureCount >= this.maxFailures) {
            this.batchEnabled = false;
        }
    }
    
    public void recordRunning() {
        this.lastBatchStatus = BatchStatus.RUNNING;
    }
    
    // 신규 광고주인지 확인
    public boolean isNewAdvertiser() {
        return this.advertiserType == AdvertiserType.NEW;
    }
    
    // 백필 처리가 필요한지 확인
    public boolean needsBackfillProcessing() {
        return isNewAdvertiser() && !backfillCompleted;
    }
    
    // 다음 처리할 월을 계산 (신규 광고주용)
    public String getNextProcessingMonth() {
        if (currentProcessingMonth == null) {
            return backfillStartMonth;
        }
        return currentProcessingMonth;
    }
    
    // 월 처리 완료 기록
    public void recordMonthCompleted(String month) {
        this.lastCompletedMonth = month;
        this.currentProcessingMonth = getNextMonth(month);
        
        // 현재 월까지 처리 완료되면 백필 완료 처리
        String currentMonth = java.time.LocalDate.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        if (month.equals(currentMonth)) {
            this.backfillCompleted = true;
            this.advertiserType = AdvertiserType.EXISTING;
        }
    }
    
    // 다음 달 계산 유틸리티 메서드
    private String getNextMonth(String currentMonth) {
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(currentMonth + "-01");
            java.time.LocalDate nextMonth = date.plusMonths(1);
            return nextMonth.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        } catch (Exception e) {
            return currentMonth;
        }
    }
    
    // 신규 광고주 초기화 (2년 전부터 백필 설정)
    public void initializeAsNewAdvertiser() {
        this.advertiserType = AdvertiserType.NEW;
        this.backfillCompleted = false;
        
        // 2년 전 1월부터 시작
        java.time.LocalDate twoYearsAgo = java.time.LocalDate.now().minusYears(2).withDayOfMonth(1);
        this.backfillStartMonth = twoYearsAgo.format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        this.currentProcessingMonth = this.backfillStartMonth;
    }
}