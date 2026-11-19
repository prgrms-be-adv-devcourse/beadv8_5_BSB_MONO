package com.bukang.member.recordcert.out;

import com.bukang.member.recordcert.domain.CourseRule;

/**
 * product-service에 종목의 기록증 규칙과 판매자 회원 ID를 묻는다 (BUK-274 제출, BUK-275 판정, product 내부 API는 BUK-270)
 * 묻지 못하면 RecordCertCheckUnavailableException(503)을 던진다
 */
public interface CourseRulePort {
	SellerCourseRule find(long raceCourseId);

	/**
	 * @param sellerMemberId 대회 판매자의 회원 ID (판매자 ID가 아니다)
	 * @param rule 종목의 기록증 규칙. 규칙이 없는 종목이면 null
	 */
	record SellerCourseRule(long sellerMemberId, CourseRule rule) {
	}
}
