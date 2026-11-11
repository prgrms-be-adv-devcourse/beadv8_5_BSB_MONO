package com.bukang.inquiry.out;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.inquiry.out.member.FakeMemberNicknamesAdapter;
import com.bukang.inquiry.out.member.FakeMemberSellerAdapter;
import com.bukang.inquiry.out.order.FakeOrderLookupAdapter;
import com.bukang.inquiry.out.order.OrderSummary;

class FakeOrderLookupAdapterTest {
	private final FakeOrderLookupAdapter adapter = new FakeOrderLookupAdapter();

	@Test
	@DisplayName("가짜 주문 서비스: 주문 ID가 「회원 ID×1000 + 대회 ID」이면 그 회원이 그 대회로 결제한 주문이다")
	void fakeOrder() {
		OrderSummary order = adapter.findOrder(4001L).orElseThrow();

		assertThat(order.memberId()).isEqualTo(4L);
		assertThat(order.items()).hasSize(1);
		assertThat(order.items().getFirst().raceId()).isEqualTo(1L);
		assertThat(order.orderNo()).isNotBlank();
		assertThat(order.paymentAmount()).isPositive();
		assertThat(order.items().getFirst().deliveryStatus()).isNotBlank();
	}

	@Test
	@DisplayName("가짜 주문 서비스: 주문 ID가 1000 미만이면 없는 주문이다")
	void fakeNoOrder() {
		assertThat(adapter.findOrder(999L)).isEmpty();
		assertThat(adapter.findOrder(1L)).isEmpty();
	}

	@Test
	@DisplayName("가짜 회원 서비스: 누구나 승인된 판매자이고 닉네임은 「러너{회원 ID}」다 (개발 전용)")
	void fakeMember() {
		assertThat(new FakeMemberSellerAdapter().isApprovedSeller(5L)).isTrue();
		assertThat(new FakeMemberNicknamesAdapter().findNicknames(java.util.List.of(5L)))
			.containsOnly(java.util.Map.entry(5L, "러너5"));
	}
}
