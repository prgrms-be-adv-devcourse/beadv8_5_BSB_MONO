package com.bukang.order.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.order.app.OrderPayRecheckUseCase.Run;
import com.bukang.order.app.OrderPayRecheckUseCase.Stop;
import com.bukang.order.domain.OrderStatus;
import com.bukang.order.support.StubKitOptionPortConfig;
import com.bukang.order.support.StubRaceCoursePort;
import com.bukang.order.support.StubRaceCoursePortConfig;
import com.bukang.order.support.StubSeatHoldPortConfig;
import com.bukang.order.support.StubWalletPortConfig;

/**
 * 결제 재확인 배치 한 회차 (BUK-232): 남은 결제 중을 다 볼 때까지 반복하고, 시간 상한·예치금 장애에서 멈추는지 본다
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubSeatHoldPortConfig.class, StubWalletPortConfig.class,
	StubKitOptionPortConfig.class})
class OrderPayRecheckTest extends OrderPaySplitTestSupport {
	private static final Duration BUDGET = Duration.ofSeconds(10);

	@Autowired
	private OrderPayRecheckUseCase orderPayRecheckUseCase;

	@Test
	@DisplayName("결제 중 100건을 한 회차에 30건씩 반복해 모두 결제 완료로 끝낸다")
	void finishesAllInOneRun() throws Exception {
		List<Long> orderIds = payingOrders(100);

		Run run = recheckStale(BUDGET);

		assertThat(run).isEqualTo(new Run(100, 100, Stop.DONE));
		assertThat(orderIds).allSatisfy(orderId -> assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAID));
	}

	@Test
	@DisplayName("시간 상한을 넘으면 그 회차를 멈추고 남은 결제 중은 다음 회차에 넘긴다")
	void stopsAtTimeLimit() throws Exception {
		List<Long> orderIds = payingOrders(3);

		Run run = recheckStale(Duration.ZERO);

		assertThat(run).isEqualTo(new Run(1, 1, Stop.TIME_LIMIT));
		assertThat(orderIds.stream().filter(orderId -> statusOf(orderId) == OrderStatus.PAYING)).hasSize(2);
	}

	@Test
	@DisplayName("예치금 결과를 잇달아 모르면 그 회차를 멈춰 장애 중에 계속 부르지 않는다")
	void stopsOnRepeatedFailures() throws Exception {
		List<Long> orderIds = payingOrders(40);
		walletPort.setPayTimesOut(true);

		Run run = recheckStale(BUDGET);

		assertThat(run).isEqualTo(new Run(OrderPayRecheckUseCase.FAILURE_LIMIT, 0, Stop.FAILURES));
		assertThat(walletPort.payCalls()).hasSize(OrderPayRecheckUseCase.FAILURE_LIMIT);
		assertThat(orderIds).allSatisfy(orderId -> assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAYING));
	}

	@Test
	@DisplayName("결과를 모르는 주문이 섞여도 회차를 멈추지 않고, 한 회차에 같은 주문을 두 번 부르지 않는다")
	void skipsUnknownWithoutRepeating() throws Exception {
		List<Long> orderIds = payingOrders(OrderPayRecheckUseCase.BATCH_SIZE + 5);
		long stuck = orderIds.getFirst();
		walletPort.failPayFor(stuck);

		Run run = recheckStale(BUDGET);

		assertThat(run).isEqualTo(new Run(orderIds.size(), orderIds.size() - 1, Stop.DONE));
		assertThat(statusOf(stuck)).isEqualTo(OrderStatus.PAYING);
		assertThat(orderOf(stuck).getPayAttempts()).isEqualTo(2);
	}

	@Test
	@DisplayName("재확인 배치도 예치금 결제 동시 호출 자리를 지나며, 못 받으면 되돌리지 않고 결제 중으로 둔다 (앞선 시도에 돈이 빠졌을 수 있음)")
	void busyKeepsPaying() throws Exception {
		List<Long> orderIds = payingOrders(OrderPayRecheckUseCase.FAILURE_LIMIT + 1);
		walletPort.setPayBusy(true);

		Run run = recheckStale(BUDGET);

		// 결과를 모르는 것으로 세어 잇달아 나오면 회차를 멈춘다 (몰린 예치금을 계속 두드리지 않는다)
		assertThat(run).isEqualTo(new Run(OrderPayRecheckUseCase.FAILURE_LIMIT, 0, Stop.FAILURES));
		assertThat(orderIds).allSatisfy(orderId -> assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PAYING));
		assertThat(walletPort.payCalls()).isEmpty();
	}

	private Run recheckStale(Duration budget) {
		LocalDateTime now = LocalDateTime.now();
		return orderPayRecheckUseCase.recheckStale(now, now.plusMinutes(1), budget);
	}

	// 회원마다 결제 대기 주문을 만들고, 결제 요청이 결과를 모른 채 끝난 것처럼 결제 중으로 둔다
	private List<Long> payingOrders(int count) throws Exception {
		// 가짜 예치금은 잔액 하나를 함께 쓴다. 모든 주문을 낼 만큼 넣는다
		walletPort.setBalance(StubRaceCoursePort.PRICE * count);
		List<Long> orderIds = new ArrayList<>();
		for (long memberId = 1; memberId <= count; memberId++) {
			long orderId = createOrder(memberId);
			transactionTemplate.executeWithoutResult(status ->
				orderRepository.findByIdForUpdate(orderId).orElseThrow().startPaying(LocalDateTime.now()));
			orderIds.add(orderId);
		}
		return orderIds;
	}
}
