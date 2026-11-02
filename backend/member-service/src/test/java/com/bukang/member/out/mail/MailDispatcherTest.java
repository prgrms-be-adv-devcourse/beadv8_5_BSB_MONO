package com.bukang.member.out.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.member.config.auth.PasswordResetProperties;

class MailDispatcherTest {
	private static final OutgoingMail MAIL = new OutgoingMail("runner1@crewrun.com", "제목", "본문", "<p>본문</p>");
	private static final List<Duration> DELAYS = List.of(
		Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofSeconds(30));

	private final InMemoryMemberMailSender sender = new InMemoryMemberMailSender();
	private final List<Duration> scheduledDelays = new ArrayList<>();
	private MailDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		MailTaskScheduler recordingScheduler = (task, delay) -> {
			scheduledDelays.add(delay);
			task.run();
		};
		PasswordResetProperties properties = new PasswordResetProperties(
			"http://localhost:3000", Duration.ofMinutes(30), Duration.ofMinutes(1), 5, Duration.ofHours(24),
			DELAYS, "크루런");
		dispatcher = new MailDispatcher(sender, recordingScheduler, properties);
	}

	@Test
	@DisplayName("성공하면 한 번만 보낸다")
	void sendsOnce() {
		dispatcher.dispatch(MAIL);

		assertThat(sender.sent()).containsExactly(MAIL);
		assertThat(scheduledDelays).containsExactly(Duration.ZERO);
	}

	@Test
	@DisplayName("두 번 실패하면 2초, 10초 뒤 다시 보내 세 번째에 보낸다")
	void retriesWithDelays() {
		sender.failNext(2);

		dispatcher.dispatch(MAIL);

		assertThat(sender.attempts()).isEqualTo(3);
		assertThat(sender.sent()).containsExactly(MAIL);
		assertThat(scheduledDelays).containsExactly(Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(10));
	}

	@Test
	@DisplayName("처음 보내기와 다시 보내기 3번이 모두 실패하면 멈춘다")
	void givesUpAfterThreeRetries() {
		sender.failNext(10);

		dispatcher.dispatch(MAIL);

		assertThat(sender.attempts()).isEqualTo(4);
		assertThat(sender.sent()).isEmpty();
	}

	@Test
	@DisplayName("다시 보내기 직전에 더 이상 필요 없는 메일이면 멈춘다")
	void stopsWhenNoLongerWanted() {
		sender.failNext(1);
		AtomicBoolean wanted = new AtomicBoolean(false);

		dispatcher.dispatch(MAIL, wanted::get);

		assertThat(sender.attempts()).isEqualTo(1);
		assertThat(sender.sent()).isEmpty();
	}

	@Test
	@DisplayName("다시 보내기 전 확인이 실패하면(Redis 멈춤) 보내지 않는다")
	void stopsWhenCheckFails() {
		sender.failNext(1);

		dispatcher.dispatch(MAIL, () -> {
			throw new IllegalStateException("Redis 멈춤");
		});

		assertThat(sender.attempts()).isEqualTo(1);
	}
}
