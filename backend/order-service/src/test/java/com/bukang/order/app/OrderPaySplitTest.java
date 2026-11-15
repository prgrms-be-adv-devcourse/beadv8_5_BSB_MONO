package com.bukang.order.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.common.shared.order.event.RaceRefundEvent;
import com.bukang.common.shared.order.event.RaceRefundKind;
import com.bukang.order.domain.Order;
import com.bukang.order.domain.OrderStatus;
import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.domain.exception.OrderNotPayableException;
import com.bukang.order.support.SeatOutboxMessages;
import com.bukang.order.support.StubKitOptionPortConfig;
import com.bukang.order.support.StubRaceCoursePort;
import com.bukang.order.support.StubRaceCoursePortConfig;
import com.bukang.order.support.StubSeatHoldPortConfig;
import com.bukang.order.support.StubWalletPortConfig;

/**
 * 결제 나누기 (BUK-232): 판매 수 +1·결제 중을 먼저 커밋하고 예치금·자리 판매를 잠금 밖에서 부른다
 * 결과를 모르는 결제는 결제 중으로 남고 재확인 배치(recheck)나 만료 정리가 끝낸다. 돈·판매 수·자리가 어긋나지 않는지 본다
 */
@SpringBootTest(properties = "order.pay.split.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubSeatHoldPortConfig.class, StubWalletPortConfig.class,
	StubKitOptionPortConfig.class})
class OrderPaySplitTest extends OrderPaySplitTestSupport {
	private static final long PRICE = StubRaceCoursePort.PRICE;

	@Autowired
	private RaceCancelRefundUseCase raceCancelRefundUseCase;

	@Test
	@DisplayName("결제하면 결제 중을 거쳐 결제 완료가 되고, 판매 수·옵션 사본·배송이 한 번씩 남는다")
	void pays() throws Exception {
		long orderId = createOrder(1);

		pay(1, orderId).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PAID"));

		assertThat(participationStatusOf(orderId)).isEqualTo(ParticipationStatus.CONFIRMED);
		assertThat(sold(SHIRT_M)).isEqualTo(1);
		assertThat(choicesOf(orderId)).isEqualTo(1);
		assertThat(shipmentsOf(orderId)).isEqualTo(1);
		assertThat(walletPort.balance()).isEqualTo(BALANCE - PRICE);
	}

	@Test
	@DisplayName("자리 판매가 연결 거절되면 202로 결제 중에 두고, 다시 눌러도 다시 빼지 않으며, 재확인 배치가 결제 완료로 끝낸다")
	void sellRefusedThenRecheckPays() throws Exception {
		long orderId = createOrder(1);
		seatHoldPort.setSellFails(true);

		pay(1, orderId).andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("PAYING"));
		pay(1, orderId).andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("PAYING"));

		// 결제 중 동안 주문 상품은 결제 대기(ORDERED) 그대로라 판매 내역에 안 잡힌다
		assertThat(orderItemRepository.findSoldEntries(RACE_ID)).isEmpty();
		assertThat(walletPort.payCalls()).hasSize(1);
		assertThat(sold(SHIRT_M)).isEqualTo(1);

		seatHoldPort.setSellFails(false);
		assertThat(orderPayUseCase.recheck(orderId, LocalDateTime.now())).isTrue();

		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAID);
		assertThat(participationStatusOf(orderId)).isEqualTo(ParticipationStatus.CONFIRMED);
		assertThat(seatHoldPort.isSold(participationIdOf(orderId))).isTrue();
		assertThat(walletPort.balance()).isEqualTo(BALANCE - PRICE);
		assertThat(sold(SHIRT_M)).isEqualTo(1);
		assertThat(orderItemRepository.findSoldEntries(RACE_ID)).hasSize(1);
		assertThat(walletPort.cancelCalls()).isEmpty();
	}

	@Test
	@DisplayName("예치금 결과를 모르면 202로 결제 중에 두고, 재확인 배치가 같은 주문 ID로 다시 불러 한 번만 차감한 채 결제 완료로 끝낸다")
	void walletUnknownThenRecheckPays() throws Exception {
		long orderId = createOrder(1);
		walletPort.setPayTimesOut(true);

		pay(1, orderId).andExpect(status().isAccepted());
		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAYING);

		walletPort.setPayTimesOut(false);
		orderPayUseCase.recheck(orderId, LocalDateTime.now());

		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAID);
		assertThat(orderOf(orderId).getPayAttempts()).isEqualTo(2);
		assertThat(walletPort.balance()).isEqualTo(BALANCE - PRICE);
	}

	@Test
	@DisplayName("결과를 모르는 사이 자리 기한이 지나면, 재확인 배치가 결제를 취소하고 판매 수·옵션을 되돌려 결제 대기로 돌린다")
	void walletUnknownThenSeatExpiredReverts() throws Exception {
		long orderId = createOrder(1);
		walletPort.setPayTimesOut(true);
		pay(1, orderId).andExpect(status().isAccepted());
		seatHoldPort.expire(participationIdOf(orderId));
		walletPort.setPayTimesOut(false);

		assertThatThrownBy(() -> orderPayUseCase.recheck(orderId, LocalDateTime.now()))
			.isInstanceOf(OrderNotPayableException.class);

		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CREATED);
		assertThat(walletPort.chargedOrders()).doesNotContain(orderId);
		assertThat(walletPort.balance()).isEqualTo(BALANCE);
		assertThat(sold(SHIRT_M)).isZero();
		assertThat(choicesOf(orderId)).isZero();
	}

	@Test
	@DisplayName("잔액이 모자라면 판매 수·옵션을 되돌려 결제 대기로 두고, 결제 취소는 부르지 않는다(다시 결제할 수 있게)")
	void insufficientReverts() throws Exception {
		long orderId = createOrder(1);
		walletPort.setBalance(0);

		pay(1, orderId).andExpect(status().isConflict());

		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CREATED);
		assertThat(sold(SHIRT_M)).isZero();
		assertThat(choicesOf(orderId)).isZero();
		assertThat(walletPort.cancelCalls()).isEmpty();

		walletPort.setBalance(BALANCE);
		pay(1, orderId).andExpect(status().isOk());
		assertThat(sold(SHIRT_M)).isEqualTo(1);
		assertThat(choicesOf(orderId)).isEqualTo(1);
	}

	@Test
	@DisplayName("예치금이 4xx로 거절하면 판매 수·옵션을 되돌려 결제 대기로 두고, 결제 취소는 부르지 않는다")
	void walletRejectedReverts() throws Exception {
		long orderId = createOrder(1);
		walletPort.setPayRejects(true);

		pay(1, orderId).andExpect(status().isServiceUnavailable());

		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CREATED);
		assertThat(sold(SHIRT_M)).isZero();
		assertThat(choicesOf(orderId)).isZero();
		assertThat(walletPort.cancelCalls()).isEmpty();
	}

	@Test
	@DisplayName("결제 중에 참가 신청이 결제 대기가 아니게 되면, 확정 대신 결제 취소·자리 복구·판매 수 되돌림 뒤 결제 대기로 돌린다")
	void participationChangedWhilePaying() throws Exception {
		long orderId = createOrder(1);
		long participationId = participationIdOf(orderId);
		seatHoldPort.setSellFails(true);
		pay(1, orderId).andExpect(status().isAccepted());
		transactionTemplate.executeWithoutResult(status ->
			participationRepository.findByIdForUpdate(participationId).orElseThrow().markReleased(LocalDateTime.now()));
		seatHoldPort.setSellFails(false);

		assertThatThrownBy(() -> orderPayUseCase.recheck(orderId, LocalDateTime.now()))
			.isInstanceOf(OrderNotPayableException.class);

		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CREATED);
		assertThat(walletPort.chargedOrders()).doesNotContain(orderId);
		assertThat(sold(SHIRT_M)).isZero();
		assertThat(choicesOf(orderId)).isZero();
		assertThat(SeatOutboxMessages.restored(orderOutboxKafkaEventRepository)).containsExactly(participationId);
	}

	@Test
	@DisplayName("요청과 재확인 배치가 같은 결제 중 주문을 동시에 확정해도 한 번만 확정된다 (5번 반복)")
	void requestAndRecheckConfirmOnce() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			for (long memberId = 1; memberId <= 5; memberId++) {
				long orderId = createOrder(memberId);
				long finalMemberId = memberId;
				CountDownLatch entered = walletPort.holdPay();
				Future<Integer> request = executor.submit(
					() -> pay(finalMemberId, orderId).andReturn().getResponse().getStatus());
				assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
				Future<Boolean> batch = executor.submit(() -> orderPayUseCase.recheck(orderId, LocalDateTime.now()));
				// 재확인 배치도 예치금 앞까지 오게 잠깐 기다린 뒤 둘을 함께 풀어 준다
				Thread.sleep(100);
				walletPort.releasePay();

				assertThat(request.get(30, TimeUnit.SECONDS)).isEqualTo(200);
				assertThat(batch.get(30, TimeUnit.SECONDS)).isTrue();
				assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAID);
				assertThat(shipmentsOf(orderId)).isEqualTo(1);
				assertThat(choicesOf(orderId)).isEqualTo(1);
				assertThat(sold(SHIRT_M)).isEqualTo((int)memberId);
				assertThat(walletPort.balance()).isEqualTo(BALANCE - PRICE * memberId);
			}
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	@DisplayName("결제 중에 대회가 취소되면 환불 완료를 보내지 않고 실패로 세며, 결제 완료 뒤 다시 처리하면 환불한다")
	void raceCanceledWhilePaying() throws Exception {
		long orderId = createOrder(1);
		walletPort.setPayTimesOut(true);
		pay(1, orderId).andExpect(status().isAccepted());
		LocalDateTime now = LocalDateTime.now();

		RaceCancelRefundUseCase.RefundResult first = raceCancelRefundUseCase.refundAll(RACE_ID, now);

		assertThat(first).isEqualTo(new RaceCancelRefundUseCase.RefundResult(0, 1));
		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAYING);
		assertThat(walletPort.cancelCalls()).isEmpty();
		assertThat(orderItemRepository.countNotRefunded(RACE_ID)).isEqualTo(1);
		assertThat(raceRefunds()).extracting(RaceRefundEvent::kind).containsExactly(RaceRefundKind.HELD);

		walletPort.setPayTimesOut(false);
		orderPayUseCase.recheck(orderId, LocalDateTime.now());
		RaceCancelRefundUseCase.RefundResult again = raceCancelRefundUseCase.refundAll(RACE_ID, now);

		assertThat(again).isEqualTo(new RaceCancelRefundUseCase.RefundResult(1, 0));
		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CANCELED);
		assertThat(walletPort.balance()).isEqualTo(BALANCE);
		assertThat(sold(SHIRT_M)).isZero();
		assertThat(raceRefunds()).extracting(RaceRefundEvent::kind)
			.containsExactly(RaceRefundKind.HELD, RaceRefundKind.HELD, RaceRefundKind.REFUNDED);
	}

	@Test
	@DisplayName("결제 중인 참가권은 결제 기한이 지나도 끝나지 않은 참가권으로 세어 탈퇴를 막는다")
	void payingBlocksWithdrawal() throws Exception {
		long orderId = createOrder(1);
		walletPort.setPayTimesOut(true);
		pay(1, orderId).andExpect(status().isAccepted());

		LocalDateTime afterDue = dueOf(orderId).plusHours(1);
		assertThat(orderItemRepository.countActiveTickets(1, LocalDate.now().atStartOfDay(), afterDue)).isEqualTo(1);
	}

	@Test
	@DisplayName("결제 중으로 기한 + 여유가 지나면 만료 정리가 결제 취소·자리 복구·판매 수 되돌림 뒤 만료한다 (기한만 지나면 기다린다)")
	void expiresStalePaying() throws Exception {
		long orderId = createOrder(1);
		walletPort.setPayTimesOut(true);
		pay(1, orderId).andExpect(status().isAccepted());
		LocalDateTime due = dueOf(orderId);
		long participationId = participationIdOf(orderId);

		orderExpiryUseCase.expireOverdue(due.plusMinutes(1));
		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAYING);

		orderExpiryUseCase.expireOverdue(due.plus(Order.PAYING_GRACE));

		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.EXPIRED);
		assertThat(participationStatusOf(orderId)).isEqualTo(ParticipationStatus.RELEASED);
		assertThat(walletPort.chargedOrders()).doesNotContain(orderId);
		assertThat(sold(SHIRT_M)).isZero();
		assertThat(choicesOf(orderId)).isZero();
		assertThat(SeatOutboxMessages.restored(orderOutboxKafkaEventRepository)).containsExactly(participationId);
	}

	@Test
	@DisplayName("판매 뒤 확정 전에 만료 정리가 먼저 끝내면, 늦게 닿은 확정이 결제를 취소하고 자리 복구를 한 번 더 남긴다")
	void lateConfirmLeavesRestore() throws Exception {
		long orderId = createOrder(1);
		long participationId = participationIdOf(orderId);
		LocalDateTime afterGrace = dueOf(orderId).plus(Order.PAYING_GRACE).plusMinutes(1);
		seatHoldPort.afterSell(() -> orderExpiryUseCase.expireOverdue(afterGrace));

		pay(1, orderId).andExpect(status().isConflict());

		assertThat(statusOf(orderId)).isEqualTo(OrderStatus.EXPIRED);
		assertThat(walletPort.chargedOrders()).doesNotContain(orderId);
		assertThat(walletPort.balance()).isEqualTo(BALANCE);
		assertThat(sold(SHIRT_M)).isZero();
		// 만료가 남긴 복구 + 늦은 확정이 남긴 복구
		assertThat(SeatOutboxMessages.restored(orderOutboxKafkaEventRepository))
			.containsExactly(participationId, participationId);
	}

	private List<RaceRefundEvent> raceRefunds() {
		return SeatOutboxMessages.raceRefunds(orderOutboxKafkaEventRepository, jsonConverter);
	}
}
