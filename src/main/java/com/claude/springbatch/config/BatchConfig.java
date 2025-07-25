package com.claude.springbatch.config;

import com.claude.springbatch.entity.CustomerData;
import com.claude.springbatch.batch.CustomerDataReader;
import com.claude.springbatch.batch.CustomerDataProcessor;
import com.claude.springbatch.batch.CustomerDataWriter;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final CustomerDataReader customerDataReader;
    private final CustomerDataProcessor customerDataProcessor;
    private final CustomerDataWriter customerDataWriter;

    public BatchConfig(JobRepository jobRepository,
                      PlatformTransactionManager transactionManager,
                      CustomerDataReader customerDataReader,
                      CustomerDataProcessor customerDataProcessor,
                      CustomerDataWriter customerDataWriter) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.customerDataReader = customerDataReader;
        this.customerDataProcessor = customerDataProcessor;
        this.customerDataWriter = customerDataWriter;
    }

    @Bean
    public Job cdpDataProcessingJob() {
        return new JobBuilder("cdpDataProcessingJob", jobRepository)
                .start(processCustomerDataStep())
                .build();
    }

    @Bean
    public Step processCustomerDataStep() {
        return new StepBuilder("processCustomerDataStep", jobRepository)
                .<CustomerData, CustomerData>chunk(100, transactionManager)
                .reader(customerDataReader)
                .processor(customerDataProcessor)
                .writer(customerDataWriter)
                .build();
    }
}