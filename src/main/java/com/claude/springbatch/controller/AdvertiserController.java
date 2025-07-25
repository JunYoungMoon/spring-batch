package com.claude.springbatch.controller;

import com.claude.springbatch.dto.AdvertiserDataRequest;
import com.claude.springbatch.dto.CustomerDataResponse;
import com.claude.springbatch.entity.AdvertiserRequest;
import com.claude.springbatch.service.AdvertiserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/advertiser")
public class AdvertiserController {

    private final AdvertiserService advertiserService;

    public AdvertiserController(AdvertiserService advertiserService) {
        this.advertiserService = advertiserService;
    }

    @PostMapping("/data")
    public ResponseEntity<List<CustomerDataResponse>> getCustomerData(
            @RequestBody AdvertiserDataRequest request) {
        
        List<CustomerDataResponse> customerData = advertiserService.getCustomerDataForAdvertiser(request);
        return ResponseEntity.ok(customerData);
    }

    @PostMapping("/request")
    public ResponseEntity<AdvertiserRequest> createDataRequest(
            @RequestBody AdvertiserDataRequest request) {
        
        AdvertiserRequest advertiserRequest = advertiserService.createDataRequest(request);
        return ResponseEntity.ok(advertiserRequest);
    }

    @GetMapping("/request/{requestId}")
    public ResponseEntity<AdvertiserRequest> getRequestStatus(@PathVariable Long requestId) {
        AdvertiserRequest request = advertiserService.getRequestById(requestId);
        if (request != null) {
            return ResponseEntity.ok(request);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/requests/{advertiserId}")
    public ResponseEntity<List<AdvertiserRequest>> getAdvertiserRequests(
            @PathVariable String advertiserId) {
        
        List<AdvertiserRequest> requests = advertiserService.getRequestsByAdvertiserId(advertiserId);
        return ResponseEntity.ok(requests);
    }

    @GetMapping("/segments")
    public ResponseEntity<List<String>> getAvailableSegments() {
        List<String> segments = advertiserService.getAvailableSegments();
        return ResponseEntity.ok(segments);
    }

    @GetMapping("/event-types")
    public ResponseEntity<List<String>> getAvailableEventTypes() {
        List<String> eventTypes = advertiserService.getAvailableEventTypes();
        return ResponseEntity.ok(eventTypes);
    }
}