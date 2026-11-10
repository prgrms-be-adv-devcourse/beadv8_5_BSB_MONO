package com.bukang.payout.domain;

import java.util.Collection;

/**
 * 최종 정산 대사의 참가권 비교 (BUK-67): 주문 판매 내역의 참가권 ID와 정산 판매 줄의 참가권 ID를 맞춰 본다
 * 집합(SetTicketDiffer)과 비트맵(BitmapTicketDiffer) 두 방식을 두고 같은 결과를 내게 한다 (AC-67-9)
 * 운영 배치는 집합을 쓴다. 메모리 · 전송 실험(지시서 4-1장) 뒤 비트맵이 나으면 바꾼다
 * 같은 참가권의 판매 줄은 DB 유일 제약(order_item_id, record_type)으로 한 줄뿐이라 중복은 따로 보지 않는다
 */
public interface TicketDiffer {
	// 쓸 방식을 고르는 설정 (set | bitmap, 없으면 set). 방식마다 앱을 다시 띄워 메모리를 따로 잰다
	String METHOD_PROPERTY = "payout.reconcile.method";

	TicketDiff diff(Collection<Long> soldIds, Collection<Long> payoutIds);
}
