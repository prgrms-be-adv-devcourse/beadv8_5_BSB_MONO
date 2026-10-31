package com.bukang.cash.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cash.charge.confirm")
public record ChargeProperties(
	int maxWaitAttempts,
	long waitIntervalMillis
) {
}
