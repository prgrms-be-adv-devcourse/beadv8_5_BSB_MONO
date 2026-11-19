package com.bukang.member.app;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.member.domain.TermsAgreement;
import com.bukang.member.out.TermsAgreementRepository;

import lombok.RequiredArgsConstructor;

/**
 * 마케팅 동의 사실을 2년마다(2·4·6년…) 알린다 (CRU-22, 정보통신망법)
 * 동의 기록은 같은 값이면 새로 쌓이지 않아 처음 동의 시각이 기준이다. 그래서 햇수를 직접 세어 짝수 해 기념일만 고른다
 * 기념일이 3일 안에 지난 기록을 대상으로 해, 하루 배치가 실패해도 다음 날 다시 잡는다.
 * 알림 ID를 동의 기록·햇수마다 고정해(알림 서비스가 같은 ID는 하나만 저장) 며칠 겹쳐 보내도 알림은 한 번만 생긴다.
 * 알림은 잃어도 되는 메시지라 아웃박스를 쓰지 않고, 커밋된 뒤에 common이 Kafka로 보낸다 (11/04 결정)
 */
@Service
@RequiredArgsConstructor
public class MarketingReconsentUseCase {
	private static final int CATCH_UP_DAYS = 3;
	private static final int INTERVAL_YEARS = 2;

	private final TermsAgreementRepository termsAgreementRepository;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public int notifyDue(LocalDateTime now) {
		int notified = 0;
		for (TermsAgreement agreement : termsAgreementRepository.findMarketingAgreedUntil(
			now.minusYears(INTERVAL_YEARS))) {
			long years = ChronoUnit.YEARS.between(agreement.getDecidedAt(), now);
			long due = years - years % INTERVAL_YEARS;
			if (agreement.getDecidedAt().plusYears(due).isBefore(now.minusDays(CATCH_UP_DAYS))) {
				continue;
			}
			eventPublisher.publishEvent(new NotificationRequestedEvent(
				"marketing-reconsent-" + agreement.getId() + "-" + due,
				agreement.getMember().getId(), NotificationKind.MARKETING_RECONSENT,
				"마케팅 정보 수신, 계속 받으실래요?", "동의하신 지 " + due + "년이 됐어요. 계속 받으실지 확인해 주세요.",
				"/me/settings/terms", LocalDateTime.now(ZoneId.of("Asia/Seoul"))));
			notified++;
		}
		return notified;
	}
}
