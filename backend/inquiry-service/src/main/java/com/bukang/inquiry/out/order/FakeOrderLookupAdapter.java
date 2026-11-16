package com.bukang.inquiry.out.order;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 가짜 어댑터: 주문 서비스를 띄우지 않고 개발할 때 쓴다. 운영은 http라 HttpOrderLookupAdapter가 답한다
 * 주문 ID를 「회원 ID × 1000 + 대회 ID」로 읽는다. 예) 4001은 회원 4가 대회 1로 결제한 주문, 7012는 회원 7이 대회 12로 결제한 주문
 * 그래서 로그인한 회원 ID로 주문 ID를 만들어 넣으면 내 주문이 되고, 다른 회원의 주문 ID를 넣으면 404가 된다
 * 주문 ID가 1000 미만이면 없는 주문이다 (대회 ID는 1~999)
 * inquiry.clients.order.mode=fake라고 적었을 때만 켜진다. mode가 없으면 빈이 없어 앱이 뜨지 않는다 (matchIfMissing 없음)
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "inquiry.clients.order", name = "mode", havingValue = "fake")
public class FakeOrderLookupAdapter implements OrderLookupPort {
	static final long UNIT = 1000L;

	// 호출마다 경고를 남기면 로그가 묻히므로, 앱을 켤 때 한 번만 알린다
	public FakeOrderLookupAdapter() {
		log.warn("주문 조회는 가짜 응답(FakeOrderLookupAdapter)을 씁니다. 주문 ID = 회원 ID x 1000 + 대회 ID, 1000 미만은 없는 주문입니다.");
	}

	@Override
	public Optional<OrderSummary> findOrder(long orderId) {
		if (orderId < UNIT) {
			return Optional.empty();
		}
		long memberId = orderId / UNIT;
		long raceId = orderId % UNIT;
		OrderItemSummary item = new OrderItemSummary(raceId, "가짜 마라톤 대회 " + raceId, "10K", "기념 티셔츠 M, 완주 메달",
			"PREPARING");
		return Optional.of(new OrderSummary(memberId, "OD-FAKE-" + orderId, 50_000L, "PAID", List.of(item)));
	}
}
