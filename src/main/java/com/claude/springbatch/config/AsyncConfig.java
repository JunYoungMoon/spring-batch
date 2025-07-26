package com.claude.springbatch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 처리 설정
 * 
 * 병렬 광고주 배치 처리를 위한 스레드 풀 설정
 * 각 광고주별로 독립적인 스레드에서 배치 작업이 실행됨
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    
    /**
     * 배치 작업용 스레드 풀 설정
     * 
     * 설정값:
     * - corePoolSize: 5 (기본 유지 스레드 수)
     * - maxPoolSize: 20 (최대 스레드 수)
     * - queueCapacity: 100 (대기 큐 크기)
     * - keepAliveSeconds: 60 (유휴 스레드 유지 시간)
     * 
     * 이 설정으로 최대 20개의 광고주 배치 작업을 동시에 실행 가능
     */
    @Bean(name = "batchTaskExecutor")
    public Executor batchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 기본 스레드 풀 크기 (항상 유지되는 스레드 수)
        executor.setCorePoolSize(5);
        
        // 최대 스레드 풀 크기 (피크 시간대 처리 용량)
        executor.setMaxPoolSize(20);
        
        // 큐 용량 (대기 중인 작업 수)
        executor.setQueueCapacity(100);
        
        // 유휴 스레드 유지 시간 (초)
        executor.setKeepAliveSeconds(60);
        
        // 스레드 이름 접두사 (로깅 및 디버깅용)
        executor.setThreadNamePrefix("BatchTask-");
        
        // 애플리케이션 종료 시 진행 중인 작업 완료 대기
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 종료 대기 시간 (30초)
        executor.setAwaitTerminationSeconds(30);
        
        // 스레드 풀 초기화
        executor.initialize();
        
        return executor;
    }
    
    /**
     * 모니터링용 스레드 풀 설정
     * 
     * 시스템 헬스 체크, 스테일 작업 정리 등 관리 작업용
     */
    @Bean(name = "managementTaskExecutor")
    public Executor managementTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(20);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("Management-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        
        executor.initialize();
        
        return executor;
    }
}