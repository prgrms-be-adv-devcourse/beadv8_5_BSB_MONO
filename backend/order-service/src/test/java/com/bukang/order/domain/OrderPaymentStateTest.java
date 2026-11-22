package com.bukang.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.order.domain.exception.OrderNotPayableException;
import com.bukang.order.out.race.RaceCourseInfo;

/**
 * 주문 결제 상태 전이 (결제 대기 → 결제 중 → 결제 완료, 되돌리기, 만료 판정). 유스케이스 없이 엔티티 규칙만 본다 (CRU-149)
 * 만료(expire)는 이벤트를 발행해 스프링이 필요하므로 거절하는 경우만 본다
 */
class OrderPaymentStateTest {
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 10, 0);
	private static final LocalDateTime DUE = NOW.plusMinutes(15);
	private static final RaceCourseInfo COURSE = new RaceCourseInfo(10, 100, NOW.plusDays(60), NOW.minusDays(1),
		NOW.plusDays(30), 14, null, 7, "종목", 50_000, true,
		LocalDate.of(2026, 11, 20), null);
	private static final ShippingAddress ADDRESS =
		new ShippingAddress("김러너", "010-1234-5678", "04700", "서울 성동구 왕십리로 00", null);

	@Test
	@DisplayName("새 주문은 결제 대기이고 기한 전에는 결제할 수 있다. 기한 시각부터는 기한 지남 코드로 거절한다")
	void checkPayableByDeadline() {
		Order order = newOrder();

		assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
		order.checkPayable(DUE.minusNanos(1));
		assertThatThrownBy(() -> order.checkPayable(DUE))
			.isInstanceOf(OrderNotPayableException.class)
			.extracting("code").isEqualTo(OrderNotPayableException.DEADLINE_PASSED_CODE);
	}

	@Test
	@DisplayName("만료된 주문은 기한 지남으로, 결제 완료 주문은 결제할 수 없는 주문으로 거절한다")
	void checkPayableByStatus() {
		assertThatThrownBy(() -> Order.checkPayable(OrderStatus.EXPIRED, DUE, NOW))
			.extracting("code").isEqualTo(OrderNotPayableException.DEADLINE_PASSED_CODE);
		assertThatThrownBy(() -> Order.checkPayable(OrderStatus.PAID, DUE, NOW))
			.extracting("code").isEqualTo(OrderNotPayableException.CODE);
		assertThatThrownBy(() -> Order.checkPayable(OrderStatus.PAYING, DUE, NOW))
			.extracting("code").isEqualTo(OrderNotPayableException.CODE);
	}

	@Test
	@DisplayName("결제를 시작하면 결제 중이 되고 시작 시각(마이크로초까지)과 시도 1번이 남는다. 결제 중에 다시 시작하면 거절한다")
	void startPaying() {
		Order order = newOrder();
		LocalDateTime startedAt = NOW.plusNanos(123_456_789);

		order.startPaying(startedAt);

		assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYING);
		assertThat(order.getPayStartedAt()).isEqualTo(NOW.plusNanos(123_456_000));
		assertThat(order.getPayAttempts()).isEqualTo(1);
		assertThat(order.countPayAttempt()).isEqualTo(2);
		assertThatThrownBy(() -> order.startPaying(NOW))
			.isInstanceOf(OrderNotPayableException.class);
	}

	@Test
	@DisplayName("결제 중만 결제 대기로 되돌릴 수 있다")
	void revertPaying() {
		Order order = newOrder();
		assertThatThrownBy(order::revertPaying).isInstanceOf(IllegalStateException.class);

		order.startPaying(NOW);
		order.revertPaying();

		assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
	}

	@Test
	@DisplayName("결제 대기는 기한 시각부터, 결제 중은 기한에 여유 시간을 더한 시각부터 만료 대상이다")
	void isOverdue() {
		Order created = newOrder();
		assertThat(created.isOverdue(DUE.minusNanos(1))).isFalse();
		assertThat(created.isOverdue(DUE)).isTrue();

		Order paying = newOrder();
		paying.startPaying(NOW);
		assertThat(paying.isOverdue(DUE)).isFalse();
		assertThat(paying.isOverdue(DUE.plus(Order.PAYING_GRACE).minusNanos(1))).isFalse();
		assertThat(paying.isOverdue(DUE.plus(Order.PAYING_GRACE))).isTrue();
	}

	@Test
	@DisplayName("기한이 지나지 않은 주문을 만료하려 하면 거절하고 상태는 그대로다")
	void expireBeforeDeadline() {
		Order order = newOrder();

		assertThatThrownBy(() -> order.expire(DUE.minusNanos(1))).isInstanceOf(IllegalStateException.class);
		assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
	}

	@Test
	@DisplayName("결제 중 주문을 결제 완료로 바꾸면 주문 상품도 결제 완료가 되고, 배송지가 있으면 발송 대기 배송이 1건 생긴다")
	void markPaidWithKit() {
		Order order = newOrder();
		order.startPaying(NOW);

		order.markPaid(NOW.plusSeconds(1), ADDRESS);

		assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
		assertThat(order.getPaidAt()).isEqualTo(NOW.plusSeconds(1));
		assertThat(order.getItems()).allMatch(item -> item.getStatus() == OrderItemStatus.PAID);
		assertThat(order.getShipments()).hasSize(1);
		assertThat(order.getShipments().getFirst().getStatus()).isEqualTo(ShipmentStatus.READY);
	}

	@Test
	@DisplayName("배송지가 없으면(키트 없는 종목) 배송을 만들지 않는다. 결제 완료를 다시 바꾸려 하면 거절한다")
	void markPaidWithoutKit() {
		Order order = newOrder();

		order.markPaid(NOW, null);

		assertThat(order.getShipments()).isEmpty();
		assertThatThrownBy(() -> order.markPaid(NOW, null)).isInstanceOf(OrderNotPayableException.class);
	}

	private static Order newOrder() {
		ParticipantInfo info = new ParticipantInfo("김러너", LocalDate.of(1994, 6, 25), Gender.MALE, "010-1234-5678",
			"김가족", "010-9876-5432", "L", ADDRESS);
		return Order.forParticipation(Participation.individual(1, 100, 10, info, NOW), COURSE, "key", DUE);
	}
}
