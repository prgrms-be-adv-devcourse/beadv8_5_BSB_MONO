package com.bukang.payout.app;

import java.util.Optional;

import com.bukang.common.global.eventpublisher.EventPublisher;
import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutRepository;

/**
 * 보류 알림을 보지 않는 테스트가 쓰는 PayoutHoldUseCase. 판매자를 찾지 못한 것으로 두어 알림을 보내지 않는다
 */
final class PayoutHoldUseCases {
	private PayoutHoldUseCases() {
	}

	static PayoutHoldUseCase withoutNotification(PayoutRepository payoutRepository,
		PayoutHoldRepository payoutHoldRepository, BusinessDayCalendar calendar) {
		return new PayoutHoldUseCase(payoutRepository, payoutHoldRepository, calendar, raceId -> Optional.empty(),
			new EventPublisher(event -> {
			}));
	}
}
