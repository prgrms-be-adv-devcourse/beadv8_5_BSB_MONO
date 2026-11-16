package com.bukang.order.app;

import com.bukang.order.dto.OrderDto;

// alreadyPaid가 true면 이미 결제한 주문이라 아무것도 바꾸지 않고 그대로 돌려준 것이다
// pending이 true면 결제 중(결과 확인 중)이라 지금 주문을 돌려준 것이다(202, BUK-232)
public record OrderPayResult(OrderDto order, boolean alreadyPaid, boolean pending) {
	public static OrderPayResult paid(OrderDto order) {
		return new OrderPayResult(order, false, false);
	}

	public static OrderPayResult alreadyPaid(OrderDto order) {
		return new OrderPayResult(order, true, false);
	}

	public static OrderPayResult pending(OrderDto order) {
		return new OrderPayResult(order, false, true);
	}
}
