package com.ecomerceshopeonline.ecomercewepapp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} jobs - currently only the release of unpaid card orders
 * ({@code CardPaymentExpiryJob}).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
