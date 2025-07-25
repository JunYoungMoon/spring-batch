package com.claude.springbatch.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDataResponse {
    private Long id;
    private String customerId;
    private String customerEmail;
    private String customerSegment;
    private String eventType;
    private String eventCategory;
    private LocalDateTime eventTimestamp;
    private String deviceType;
    private String browserType;
    private String location;
    private String channel;
    private String campaignId;
    private BigDecimal revenue;
    private String currency;
    private String aiFeatures;
    private LocalDateTime createdAt;
}