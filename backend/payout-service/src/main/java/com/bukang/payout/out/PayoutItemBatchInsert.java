package com.bukang.payout.out;

import java.util.List;

import com.bukang.payout.domain.PayoutItem;

/**
 * 정산 줄을 JPA 영속성 컨텍스트를 거치지 않고 JDBC로 일괄 저장한다 (BUK-252)
 * PayoutRepository가 이 기능을 함께 내보낸다 (Spring Data 사용자 정의 저장소 조각)
 */
public interface PayoutItemBatchInsert {
	// 한 번에 DB로 보내는 줄 수. 생성도 이만큼 모이면 넘기고 비워, 줄 전체를 들고 있지 않는다
	int BATCH_SIZE = 1000;

	/**
	 * 정산 내역에 붙는 참가권 줄(판매 · 수수료)만 받는다. 정산 내역은 먼저 저장해 ID가 있어야 한다
	 * 부르는 쪽 트랜잭션 안에서 돈다. 실패하면 정산 내역 · 이미 넣은 줄과 함께 롤백된다
	 */
	void insertItems(List<PayoutItem> items);
}
