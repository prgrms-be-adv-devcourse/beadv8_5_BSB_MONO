package com.bukang.cash.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cash.charge.confirm")
public record ChargeConfirmProperties(
	int maxWaitAttempts,
	long waitIntervalMillis,
	List<Long> lookupRetryDelaysMillis
) {
}
