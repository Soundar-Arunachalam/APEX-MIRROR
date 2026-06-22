package com.psp.npci.adapter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async thread-pool configuration for the NPCI Adapter.
 *
 * <p>
 * The {@code @Async} methods in this service (webhook post-processing) must
 * return quickly so that the HTTP Ack is not delayed. This dedicated executor
 * prevents webhook processing from competing with REST/Kafka threads.
 *
 * <h2>Pool tuning rationale</h2>
 * <ul>
 * <li><b>corePoolSize=4</b> — handles normal callback throughput</li>
 * <li><b>maxPoolSize=20</b> — handles spikes without OOM risk</li>
 * <li><b>queueCapacity=100</b> — buffers bursts; alert if utilisation is
 * high</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Named executor used by {@code @Async("npciAsyncExecutor")} in webhook
     * handlers.
     * Using a named executor (rather than the default one) avoids accidentally
     * sharing
     * the thread pool with other Spring @Async callers in the same JVM.
     */
    @Bean(name = "npciAsyncExecutor")
    public Executor npciAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("npci-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
