package com.myorderlynk.app.service.notification;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Dedicated thread pool for WhatsApp delivery, kept separate from the email pool so a
 * slow WhatsApp API never starves email (and vice-versa). Async is enabled globally
 * by {@code ResendConfig}'s {@code @EnableAsync}.
 */
@Configuration
public class WhatsAppConfig {

    @Bean("whatsappExecutor")
    public TaskExecutor whatsappExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("whatsapp-");
        executor.initialize();
        return executor;
    }
}