package com.claude.springbatch.batch;

import com.claude.springbatch.entity.CustomerData;
import com.claude.springbatch.repository.CustomerDataRepository;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class CustomerDataWriter implements ItemWriter<CustomerData> {

    private final CustomerDataRepository customerDataRepository;

    public CustomerDataWriter(CustomerDataRepository customerDataRepository) {
        this.customerDataRepository = customerDataRepository;
    }

    @Override
    public void write(Chunk<? extends CustomerData> chunk) throws Exception {
        List<? extends CustomerData> items = chunk.getItems();
        
        for (CustomerData item : items) {
            if (item != null && item.getProcessedForAi()) {
                customerDataRepository.save(item);
            }
        }
    }
}