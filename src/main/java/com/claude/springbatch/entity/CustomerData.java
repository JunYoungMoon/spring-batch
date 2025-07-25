package com.claude.springbatch.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Entity
@Table(name = "customer_data")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerData {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    
    private String eventType;
    private String eventCategory;
    private LocalDateTime eventTimestamp;
    
    @Column(columnDefinition = "TEXT")
    private String eventData;
    
    private String deviceType;
    private String browserType;
    private String location;
    private String channel;
    private String campaignId;
    private BigDecimal revenue;
    private String currency;
    
    @Column(name = "processed_for_ai")
    private Boolean processedForAi = false;
    
    @Column(name = "ai_features", columnDefinition = "TEXT")
    private String aiFeatures;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}