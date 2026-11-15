package com.bukang.cash.dto.charge;

import java.time.LocalDateTime;

// 관리자 「고액 충전 확인」 목록 한 줄. 표시는 회원마다 한 건이라 한 줄이 회원 한 명이다
// chargeAmount·chargedAt은 표시가 붙은 충전(누적이 처음 50만 원을 넘긴 충전)의 금액과 완료 시각이고,
// joinWindowChargeTotal은 가입 후 24시간 안에 완료된 충전 합계다
public record ChargeReviewDto(
	long memberId,
	String nickname,
	LocalDateTime joinedAt,
	long chargeAmount,
	LocalDateTime chargedAt,
	long joinWindowChargeTotal
) {
}
