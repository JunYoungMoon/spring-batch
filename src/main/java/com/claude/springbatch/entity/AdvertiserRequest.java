package com.claude.springbatch.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "advertiser_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdvertiserRequest {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String advertiserId;
    
    @Column(nullable = false)
    private String requestType;
    
    @Column(columnDefinition = "TEXT")
    private String criteria;
    
    @Column(columnDefinition = "TEXT")
    private String response;
    
    @Enumerated(EnumType.STRING)
    private RequestStatus status;
    
    private LocalDateTime requestTime;
    private LocalDateTime processedTime;
    private Integer recordCount;
    
    @Column(name = "ai_enhanced")
    private Boolean aiEnhanced = false;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public enum RequestStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (requestTime == null) {
            requestTime = LocalDateTime.now();
        }
        if (status == null) {
            status = RequestStatus.PENDING;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}