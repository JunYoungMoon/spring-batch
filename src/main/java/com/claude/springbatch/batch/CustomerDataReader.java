package com.claude.springbatch.batch;

import com.claude.springbatch.entity.CustomerData;
import com.claude.springbatch.repository.CustomerDataRepository;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;

@Component
public class CustomerDataReader implements ItemReader<CustomerData> {

    private final CustomerDataRepository customerDataRepository;

    public CustomerDataReader(CustomerDataRepository customerDataRepository) {
        this.customerDataRepository = customerDataRepository;
    }

    private Iterator<CustomerData> dataIterator;
    private boolean initialized = false;

    @Override
    public CustomerData read() throws Exception {
        if (!initialized) {
            initializeData();
            initialized = true;
        }

        if (dataIterator != null && dataIterator.hasNext()) {
            return dataIterator.next();
        }
        return null;
    }

    private void initializeData() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(1);
        List<CustomerData> unprocessedData = customerDataRepository.findUnprocessedDataSince(cutoffTime);
        
        if (!unprocessedData.isEmpty()) {
            dataIterator = unprocessedData.iterator();
        }
    }

    public void reset() {
        initialized = false;
        dataIterator = null;
    }
}