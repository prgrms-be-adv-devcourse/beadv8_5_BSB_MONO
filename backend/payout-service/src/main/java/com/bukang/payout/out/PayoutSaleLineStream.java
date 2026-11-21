package com.bukang.payout.out;

import java.util.Collection;
import java.util.stream.Stream;

import com.bukang.payout.domain.Payout;

/**
 * 최종 대사: 판매 줄의 참가권 ID만 흘려 읽는다 (CRU-57). 같은 참가권의 판매 줄은 유일 제약으로 하나다
 * PayoutRepository가 이 기능을 함께 내보낸다 (Spring Data 사용자 정의 저장소 조각)
 */
public interface PayoutSaleLineStream {
	/**
	 * 스트림은 트랜잭션 안에서 닫아야 하고, 다 읽기 전에는 같은 연결로 다른 쿼리를 보내지 않는다 (MySQL 스트리밍 제약)
	 */
	Stream<Long> streamSaleLineIds(Collection<Payout> payouts);
}
