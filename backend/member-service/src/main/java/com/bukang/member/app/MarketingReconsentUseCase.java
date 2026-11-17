package com.bukang.member.app;

import java.time.LocalDateTime;
import java.time.ZoneId;
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
 * 마케팅에 동의한 지 2년이 된 회원에게 재동의 알림을 요청한다 (CRU-22)
 * 동의 시각이 정확히 2년 전인 기록부터 3일 전까지를 대상으로 해, 하루 배치가 실패해도 다음 날 다시 잡는다.
 * 알림 ID를 동의 기록마다 고정해(알림 서비스가 같은 ID는 하나만 저장) 며칠 겹쳐 보내도 알림은 한 번만 생긴다.
 * 알림은 잃어도 되는 메시지라 아웃박스를 쓰지 않고, 커밋된 뒤에 common이 Kafka로 보낸다 (11/04 결정)
 */
@Service
@RequiredArgsConstructor
public class MarketingReconsentUseCase {
	private static final int CATCH_UP_DAYS = 3;

	private final TermsAgreementRepository termsAgreementRepository;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public int notifyDue(LocalDateTime now) {
		LocalDateTime to = now.minusYears(2);
		List<TermsAgreement> due = termsAgreementRepository.findMarketingAgreedBetween(
			to.minusDays(CATCH_UP_DAYS), to);
		for (TermsAgreement agreement : due) {
			eventPublisher.publishEvent(new NotificationRequestedEvent("marketing-reconsent-" + agreement.getId(),
				agreement.getMember().getId(), NotificationKind.MARKETING_RECONSENT,
				"마케팅 정보 수신, 계속 받으실래요?", "동의하신 지 2년이 됐어요. 계속 받으실지 확인해 주세요.",
				"/me/settings/terms", LocalDateTime.now(ZoneId.of("Asia/Seoul"))));
		}
		return due.size();
	}
}
