package com.claude.springbatch.batch;

import com.claude.springbatch.entity.CustomerData;
import com.claude.springbatch.repository.CustomerDataRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Component
@StepScope
@Slf4j
public class AdvertiserSpecificDataReader implements ItemReader<CustomerData> {

    private final CustomerDataRepository customerDataRepository;
    private final String advertiserId;
    private final String processingMonth;
    
    private Iterator<CustomerData> dataIterator;
    private boolean initialized = false;
    
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    public AdvertiserSpecificDataReader(CustomerDataRepository customerDataRepository,
                                      @Value("#{jobParameters['advertiserId']}") String advertiserId,
                                      @Value("#{jobParameters['processingMonth']}") String processingMonth) {
        this.customerDataRepository = customerDataRepository;
        this.advertiserId = advertiserId != null ? advertiserId : "DEFAULT";
        this.processingMonth = processingMonth != null ? processingMonth : LocalDate.now().format(MONTH_FORMATTER);
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
        log.info("[{}] 데이터 리더 초기화 시작 - 처리 월: {}", advertiserId, processingMonth);
        
        // 처리할 월의 시작일과 종료일 계산
        LocalDate monthStart = LocalDate.parse(processingMonth + "-01");
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        
        LocalDateTime startDateTime = monthStart.atStartOfDay();
        LocalDateTime endDateTime = monthEnd.atTime(23, 59, 59);
        
        log.info("[{}] 처리 기간: {} ~ {}", advertiserId, startDateTime, endDateTime);
        
        // 해당 월의 미처리 데이터 조회
        List<CustomerData> monthlyData = customerDataRepository.findUnprocessedDataBetween(startDateTime, endDateTime);
        
        // 광고주별 필터링 적용
        List<CustomerData> advertiserData = filterDataForAdvertiser(monthlyData);
        
        if (!advertiserData.isEmpty()) {
            dataIterator = advertiserData.iterator();
            log.info("[{}] 처리할 레코드 {}개 발견 - 월: {}", advertiserId, advertiserData.size(), processingMonth);
        } else {
            log.info("[{}] 처리할 데이터가 없습니다 - 월: {}", advertiserId, processingMonth);
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