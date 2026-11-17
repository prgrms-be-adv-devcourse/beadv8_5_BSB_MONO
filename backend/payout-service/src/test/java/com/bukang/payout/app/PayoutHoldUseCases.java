package com.bukang.payout.app;

import com.bukang.common.global.eventpublisher.EventPublisher;
import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutRepository;

/**
 * 보류 알림을 보지 않는 테스트가 쓰는 PayoutHoldUseCase. 보류 이벤트를 버린다
 */
final class PayoutHoldUseCases {
	private PayoutHoldUseCases() {
	}

	static PayoutHoldUseCase withoutNotification(PayoutRepository payoutRepository,
		PayoutHoldRepository payoutHoldRepository, BusinessDayCalendar calendar) {
		return new PayoutHoldUseCase(payoutRepository, payoutHoldRepository, calendar, new EventPublisher(event -> {
		}));
	}
}
