package com.bukang.product.dto;

import com.bukang.product.domain.RaceCourse;
import com.bukang.product.domain.RecordCertRule;

import lombok.AllArgsConstructor;
import lombok.Getter;

// member가 기록증을 판정 · 심사할 때 받는 종목 규칙과 판매자 회원 ID (BUK-270)
// sellerMemberId는 판매자 ID(product_seller.id)가 아니라 그 판매자를 신청한 회원의 ID다 (판매자 화면은 회원 ID로 비교)
@AllArgsConstructor
@Getter
public class RecordCertRuleDto {
	private final long sellerMemberId;
	// 규칙이 없는 종목이면 null
	private final RecordCertRule rule;

	public static RecordCertRuleDto from(RaceCourse course) {
		return new RecordCertRuleDto(course.getRace().getSeller().getMember().getId(), course.getRecordCertRule());
	}
}
