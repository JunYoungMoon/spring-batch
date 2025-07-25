package com.claude.springbatch.controller;

import com.claude.springbatch.entity.Advertiser;
import com.claude.springbatch.service.AdvertiserRotationService;
import com.claude.springbatch.service.BatchJobOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/batch")
public class BatchAdminController {

    private final AdvertiserRotationService rotationService;
    private final BatchJobOrchestrator orchestrator;

    public BatchAdminController(AdvertiserRotationService rotationService,
                               BatchJobOrchestrator orchestrator) {
        this.rotationService = rotationService;
        this.orchestrator = orchestrator;
    }

    @GetMapping("/status")
    public ResponseEntity<BatchJobOrchestrator.BatchSystemStatus> getSystemStatus() {
        return ResponseEntity.ok(orchestrator.getSystemStatus());
    }

    @GetMapping("/advertisers/eligible")
    public ResponseEntity<List<Advertiser>> getEligibleAdvertisers() {
        List<Advertiser> eligible = rotationService.getEligibleAdvertisers();
        return ResponseEntity.ok(eligible);
    }

    @GetMapping("/advertisers/running")
    public ResponseEntity<List<Advertiser>> getRunningJobs() {
        List<Advertiser> running = rotationService.getRunningBatchJobs();
        return ResponseEntity.ok(running);
    }

    @PostMapping("/trigger/{advertiserId}")
    public ResponseEntity<String> triggerBatchJob(@PathVariable String advertiserId) {
        orchestrator.triggerBatchForAdvertiser(advertiserId);
        return ResponseEntity.ok("Batch job triggered for advertiser: " + advertiserId);
    }

    @PostMapping("/reset/{advertiserId}")
    public ResponseEntity<String> resetAdvertiserFailures(@PathVariable String advertiserId) {
        rotationService.resetFailedAdvertiser(advertiserId);
        return ResponseEntity.ok("Reset failure count for advertiser: " + advertiserId);
    }

    @GetMapping("/advertiser/{advertiserId}")
    public ResponseEntity<Advertiser> getAdvertiserStatus(@PathVariable String advertiserId) {
        return rotationService.getAdvertiserById(advertiserId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}