package com.claude.springbatch.batch;

import com.claude.springbatch.entity.CustomerData;
import com.claude.springbatch.repository.CustomerDataRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Component
@StepScope
@Slf4j
public class AdvertiserSpecificDataWriter implements ItemWriter<CustomerData> {

    private final CustomerDataRepository customerDataRepository;
    private final String advertiserId;

    public AdvertiserSpecificDataWriter(CustomerDataRepository customerDataRepository,
                                      @Value("#{jobParameters['advertiserId']}") String advertiserId) {
        this.customerDataRepository = customerDataRepository;
        this.advertiserId = advertiserId != null ? advertiserId : "DEFAULT";
    }

    @Override
    public void write(Chunk<? extends CustomerData> chunk) throws Exception {
        List<? extends CustomerData> items = chunk.getItems();
        
        if (items.isEmpty()) {
            return;
        }
        
        log.info("Writing {} processed items for advertiser: {}", items.size(), advertiserId);
        
        int savedCount = 0;
        for (CustomerData item : items) {
            if (item != null && item.getProcessedForAi()) {
                try {
                    customerDataRepository.save(item);
                    savedCount++;
                    log.debug("Saved processed data for advertiser {}: customer {}", 
                             advertiserId, item.getCustomer().getCustomerId());
                } catch (Exception e) {
                    log.error("Failed to save data for advertiser {}: customer {}, error: {}", 
                             advertiserId, item.getCustomer().getCustomerId(), e.getMessage());
                    throw e;
                }
            }
        }
        
        log.info("Successfully saved {} items for advertiser: {}", savedCount, advertiserId);
    }
}