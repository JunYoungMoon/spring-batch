package com.claude.springbatch.batch;

import com.claude.springbatch.entity.Customer;
import com.claude.springbatch.entity.CustomerData;
import com.claude.springbatch.service.AIFeatureExtractorService;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;

@Component
public class CustomerDataProcessor implements ItemProcessor<CustomerData, CustomerData> {

    private final AIFeatureExtractorService aiFeatureExtractorService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CustomerDataProcessor(AIFeatureExtractorService aiFeatureExtractorService) {
        this.aiFeatureExtractorService = aiFeatureExtractorService;
    }

    @Override
    public CustomerData process(CustomerData item) throws Exception {
        if (item.getProcessedForAi()) {
            return null;
        }

        Map<String, Object> aiFeatures = extractAIFeatures(item);
        
        String aiFeatureJson = objectMapper.writeValueAsString(aiFeatures);
        item.setAiFeatures(aiFeatureJson);
        item.setProcessedForAi(true);

        return item;
    }

    private Map<String, Object> extractAIFeatures(CustomerData data) {
        Map<String, Object> features = new HashMap<>();
        
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

        features.putAll(aiFeatureExtractorService.extractAdvancedFeatures(data));

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
}