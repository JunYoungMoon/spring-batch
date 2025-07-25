package com.claude.springbatch.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdvertiserDataRequest {
    private String advertiserId;
    private List<String> segments;
    private List<String> eventTypes;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private List<String> channels;
    private String location;
    private Boolean aiEnhanced;
    private Integer limit;
    private Integer offset;
}