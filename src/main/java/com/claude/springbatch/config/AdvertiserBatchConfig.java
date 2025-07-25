package com.claude.springbatch.config;

import com.claude.springbatch.entity.CustomerData;
import com.claude.springbatch.batch.AdvertiserSpecificDataReader;
import com.claude.springbatch.batch.AdvertiserSpecificDataProcessor;
import com.claude.springbatch.batch.AdvertiserSpecificDataWriter;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class AdvertiserBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final AdvertiserSpecificDataReader advertiserDataReader;
    private final AdvertiserSpecificDataProcessor advertiserDataProcessor;
    private final AdvertiserSpecificDataWriter advertiserDataWriter;

    public AdvertiserBatchConfig(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager,
                               AdvertiserSpecificDataReader advertiserDataReader,
                               AdvertiserSpecificDataProcessor advertiserDataProcessor,
                               AdvertiserSpecificDataWriter advertiserDataWriter) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.advertiserDataReader = advertiserDataReader;
        this.advertiserDataProcessor = advertiserDataProcessor;
        this.advertiserDataWriter = advertiserDataWriter;
    }

    @Bean
    public Job advertiserSpecificCdpJob() {
        return new JobBuilder("advertiserSpecificCdpJob", jobRepository)
                .start(processAdvertiserDataStep())
                .build();
    }

    @Bean
    public Step processAdvertiserDataStep() {
        return new StepBuilder("processAdvertiserDataStep", jobRepository)
                .<CustomerData, CustomerData>chunk(50, transactionManager)
                .reader(advertiserDataReader)
                .processor(advertiserDataProcessor)
                .writer(advertiserDataWriter)
                .faultTolerant()
                .skipLimit(10)
                .skip(Exception.class)
                .build();
    }
    
    // Remove this bean - we'll inject the parameter directly in the components
}