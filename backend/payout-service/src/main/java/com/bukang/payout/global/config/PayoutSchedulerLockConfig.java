package com.bukang.payout.global.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;

/**
 * 정산 배치 잠금 (BUK-167). 서버가 여러 대여도 생성 · 지급 배치가 한 곳에서만 돌게 DB 행(PAYOUT_SHEDLOCK)으로 잠근다
 * 스케줄과 관리자 실행이 같은 잠금을 써야 하므로 @SchedulerLock 대신 LockingTaskExecutor로 코드에서 잠근다 (PayoutBatchUseCase)
 * 모든 서비스가 같은 DB를 쓰므로 기본 표 이름(shedlock) 대신 정산 접두사를 붙인다
 */
@Configuration
public class PayoutSchedulerLockConfig {
	private static final String LOCK_TABLE = "payout_shedlock";

	@Bean
	public LockProvider payoutLockProvider(DataSource dataSource) {
		// 서버마다 시계가 달라도 같은 기준으로 잠금 시각을 보도록 DB 시각을 쓴다
		return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
			.withJdbcTemplate(new JdbcTemplate(dataSource))
			.withTableName(LOCK_TABLE)
			.usingDbTime()
			.build());
	}

	@Bean
	public LockingTaskExecutor payoutLockingTaskExecutor(LockProvider payoutLockProvider) {
		return new DefaultLockingTaskExecutor(payoutLockProvider);
	}
}
