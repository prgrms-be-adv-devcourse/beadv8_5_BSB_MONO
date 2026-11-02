package com.bukang.member.out.mail;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;

/**
 * 발송 전용 스레드 2개로 메일을 보낸다. 다시 보내기 대기도 스레드를 잡지 않고 예약으로 처리한다
 * 예약은 서버 메모리에만 있어 재시작하면 사라진다 (BUK-40에서 받아들임)
 */
public class ExecutorMailTaskScheduler implements MailTaskScheduler {
	private static final int THREADS = 2;

	private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(THREADS, threadFactory());

	@Override
	public void schedule(Runnable task, Duration delay) {
		executor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
	}

	@PreDestroy
	public void shutdown() {
		executor.shutdown();
	}

	private static ThreadFactory threadFactory() {
		AtomicInteger sequence = new AtomicInteger();
		return runnable -> {
			Thread thread = new Thread(runnable, "member-mail-" + sequence.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		};
	}
}
