package com.claude.springbatch.service;

import com.claude.springbatch.entity.CustomerData;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class AIFeatureExtractorService {

    public Map<String, Object> extractAdvancedFeatures(CustomerData data) {
        Map<String, Object> features = new HashMap<>();
        
        features.put("recency", calculateRecency(data.getEventTimestamp()));
        features.put("frequency", 1.0);
        features.put("sessionIndicator", generateSessionIndicator(data));
        features.put("crossChannelActivity", analyzeCrossChannelActivity(data));
        features.put("behaviorPattern", analyzeBehaviorPattern(data));
        
        if (data.getCampaignId() != null) {
            features.put("campaignEngagement", 1);
            features.put("campaignId", data.getCampaignId());
        } else {
            features.put("campaignEngagement", 0);
        }
        
        features.put("locationScore", calculateLocationScore(data.getLocation()));
        features.put("deviceFingerprint", generateDeviceFingerprint(data));
        
        return features;
    }

    private double calculateRecency(LocalDateTime eventTime) {
        if (eventTime == null) return 0.0;
        long hoursAgo = ChronoUnit.HOURS.between(eventTime, LocalDateTime.now());
        return Math.max(0, 100 - hoursAgo);
    }

    private String generateSessionIndicator(CustomerData data) {
        int hour = data.getEventTimestamp().getHour();
        if (hour >= 6 && hour < 12) return "MORNING";
        if (hour >= 12 && hour < 18) return "AFTERNOON";
        if (hour >= 18 && hour < 22) return "EVENING";
        return "NIGHT";
    }

    private double analyzeCrossChannelActivity(CustomerData data) {
        String channel = data.getChannel();
        if (channel == null) return 0.0;
        
        switch (channel.toLowerCase()) {
            case "email": return 0.8;
            case "social": return 0.9;
            case "paid_search": return 0.7;
            case "organic": return 0.6;
            case "direct": return 1.0;
            default: return 0.5;
        }
    }

    private String analyzeBehaviorPattern(CustomerData data) {
        String eventType = data.getEventType();
        if (eventType == null) return "UNKNOWN";
        
        switch (eventType.toLowerCase()) {
            case "purchase": return "TRANSACTIONAL";
            case "view": return "BROWSING";
            case "click": return "ENGAGING";
            case "search": return "RESEARCH";
            case "cart": return "CONSIDERATION";
            default: return "GENERAL";
        }
    }

    private double calculateLocationScore(String location) {
        if (location == null) return 0.0;
        
        String[] tierOneCities = {"New York", "Los Angeles", "Chicago", "San Francisco"};
        for (String city : tierOneCities) {
            if (location.contains(city)) return 1.0;
        }
        return 0.5;
    }

    private String generateDeviceFingerprint(CustomerData data) {
        StringBuilder fingerprint = new StringBuilder();
        if (data.getDeviceType() != null) {
            fingerprint.append(data.getDeviceType().substring(0, Math.min(3, data.getDeviceType().length())));
        }
        if (data.getBrowserType() != null) {
            fingerprint.append("_").append(data.getBrowserType().substring(0, Math.min(3, data.getBrowserType().length())));
        }
        return fingerprint.toString().toUpperCase();
    }
}