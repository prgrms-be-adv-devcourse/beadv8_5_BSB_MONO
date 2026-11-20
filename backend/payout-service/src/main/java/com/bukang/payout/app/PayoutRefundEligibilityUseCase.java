package com.bukang.payout.app;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Service;

import com.bukang.payout.domain.PayoutCreateDate;
import com.bukang.payout.dto.RefundEligibilityDto;

/**
 * 참가권 환불 가능 여부 판정 (PRO-28). 정산 생성일 규칙(PayoutCreateDate)으로 정하고, 응답 DTO는 결과만 담는다
 * 주문 서비스가 환불 요청을 받을 때 내부 API로 물어본다
 */
@Service
public class PayoutRefundEligibilityUseCase {
	// 서버 시간대와 상관없이 한국 날짜로 판단한다 (정산 생성일 배치와 같은 기준)
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	public RefundEligibilityDto eligibility(LocalDate raceDate) {
		LocalDate today = LocalDate.now(ZONE);
		return new RefundEligibilityDto(raceDate, PayoutCreateDate.of(raceDate),
			PayoutCreateDate.isRefundable(raceDate, today));
	}
}
