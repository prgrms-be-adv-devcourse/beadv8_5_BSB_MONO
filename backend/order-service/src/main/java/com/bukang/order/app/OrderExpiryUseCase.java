package com.bukang.order.app;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.domain.Order;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.wallet.WalletPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 기한(15분)이 지난 결제 대기 건을 정리한다 (정책 4장 결제, ERD 추천안 v4 9쪽 6번)
 * 주문마다 예치금 결제 취소(D) → 주문 CREATED → EXPIRED, 참가 신청 APPLIED → RELEASED
 * 결제 취소를 부르는 이유: D 결제가 커밋된 뒤 주문 쪽이 롤백되면(판매 호출 오류, 커밋 실패, 서버 재시작, 응답 시간 초과)
 * 돈만 빠진 결제 대기 주문이 남는다. 그 주문은 결국 여기로 오므로, 결제하지 않은 주문까지 모두 불러 돌려받는다
 * (결제하지 않은 주문이면 D가 "돌려줄 것 없음"으로 끝내고 뒤늦게 온 결제를 막는다, 10/13 김소희님 요청)
 * 자리: 판매된 채 남은 자리는 복구(product.hold.restore)하고, 결제하지 않은 자리는 해제(product.hold.cancel)해 달라고 알린다
 * (B는 기한 지난 자리를 스스로 풀지 않는다, 10/14 김호빈님과 정함, PRO-166). 두 메시지 모두 만료 트랜잭션 안에서
 * 아웃박스에 저장하고 커밋 뒤 보낸다 (PRO-270). 만료가 롤백되면 메시지도 없고, 커밋되면 Kafka가 잠시 멈춰도 다시 보낸다
 * 여기의 payment_due_at은 화면·정리용 사본이라, 정리가 몇 분 늦어도 결제 판정에는 영향이 없다 (판정 원본은 B)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderExpiryUseCase {
	// 한 번의 정리에서 처리할 최대 주문 수. 주문마다 예치금 호출(시간 제한 2초)을 하므로, D가 느려도 한 번이 수 분 안에 끝난다
	// 남은 주문은 다음 실행(1분 뒤)에서 이어서 처리한다
	static final int BATCH_SIZE = 100;

	private final ParticipationRepository participationRepository;
	private final OrderRepository orderRepository;
	private final WalletPort walletPort;
	private final OrderOutboxKafkaAppendUseCase orderOutboxKafkaAppendUseCase;
	private final TransactionTemplate transactionTemplate;

	/**
	 * @param releasedParticipations 미결제 해제한 참가 신청 수
	 * @param expiredOrders          만료한 주문 수
	 * @param retryOrders            만료 처리(결제 취소 등)에 실패해 그대로 둔 주문 수. 다음 실행에서 다시 시도한다
	 */
	public record ExpiryResult(int releasedParticipations, int expiredOrders, int retryOrders) {
	}

	/**
	 * 주문마다 트랜잭션을 따로 연다. 한 주문의 결제 취소가 실패해도 그 주문만 롤백되어 결제 대기로 남고,
	 * 다음 실행(1분 뒤)에 다시 대상이 된다. 결제 취소를 먼저 부르고 만료하므로, 돌려받지 못한 채 만료되는 주문은 없다
	 * 바깥 트랜잭션 없이 부른다 (OrderExpiryScheduler)
	 */
	public ExpiryResult expireOverdue(LocalDateTime now) {
		List<Long> overdueOrderIds = orderRepository.findOverdueIds(now, PageRequest.of(0, BATCH_SIZE));
		int expired = 0;
		int released = 0;
		int retry = 0;
		for (long orderId : overdueOrderIds) {
			try {
				Expired result = transactionTemplate.execute(status -> expireLocked(orderId, now));
				if (result.order()) {
					expired++;
				}
				if (result.participation()) {
					released++;
				}
			} catch (RuntimeException exception) {
				// 예치금 서비스 장애·응답 시간 초과, 잠금 대기 시간 초과 등. 주문은 결제 대기로 남아 다음 실행에서 다시 시도한다
				// D가 오래 내려가 있으면 매분 주문마다 쌓이므로 스택 없이 한 줄로 남긴다
				retry++;
				log.warn("결제 기한이 지난 주문의 만료 처리에 실패해 다음에 다시 시도합니다. orderId={}, cause={}",
					orderId, exception.toString());
			}
		}
		if (released > 0 || expired > 0 || retry > 0) {
			log.info("결제 기한이 지난 건을 정리했습니다. 참가 신청 {}건, 주문 {}건, 다시 시도 {}건", released, expired, retry);
		}
		return new ExpiryResult(released, expired, retry);
	}

	/**
	 * 주문 → 참가 신청 순서로 잠근다. 결제(OrderPayUseCase)와 같은 순서라
	 * 기한 직전에 겹쳐도 서로 기다리다 멈추지 않는다
	 * 결제 취소는 주문을 잠근 채 부른다. 결제가 차감을 마치고 자리를 파는 사이에 결제 취소가 끼어들면
	 * 돈은 돌려줬는데 주문은 결제 완료로 남기 때문이다. 결제 중이면 잠금을 기다렸다가, 결제가 끝난 주문이면 건너뛴다
	 */
	private Expired expireLocked(long orderId, LocalDateTime now) {
		Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
		// 조회와 잠금 사이에 결제됐으면 건너뛴다
		if (order == null || !order.isOverdue(now)) {
			return new Expired(false, false);
		}
		walletPort.cancelPayment(order.getId(), order.getBuyerId());
		// 결제·판매 뒤 주문 저장이 롤백돼 판매된 채 남은 자리를 되돌린다. 판매되지 않은 자리면 B가 아무것도 하지 않는다
		// 결제하지 않은 자리(HELD)는 해제 메시지로 푼다 (Order.expire → OrderExpiredEventListener)
		orderOutboxKafkaAppendUseCase.appendSeatRestore(order.participationId());
		order.expire(now);
		boolean participationReleased = participationRepository.findByIdForUpdate(order.participationId())
			.map(participation -> participation.markReleased(now))
			.orElse(false);
		return new Expired(true, participationReleased);
	}

	private record Expired(boolean order, boolean participation) {
	}
}
