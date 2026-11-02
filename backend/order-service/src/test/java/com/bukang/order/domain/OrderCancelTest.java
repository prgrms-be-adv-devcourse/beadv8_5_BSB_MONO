package com.bukang.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.order.domain.exception.OrderItemNotCancelableException;
import com.bukang.order.out.race.RaceCourseInfo;

/**
 * 주문 상품 단위 취소에 따른 주문 상태 (PARTIAL_CANCELED / CANCELED)
 * 지금 주문은 상품 1개짜리뿐이라(장바구니 결제 전), 상품 2개짜리 주문을 직접 만들어 확인한다
 */
class OrderCancelTest {
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 10, 0);
	private static final RaceCourseInfo COURSE = new RaceCourseInfo(10, 100, NOW.plusDays(60), NOW.minusDays(1),
		NOW.plusDays(30), 14, null, 7, "종목", 50_000, true,
		LocalDate.of(2026, 11, 20), null);

	@Test
	@DisplayName("상품 2개 중 1개를 취소하면 PARTIAL_CANCELED, 나머지도 취소하면 CANCELED가 되고 취소 금액이 쌓인다")
	void cancelItems() {
		Order order = paidOrderWithTwoItems();
		OrderItem first = order.getItems().get(0);
		OrderItem second = order.getItems().get(1);

		order.cancelItem(first, NOW);
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIAL_CANCELED);
		assertThat(order.getCanceledAmount()).isEqualTo(50_000);
		assertThat(first.getStatus()).isEqualTo(OrderItemStatus.CANCELED);
		assertThat(first.getCanceledAmount()).isEqualTo(50_000);
		assertThat(first.getCanceledAt()).isEqualTo(NOW);
		assertThat(second.getStatus()).isEqualTo(OrderItemStatus.PAID);

		order.cancelItem(second, NOW.plusMinutes(1));
		assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
		assertThat(order.getCanceledAmount()).isEqualTo(100_000);
	}

	@Test
	@DisplayName("이미 취소한 상품을 다시 취소하면 거절되고 취소 금액은 그대로다")
	void cancelTwice() {
		Order order = paidOrderWithTwoItems();
		OrderItem first = order.getItems().get(0);
		order.cancelItem(first, NOW);

		assertThatThrownBy(() -> order.cancelItem(first, NOW))
			.isInstanceOf(OrderItemNotCancelableException.class)
			.hasMessage("이미 취소한 상품입니다.");
		assertThat(order.getCanceledAmount()).isEqualTo(50_000);
	}

	@Test
	@DisplayName("취소를 시작하면 취소 중과 시작 시각만 남고 주문 상태는 그대로다. 환불이 확인돼 마무리하면 취소가 된다")
	void startThenCompleteCancel() {
		Order order = paidOrderWithTwoItems();
		OrderItem first = order.getItems().get(0);

		order.startCancelItem(first, NOW);
		assertThat(first.getStatus()).isEqualTo(OrderItemStatus.CANCELING);
		assertThat(first.getCancelRequestedAt()).isEqualTo(NOW);
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
		assertThat(order.getCanceledAmount()).isZero();
		assertThatThrownBy(() -> order.startCancelItem(first, NOW))
			.isInstanceOf(OrderItemNotCancelableException.class)
			.hasMessage("취소 처리 중인 상품입니다. 잠시 뒤 주문 내역에서 확인해주세요.");

		order.cancelItem(first, NOW.plusMinutes(1));
		assertThat(first.getStatus()).isEqualTo(OrderItemStatus.CANCELED);
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIAL_CANCELED);
		assertThat(order.getCanceledAmount()).isEqualTo(50_000);
	}

	@Test
	@DisplayName("환불이 거절되면 취소 중을 결제 완료로 되돌리고 시작 시각을 지운다. 취소 중이 아니면 되돌릴 수 없다")
	void revertCancel() {
		Order order = paidOrderWithTwoItems();
		OrderItem first = order.getItems().get(0);
		order.startCancelItem(first, NOW);

		order.revertCancelItem(first);
		assertThat(first.getStatus()).isEqualTo(OrderItemStatus.PAID);
		assertThat(first.getCancelRequestedAt()).isNull();
		assertThatThrownBy(() -> order.revertCancelItem(first)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("대회 취소 일괄 환불은 결제 완료만 고르고 취소 중은 건너뛴다 (재처리가 같은 주문 ID로 환불하고 마무리한다)")
	void cancelPaidItemsSkipsCanceling() {
		Order order = paidOrderWithTwoItems();
		OrderItem first = order.getItems().get(0);
		OrderItem second = order.getItems().get(1);
		order.startCancelItem(first, NOW);

		assertThat(order.cancelPaidItems(NOW.plusMinutes(1))).containsExactly(second);
		assertThat(first.getStatus()).isEqualTo(OrderItemStatus.CANCELING);
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIAL_CANCELED);
		assertThat(order.getCanceledAmount()).isEqualTo(50_000);
	}

	private static Order paidOrderWithTwoItems() {
		Order order = Order.forParticipation(participation(1), COURSE, "key", NOW.plusMinutes(10));
		order.getItems().add(OrderItem.ticket(order, participation(2), COURSE));
		order.markPaid(NOW, null);
		return order;
	}

	private static Participation participation(long memberId) {
		ParticipantInfo info = new ParticipantInfo("김러너", LocalDate.of(1994, 6, 25), Gender.MALE, "010-1234-5678",
			"김가족", "010-9876-5432", "L",
			new ShippingAddress("김러너", "010-1234-5678", "04700", "서울 성동구 왕십리로 00", null));
		return Participation.individual(memberId, 100, 10, info, NOW);
	}
}
