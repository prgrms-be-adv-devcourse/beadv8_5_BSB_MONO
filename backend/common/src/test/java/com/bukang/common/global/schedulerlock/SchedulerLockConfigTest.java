package com.bukang.common.global.schedulerlock;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 스케줄 작업 잠금 (BUK-206). 스프링 컨텍스트 두 개를 pod 두 개로 보고, 같은 H2 DB의 잠금 표를 함께 쓰게 한다.
 * 작업 내용(Work)만 Mock으로 두고, 잠금 설정·ShedLock·잠금 표는 진짜를 쓴다.
 */
class SchedulerLockConfigTest {
	private static final String LOCK_TABLE = "test_shedlock";
	private static final DataSource DATA_SOURCE = new DriverManagerDataSource(
		"jdbc:h2:mem:scheduler-lock-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");

	@BeforeAll
	static void createLockTable() {
		// 서비스의 Flyway SQL(V..__create_<서비스>_shedlock.sql)과 같은 칸
		new JdbcTemplate(DATA_SOURCE).execute("""
			create table if not exists test_shedlock (
				name varchar(64) not null,
				lock_until datetime(6) not null,
				locked_at datetime(6) not null,
				locked_by varchar(255) not null,
				primary key (name)
			)""");
	}

	@Test
	@DisplayName("한 pod가 작업을 돌리는 동안 다른 pod가 깨어나면, 그 pod에서는 작업이 돌지 않는다")
	void otherPodSkipsWhileLockIsHeld() throws Exception {
		// given: pod A의 작업은 시작했다고 알린 뒤, 끝내도 된다는 신호가 올 때까지 잠금을 쥔 채 기다린다
		CountDownLatch startedOnA = new CountDownLatch(1);
		CountDownLatch finishA = new CountDownLatch(1);
		Work workOnA = mock(Work.class);
		doAnswer(invocation -> {
			startedOnA.countDown();
			finishA.await(5, TimeUnit.SECONDS);
			return null;
		}).when(workOnA).doWork();
		Work workOnB = mock(Work.class);

		try (AnnotationConfigApplicationContext podA = startPod(workOnA);
			AnnotationConfigApplicationContext podB = startPod(workOnB)) {
			// when: A가 작업을 돌리는 도중에 B가 같은 작업을 부른다
			CompletableFuture<Void> runOnA = CompletableFuture.runAsync(() -> podA.getBean(LockedJob.class).run());
			assertThat(startedOnA.await(5, TimeUnit.SECONDS)).isTrue();
			podB.getBean(LockedJob.class).run();
			finishA.countDown();
			runOnA.get(5, TimeUnit.SECONDS);
		}

		// then+
		verify(workOnA, times(1)).doWork();
		verify(workOnB, never()).doWork();
	}

	// 서비스와 같은 설정(scheduler-lock.enabled·table)으로 잠금 설정을 켜고, 잠금을 붙인 작업 하나를 둔 컨텍스트
	private static AnnotationConfigApplicationContext startPod(Work work) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("scheduler-lock",
			Map.of("scheduler-lock.enabled", "true", "scheduler-lock.table", LOCK_TABLE)));
		context.registerBean(DataSource.class, () -> DATA_SOURCE);
		context.registerBean(LockedJob.class, () -> new LockedJob(work));
		context.register(SchedulerLockConfig.class);
		context.refresh();
		return context;
	}

	interface Work {
		void doWork();
	}

	// 서비스의 스케줄러처럼 @SchedulerLock을 붙인 작업. 잠금은 스프링 프록시를 거쳐 부를 때만 걸린다
	static class LockedJob {
		private final Work work;

		LockedJob(Work work) {
			this.work = work;
		}

		@SchedulerLock(name = "test-job", lockAtMostFor = "PT1M")
		public void run() {
			work.doWork();
		}
	}
}
