package com.bukang.inquiry.support;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.bukang.inquiry.domain.exception.InquiryExternalServiceUnavailableException;
import com.bukang.inquiry.out.order.OrderLookupPort;
import com.bukang.inquiry.out.order.OrderSummary;

/**
 * 테스트용 주문 서비스. 테스트가 정한 주문만 있고, 주문 서비스가 멈춘 상황도 만들 수 있다
 */
public class StubOrderLookupPort implements OrderLookupPort {
	private final Map<Long, OrderSummary> orders = new ConcurrentHashMap<>();
	private volatile boolean unavailable;

	@Override
	public Optional<OrderSummary> findOrder(long orderId) {
		if (unavailable) {
			throw new InquiryExternalServiceUnavailableException("주문 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.");
		}
		return Optional.ofNullable(orders.get(orderId));
	}

	public void order(long orderId, OrderSummary order) {
		orders.put(orderId, order);
	}

	public void setUnavailable(boolean unavailable) {
		this.unavailable = unavailable;
	}

	public void clear() {
		orders.clear();
		unavailable = false;
	}
}
