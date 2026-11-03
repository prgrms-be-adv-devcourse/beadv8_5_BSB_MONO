package com.bukang.member.out.mail;

import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.springframework.stereotype.Component;

import com.bukang.member.config.auth.PasswordResetProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 메일을 요청 스레드 밖에서 보내고, 실패하면 정해진 간격(기본 2초, 10초, 30초)으로 다시 보낸다 (BUK-40)
 * 응답이 SMTP를 기다리지 않으므로, 가입한 이메일인지가 응답 시간으로 드러나지 않는다
 * 아웃박스는 쓰지 않는다. 재설정 요청은 DB를 바꾸지 않고, 메일을 잃어도 사용자가 다시 요청하면 된다
 */
@Slf4j
@Component
public class MailDispatcher {
	private final MemberMailSender mailSender;
	private final MailTaskScheduler scheduler;
	private final List<Duration> retryDelays;

	public MailDispatcher(MemberMailSender mailSender, MailTaskScheduler scheduler,
		PasswordResetProperties properties) {
		this.mailSender = mailSender;
		this.scheduler = scheduler;
		this.retryDelays = List.copyOf(properties.retryDelays());
	}

	public void dispatch(OutgoingMail mail) {
		dispatch(mail, () -> true);
	}

	/**
	 * @param stillWanted 다시 보내기 직전에 확인한다. 거짓이면 멈춘다 (예: 그사이 새 재설정 링크를 요청해 이 링크가 무효가 됨)
	 */
	public void dispatch(OutgoingMail mail, BooleanSupplier stillWanted) {
		scheduler.schedule(() -> attempt(mail, stillWanted, 0), Duration.ZERO);
	}

	private void attempt(OutgoingMail mail, BooleanSupplier stillWanted, int retry) {
		if (retry > 0 && !isStillWanted(stillWanted)) {
			log.info("더 이상 필요 없는 메일이라 다시 보내지 않습니다: {}", mail);
			return;
		}
		try {
			mailSender.send(mail);
		} catch (RuntimeException e) {
			if (retry >= retryDelays.size()) {
				log.warn("메일을 {}번 다시 보냈지만 실패했습니다: {}", retry, mail, e);
				return;
			}
			Duration delay = retryDelays.get(retry);
			log.warn("메일 발송에 실패해 {} 뒤 다시 보냅니다 ({}번째): {}", delay, retry + 1, mail, e);
			scheduler.schedule(() -> attempt(mail, stillWanted, retry + 1), delay);
		}
	}

	// Redis가 멈춰 확인하지 못하면 보내지 않는다. 무효일 수 있는 링크를 보내는 것보다 다시 요청하게 하는 편이 낫다
	private static boolean isStillWanted(BooleanSupplier stillWanted) {
		try {
			return stillWanted.getAsBoolean();
		} catch (RuntimeException e) {
			log.warn("다시 보내기 전 확인에 실패해 멈춥니다", e);
			return false;
		}
	}
}
