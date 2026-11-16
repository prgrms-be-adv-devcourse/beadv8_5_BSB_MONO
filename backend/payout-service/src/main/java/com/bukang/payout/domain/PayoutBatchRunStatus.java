package com.bukang.payout.domain;

/**
 * 정산 배치 실행 기록의 상태 (BUK-167, 11/11 결정). DB는 ENUM 칸이다 (PayoutEnumColumnTest)
 */
public enum PayoutBatchRunStatus {
	// 시작 줄을 먼저 커밋한다. 끝 줄을 못 쓰고 서버가 죽으면 이 값으로 남는다
	진행_중,
	// 끝까지 돌았고 문제 건이 없다
	완료,
	// 끝까지 돌았지만 실패 · 결과 모름 · 불일치가 1건 이상이다
	일부_실패,
	// 예외로 중간에 멈췄다
	실패
}
