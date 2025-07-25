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
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public enum AdvertiserStatus {
        ACTIVE, INACTIVE, SUSPENDED
    }
    
    public enum BatchStatus {
        SUCCESS, FAILED, RUNNING, PENDING
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
}