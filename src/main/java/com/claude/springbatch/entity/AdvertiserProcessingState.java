package com.claude.springbatch.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

/**
 * 광고주별 배치 처리 상태 추적 엔티티
 * 
 * 각 광고주의 월별 처리 상태를 상세하게 추적하여
 * 병렬 처리 환경에서 중복 처리 방지와 진행 상황 모니터링을 지원
 */
@Entity
@Table(name = "advertiser_processing_states", 
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"advertiser_id", "processing_month"})
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdvertiserProcessingState {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "advertiser_id", nullable = false)
    private String advertiserId;
    
    // 처리 중인 월 (YYYY-MM 형식)
    @Column(name = "processing_month", nullable = false)
    private String processingMonth;
    
    // 처리 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ProcessingStatus status;
    
    // 처리 시작 시간
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    // 처리 완료 시간
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    // 처리된 레코드 수
    @Column(name = "processed_records")
    private Long processedRecords = 0L;
    
    // 실패한 레코드 수
    @Column(name = "failed_records")
    private Long failedRecords = 0L;
    
    // 오류 메시지
    @Column(name = "error_message", length = 1000)
    private String errorMessage;
    
    // 재시도 횟수
    @Column(name = "retry_count")
    private Integer retryCount = 0;
    
    // 최대 재시도 횟수
    @Column(name = "max_retries")
    private Integer maxRetries = 3;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public enum ProcessingStatus {
        PENDING,    // 대기 중
        RUNNING,    // 실행 중
        COMPLETED,  // 완료
        FAILED,     // 실패
        RETRYING    // 재시도 중
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = ProcessingStatus.PENDING;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    
    // 처리 시작
    public void startProcessing() {
        this.status = ProcessingStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
        this.processedRecords = 0L;
        this.failedRecords = 0L;
        this.errorMessage = null;
    }
    
    // 처리 완료
    public void completeProcessing(long processedCount, long failedCount) {
        this.status = ProcessingStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.processedRecords = processedCount;
        this.failedRecords = failedCount;
    }
    
    // 처리 실패
    public void failProcessing(String error, long processedCount, long failedCount) {
        this.status = ProcessingStatus.FAILED;
        this.completedAt = LocalDateTime.now();
        this.errorMessage = error;
        this.processedRecords = processedCount;
        this.failedRecords = failedCount;
    }
    
    // 재시도 가능 여부 확인
    public boolean canRetry() {
        return this.retryCount < this.maxRetries && this.status == ProcessingStatus.FAILED;
    }
    
    // 재시도 준비
    public void prepareRetry() {
        if (canRetry()) {
            this.retryCount++;
            this.status = ProcessingStatus.RETRYING;
            this.errorMessage = null;
        }
    }
    
    // 처리 진행률 계산
    public double getProgressPercentage() {
        if (processedRecords == null || (processedRecords + failedRecords) == 0) {
            return 0.0;
        }
        long totalRecords = processedRecords + failedRecords;
        return (double) processedRecords / totalRecords * 100.0;
    }
    
    // 처리 중 상태인지 확인
    public boolean isProcessing() {
        return status == ProcessingStatus.RUNNING || status == ProcessingStatus.RETRYING;
    }
    
    // 처리 완료 상태인지 확인
    public boolean isCompleted() {
        return status == ProcessingStatus.COMPLETED;
    }
    
    // 처리 실패 상태인지 확인
    public boolean isFailed() {
        return status == ProcessingStatus.FAILED;
    }
}