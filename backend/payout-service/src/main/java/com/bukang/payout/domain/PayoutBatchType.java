package com.bukang.payout.domain;

/**
 * 정산 배치 종류 (BUK-167). 실행 기록에 쓴다. 대사는 생성 배치 안에서 돌므로 생성에 들어간다
 * DB는 ENUM 칸이다. 값을 더하면 같은 PR에 ENUM 목록을 바꾸는 Flyway SQL을 넣는다 (PayoutEnumColumnTest가 확인)
 */
public enum PayoutBatchType {
	// 03:00 정산 생성 + 대사
	생성,
	// 04:00 지급
	지급,
	// 04:00 지급 뒤 회수 청구 · 알림
	청구
}
