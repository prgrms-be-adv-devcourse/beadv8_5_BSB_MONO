package com.bukang.order.dto;

import java.time.LocalDateTime;

import com.bukang.order.domain.PartyStatus;

/**
 * 판매자 파티별 내역의 한 줄 (BUK-227, 시안 S03-1, 정책 3장 파티 결제의 임시 확보-4)
 * 신청 인원 = 결제 인원 + 결제하지 않은 자리. 결제하지 않은 자리는 아직 결제 중인 자리와 기한이 지나 풀린 자리를 합한다
 * 결제 기한은 결제 진행 중(PAYING)일 때 의미가 있고, 그 밖의 상태는 화면이 「끝남」으로 보여 준다
 */
public record SellerPartyDto(long partyId, long crewId, long raceCourseId, PartyStatus status, long appliedCount,
	long paidCount, long unpaidCount, LocalDateTime paymentDueAt) {
}
