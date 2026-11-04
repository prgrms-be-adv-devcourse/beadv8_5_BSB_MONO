package com.bukang.member.out.mail;

import java.time.Duration;

/**
 * 메일 발송을 요청 스레드 밖에서 실행한다. 운영은 발송 전용 스레드 풀, 테스트는 바로 실행하는 구현을 쓴다
 */
public interface MailTaskScheduler {
	void schedule(Runnable task, Duration delay);
}
