package com.bukang.product.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 아웃박스 이벤트의 상태: 미발행 → 발행 완료, 또는 미발행 → (재시도를 다 씀) 실패
 */
@Getter
@RequiredArgsConstructor
public enum OutboxStatus {
	PENDING("미발행"),
	SENT("발행 완료"),
	// 재시도 횟수를 다 써서 릴레이가 더는 보내지 않는다. 원인을 고친 뒤 PENDING으로 되돌리면 다시 보낸다
	FAILED("실패");

	// 화면과 메시지에 쓰는 이름
	private final String label;
}
