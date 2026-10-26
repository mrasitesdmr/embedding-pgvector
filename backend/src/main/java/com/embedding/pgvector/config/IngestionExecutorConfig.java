package com.embedding.pgvector.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * İçe alma işlerini arka planda çalıştıran sınırlı boyutlu iş parçacığı havuzunu yapılandırır.
 */
@Configuration(proxyBeanMethods = false)
public class IngestionExecutorConfig {

    public static final String INGESTION_EXECUTOR = "ingestionExecutor";

    @Bean(INGESTION_EXECUTOR)
    public ThreadPoolTaskExecutor ingestionExecutor(IngestionProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.workerThreads());
        executor.setMaxPoolSize(properties.workerThreads());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("ingestion-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
