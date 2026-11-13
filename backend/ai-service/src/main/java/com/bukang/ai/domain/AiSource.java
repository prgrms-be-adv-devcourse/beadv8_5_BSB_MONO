package com.bukang.ai.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 답의 출처. 도구 하나가 화면 하나를 가리킨다 (어느 화면 기준인지, 그 화면으로 가는 버튼)
 * 모델이 만들지 않고, 부른 도구를 보고 코드가 붙인다. 경로는 프론트 라우트(frontend/src/app)와 같게 둔다
 * 화면 이름·버튼 글은 추천값(미확정)
 */
@Getter
@RequiredArgsConstructor
public enum AiSource {
	SELLER_KITS("주문 관리 · 키트 배송 현황", "키트 배송 관리로 가기", "/seller/kits"),
	SELLER_PAYOUT("정산 · 정산 요약", "정산으로 가기", "/seller/settlements"),
	SELLER_HOLDS("정산 · 지급보류 내역", "지급보류 내역 보기", "/seller/settlements?tab=holds"),
	// 관리자 문의 화면은 프론트 라우트가 아직 없어 버튼을 붙이지 않는다
	ADMIN_INQUIRIES("문의 관리 · 답변 대기", null, null),
	ADMIN_SELLERS("판매자 승인 · 승인 대기", "판매자 승인으로 가기", "/admin/sellers");

	private final String screen;
	private final String buttonLabel;
	private final String path;
}
