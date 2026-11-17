package com.bukang.payout.out;

import java.util.List;
import java.util.function.LongConsumer;

/**
 * 코스 하나의 결제 완료 주문 상품 ID. 주문 응답대로 들고 있다가, 정산 줄을 만들 때 하나씩 꺼낸다
 */
public interface PaidIds {
	int count();

	// 주문 상품 ID 오름차순
	void forEach(LongConsumer action);

	static PaidIds of(List<Long> ids) {
		return new ListIds(ids);
	}

	record ListIds(List<Long> ids) implements PaidIds {
		@Override
		public int count() {
			return ids.size();
		}

		@Override
		public void forEach(LongConsumer action) {
			ids.forEach(action::accept);
		}
	}
}
