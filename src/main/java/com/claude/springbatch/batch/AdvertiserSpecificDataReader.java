package com.claude.springbatch.batch;

import com.claude.springbatch.entity.CustomerData;
import com.claude.springbatch.repository.CustomerDataRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Component
@StepScope
@Slf4j
public class AdvertiserSpecificDataReader implements ItemReader<CustomerData> {

    private final CustomerDataRepository customerDataRepository;
    private final String advertiserId;
    
    private Iterator<CustomerData> dataIterator;
    private boolean initialized = false;

    public AdvertiserSpecificDataReader(CustomerDataRepository customerDataRepository,
                                      @Value("#{jobParameters['advertiserId']}") String advertiserId) {
        this.customerDataRepository = customerDataRepository;
        this.advertiserId = advertiserId != null ? advertiserId : "DEFAULT";
    }

    @Override
    public CustomerData read() throws Exception {
        if (!initialized) {
            initializeAdvertiserData();
            initialized = true;
        }

        if (dataIterator != null && dataIterator.hasNext()) {
            CustomerData data = dataIterator.next();
            log.debug("Reading data for advertiser {}: customer {}", advertiserId, 
                     data.getCustomer().getCustomerId());
            return data;
        }
        
        log.info("Finished reading data for advertiser: {}", advertiserId);
        return null;
    }

    private void initializeAdvertiserData() {
        log.info("Initializing data reader for advertiser: {}", advertiserId);
        
        // Get unprocessed data from the last 24 hours for this advertiser's criteria
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(1);
        List<CustomerData> unprocessedData = customerDataRepository.findUnprocessedDataSince(cutoffTime);
        
        // Filter data based on advertiser-specific criteria
        List<CustomerData> advertiserData = filterDataForAdvertiser(unprocessedData);
        
        if (!advertiserData.isEmpty()) {
            dataIterator = advertiserData.iterator();
            log.info("Found {} records to process for advertiser: {}", advertiserData.size(), advertiserId);
        } else {
            log.info("No data found to process for advertiser: {}", advertiserId);
        }
    }
    
    private List<CustomerData> filterDataForAdvertiser(List<CustomerData> allData) {
        // For now, we'll process all unprocessed data for each advertiser
        // In a real implementation, you might filter based on:
        // - Customer segments the advertiser is interested in
        // - Geographic regions
        // - Product categories
        // - Customer consent/privacy settings
        
        return allData.stream()
                .filter(data -> data.getCustomer() != null)
                .filter(data -> !data.getProcessedForAi())
                .limit(1000) // Limit batch size per advertiser
                .toList();
    }

    public void reset() {
        initialized = false;
        dataIterator = null;
        log.info("Reset data reader for advertiser: {}", advertiserId);
    }
}