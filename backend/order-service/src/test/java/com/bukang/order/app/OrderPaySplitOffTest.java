package com.bukang.order.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.order.domain.Order;
import com.bukang.order.domain.OrderStatus;
import com.bukang.order.support.StubKitOptionPortConfig;
import com.bukang.order.support.StubRaceCoursePortConfig;
import com.bukang.order.support.StubSeatHoldPortConfig;
import com.bukang.order.support.StubWalletPortConfig;

/**
 * 결제 나누기 스위치를 끈 뒤에도(BUK-232) 남은 결제 중 주문을 끝까지 처리하는지 본다
 * 스위치를 켰다 끈 배포를 흉내 내려고 주문을 직접 결제 중으로 만든다
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubSeatHoldPortConfig.class, StubWalletPortConfig.class,
	StubKitOptionPortConfig.class})
class OrderPaySplitOffTest extends OrderPaySplitTestSupport {

	@Test
	@DisplayName("스위치가 꺼져 있어도 결제 중 주문에 다시 결제하면 202로 지금 주문을 주고, 재확인 배치가 결제 완료로 끝낸다")
	void recheckPaysWhenSwitchOff() throws Exception {
		long orderId = createOrder(1);
		markPaying(orderId);

		pay(1, orderId).andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("PAYING"));
		assertThat(walletPort.payCalls()).isEmpty();

		assertThat(orderPayUseCase.recheck(orderId, LocalDateTime.now())).isTrue();
		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAID);
	}

	@Test
	@DisplayName("스위치가 꺼져 있어도 기한 + 여유가 지난 결제 중 주문은 만료 정리가 끝낸다")
	void expiresWhenSwitchOff() throws Exception {
		long orderId = createOrder(1);
		markPaying(orderId);

		orderExpiryUseCase.expireOverdue(dueOf(orderId).plus(Order.PAYING_GRACE));

		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.EXPIRED);
		assertThat(walletPort.cancelCalls()).hasSize(1);
	}

	@Test
	@DisplayName("스위치가 꺼져 있으면 옛 흐름으로 결제한다 (결제 중을 거치지 않음)")
	void paysWithOldFlow() throws Exception {
		long orderId = createOrder(1);

		pay(1, orderId).andExpect(status().isOk());

		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAID);
		assertThat(orderOf(orderId).getPayStartedAt()).isNull();
	}

	private void markPaying(long orderId) {
		transactionTemplate.executeWithoutResult(status ->
			orderRepository.findByIdForUpdate(orderId).orElseThrow().startPaying(LocalDateTime.now()));
	}
}
