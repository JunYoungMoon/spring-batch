package com.claude.springbatch.batch;

import com.claude.springbatch.entity.Customer;
import com.claude.springbatch.entity.CustomerData;
import com.claude.springbatch.service.AIFeatureExtractorService;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Component
@StepScope
@Slf4j
public class AdvertiserSpecificDataProcessor implements ItemProcessor<CustomerData, CustomerData> {

    private final AIFeatureExtractorService aiFeatureExtractorService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String advertiserId;

    public AdvertiserSpecificDataProcessor(AIFeatureExtractorService aiFeatureExtractorService,
                                         @Value("#{jobParameters['advertiserId']}") String advertiserId) {
        this.aiFeatureExtractorService = aiFeatureExtractorService;
        this.advertiserId = advertiserId != null ? advertiserId : "DEFAULT";
    }

    @Override
    public CustomerData process(CustomerData item) throws Exception {
        if (item.getProcessedForAi()) {
            log.debug("Skipping already processed item for advertiser {}: {}", 
                     advertiserId, item.getId());
            return null;
        }

        log.debug("Processing customer data for advertiser {}: customer {}", 
                 advertiserId, item.getCustomer().getCustomerId());

        try {
            // Extract AI features with advertiser-specific context
            Map<String, Object> aiFeatures = extractAdvertiserSpecificFeatures(item);
            
            String aiFeatureJson = objectMapper.writeValueAsString(aiFeatures);
            item.setAiFeatures(aiFeatureJson);
            item.setProcessedForAi(true);

            log.debug("Successfully processed data for advertiser {}: customer {}", 
                     advertiserId, item.getCustomer().getCustomerId());
            
            return item;
            
        } catch (Exception e) {
            log.error("Error processing data for advertiser {}: customer {}, error: {}", 
                     advertiserId, item.getCustomer().getCustomerId(), e.getMessage());
            throw e;
        }
    }

    private Map<String, Object> extractAdvertiserSpecificFeatures(CustomerData data) {
        Map<String, Object> features = new HashMap<>();
        
        // Add advertiser context
        features.put("processedForAdvertiser", advertiserId);
        features.put("processingTimestamp", java.time.LocalDateTime.now().toString());
        
        // Extract standard features
        Customer customer = data.getCustomer();
        if (customer != null) {
            features.put("customerSegment", customer.getSegment());
            features.put("customerStatus", customer.getStatus());
            features.put("daysSinceRegistration", 
                calculateDaysSinceRegistration(customer.getRegistrationDate()));
        }

        features.put("eventType", data.getEventType());
        features.put("eventCategory", data.getEventCategory());
        features.put("deviceType", data.getDeviceType());
        features.put("browserType", data.getBrowserType());
        features.put("channel", data.getChannel());
        features.put("location", data.getLocation());
        
        if (data.getRevenue() != null) {
            features.put("revenue", data.getRevenue().doubleValue());
            features.put("revenueCategory", categorizeRevenue(data.getRevenue().doubleValue()));
        }

        features.put("hourOfDay", data.getEventTimestamp().getHour());
        features.put("dayOfWeek", data.getEventTimestamp().getDayOfWeek().getValue());

        try {
            JsonNode eventDataJson = objectMapper.readTree(data.getEventData());
            features.put("eventComplexity", calculateEventComplexity(eventDataJson));
        } catch (Exception e) {
            features.put("eventComplexity", 0);
        }

        // Add advanced features from AI service
        features.putAll(aiFeatureExtractorService.extractAdvancedFeatures(data));
        
        // Add advertiser-specific scoring
        features.put("advertiserRelevanceScore", calculateAdvertiserRelevance(data));

        return features;
    }

    private long calculateDaysSinceRegistration(java.time.LocalDateTime registrationDate) {
        if (registrationDate == null) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(registrationDate, java.time.LocalDateTime.now());
    }

    private String categorizeRevenue(double revenue) {
        if (revenue == 0) return "NO_REVENUE";
        if (revenue <= 10) return "LOW";
        if (revenue <= 100) return "MEDIUM";
        if (revenue <= 1000) return "HIGH";
        return "PREMIUM";
    }

    private int calculateEventComplexity(JsonNode eventData) {
        if (eventData == null) return 0;
        return countJsonFields(eventData);
    }

    private int countJsonFields(JsonNode node) {
        if (node.isObject()) {
            return node.size();
        } else if (node.isArray()) {
            return node.size();
        }
        return 1;
    }
    
    private double calculateAdvertiserRelevance(CustomerData data) {
        // Calculate relevance score based on advertiser-specific criteria
        double score = 0.5; // Base score
        
        // Boost score based on event type
        if ("purchase".equals(data.getEventType())) {
            score += 0.3;
        } else if ("view".equals(data.getEventType())) {
            score += 0.1;
        }
        
        // Boost score based on revenue
        if (data.getRevenue() != null && data.getRevenue().doubleValue() > 0) {
            score += 0.2;
        }
        
        return Math.min(1.0, score);
    }
}