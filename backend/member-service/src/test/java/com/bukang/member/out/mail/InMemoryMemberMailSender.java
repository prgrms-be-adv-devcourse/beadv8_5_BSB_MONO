package com.bukang.member.out.mail;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailSendException;

/**
 * 테스트용 메일 발송기. 보낸 메일을 목록에 모은다. failNext로 다음 몇 번을 실패시켜 다시 보내기를 확인한다
 * Config는 발송을 바로 실행하는 스케줄러도 함께 끼워, 테스트가 발송 스레드를 기다리지 않게 한다
 */
public class InMemoryMemberMailSender implements MemberMailSender {
	private final List<OutgoingMail> sent = new CopyOnWriteArrayList<>();
	private final AtomicInteger failures = new AtomicInteger();
	private final AtomicInteger attempts = new AtomicInteger();

	@Override
	public void send(OutgoingMail mail) {
		attempts.incrementAndGet();
		if (failures.getAndUpdate(left -> Math.max(0, left - 1)) > 0) {
			throw new MailSendException("테스트용 발송 실패");
		}
		sent.add(mail);
	}

	public void failNext(int times) {
		failures.set(times);
	}

	public List<OutgoingMail> sent() {
		return new ArrayList<>(sent);
	}

	public int attempts() {
		return attempts.get();
	}

	public void clear() {
		sent.clear();
		failures.set(0);
		attempts.set(0);
	}

	// 기다리는 시간 없이 바로 실행한다
	public static class ImmediateMailTaskScheduler implements MailTaskScheduler {
		@Override
		public void schedule(Runnable task, Duration delay) {
			task.run();
		}
	}

	@TestConfiguration
	public static class Config {
		@Bean
		@Primary
		public InMemoryMemberMailSender inMemoryMemberMailSender() {
			return new InMemoryMemberMailSender();
		}

		@Bean
		@Primary
		public MailTaskScheduler immediateMailTaskScheduler() {
			return new ImmediateMailTaskScheduler();
		}
	}
}
