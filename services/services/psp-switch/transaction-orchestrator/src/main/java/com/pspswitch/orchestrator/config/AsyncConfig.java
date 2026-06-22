package com.pspswitch.orchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async and Scheduling configuration for the Transaction Orchestrator.
 * 
 * @EnableAsync: ThreadPoolTaskExecutor with 10 core threads for saga steps
 * @EnableScheduling: ReconciliationService @Scheduled(fixedDelay=60000)
 * 
 *                    The pool is named "orchestrator-" for easy identification
 *                    in logs and thread
 *                    dumps.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean(name = "orchestratorExecutor")
    public Executor orchestratorExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("orchestrator-");
        executor.initialize();
        return executor;
    }
}
