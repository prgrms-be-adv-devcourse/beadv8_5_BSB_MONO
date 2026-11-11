package com.bukang.payout.domain;

/**
 * 실패 기록을 남길 때 배치가 함께 한 조치 (BUK-108). 관리자 「이상 감지」 목록의 「처리」 칸에 그대로 보인다
 * 기록할 때 한 번 적고 바꾸지 않는다. 관리자가 보류를 풀어도 이 값은 그대로다 (보류 상태는 지급보류 표가 갖는다)
 * DB는 ENUM 칸이다. 값을 더하면 같은 PR에 ENUM 목록을 바꾸는 Flyway SQL을 넣는다 (PayoutEnumColumnTest가 확인)
 */
public enum PayoutCreateFailureAction {
	// 대사 불일치로 정산 내역이 지급보류 중이다. 채우기와 함께 일어나도 관리자가 먼저 봐야 하는 이 값으로 적는다
	지급보류,
	// 빠진 참가권을 지급 전 최종 정산 내역에 채웠다
	자동_보정,
	// 기록만 남겼다. 대사 외의 실패는 모두 이 값이다
	기록만
}
