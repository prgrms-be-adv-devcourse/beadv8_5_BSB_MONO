package com.bukang.common.global.schedulerlock;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

/**
 * 스케줄 작업 잠금 (ShedLock, BUK-206). pod가 여러 개여도 같은 작업은 잠금 표에 먼저 이름을 적은 한 곳에서만 돈다.
 * 모든 서비스가 common을 스캔하므로, scheduler-lock.enabled: true로 켠 서비스에서만 만든다.
 * - 작업에는 @SchedulerLock(name = "<서비스>-<작업>", lockAtMostFor = ...)을 붙인다. 이름이 같으면 같은 잠금이다.
 *   lockAtMostFor: 잡은 pod가 죽어도 이 시간이 지나면 잠금이 풀려 다른 pod가 이어 받는다. 작업이 이보다 오래 걸리면 두 곳에서 돌 수 있으니
 *   한 번 실행에 걸리는 가장 긴 시간보다 길게 둔다.
 *   lockAtLeastFor: 정각에 도는 작업(cron)에 둔다. 작업이 금방 끝나도 이만큼 잠금을 쥐어, pod마다 시계가 조금 달라 늦게 깬 pod가 다시 돌지 않게 한다.
 * - 코드에서 잠가야 하면(관리자 실행과 스케줄이 같은 잠금을 쓸 때) LockingTaskExecutor를 받아 쓴다 (정산 PayoutBatchUseCase).
 * - 잠금 표는 서비스마다 Flyway SQL로 만든다. 모든 서비스가 같은 DB를 쓰므로 표 이름에 서비스 접두사를 붙인다(scheduler-lock.table).
 */
@Configuration
@ConditionalOnProperty(prefix = "scheduler-lock", name = "enabled", havingValue = "true")
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulerLockConfig {
	@Bean
	public LockProvider lockProvider(DataSource dataSource, @Value("${scheduler-lock.table}") String table) {
		// 서버마다 시계가 달라도 같은 기준으로 잠금 시각을 보도록 DB 시각을 쓴다
		return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
			.withJdbcTemplate(new JdbcTemplate(dataSource))
			.withTableName(table)
			.usingDbTime()
			.build());
	}

	@Bean
	public LockingTaskExecutor lockingTaskExecutor(LockProvider lockProvider) {
		return new DefaultLockingTaskExecutor(lockProvider);
	}
}
