package com.medical.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig implements WebMvcConfigurer {

    /**
     * SSE / async MVC 专用线程池，替代默认的 SimpleAsyncTaskExecutor
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mvc-async-");
        executor.initialize();
        configurer.setTaskExecutor(executor);
        configurer.setDefaultTimeout(120_000); // 120s for SSE long-polling
    }

    /**
     * AI Function Calling tool 专用线程池。
     * Spring AI M5 在 reactor-http-nio 线程上同步执行 tool callback，
     * 而 OpenFeign BlockingLoadBalancerClient 内部调用 Mono.block()，
     * Reactor 禁止在 nio 线程上 blocking。
     * Tool callback 内使用 CompletableFuture.supplyAsync(feignCall, toolCallExecutor).join()
     * 将 Feign 调用卸载到此线程池，join() 不触发 Reactor 的 blocking 检测。
     */
    @Bean("toolCallExecutor")
    public Executor toolCallExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("tool-call-");
        executor.initialize();
        return executor;
    }

    @Bean("summaryExecutor")
    public Executor summaryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("summary-");
        executor.initialize();
        return executor;
    }
}
