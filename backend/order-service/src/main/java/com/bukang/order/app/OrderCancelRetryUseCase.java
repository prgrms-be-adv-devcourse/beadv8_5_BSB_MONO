package com.bukang.order.app;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.bukang.order.domain.exception.ExternalServiceRejectedException;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.dto.CancelingOrderItemDto;
import com.bukang.order.out.OrderItemRepository;
import com.bukang.order.out.wallet.WalletPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 환불 결과를 몰라 취소 중(CANCELING)으로 남은 사용자 취소를 마무리한다 (BUK-9, OrderCancelRetryScheduler가 1분마다 부른다)
 * 취소를 시작한 지 RETRY_AFTER ~ GIVE_UP_AFTER 된 것만, 오래된 것부터 BATCH_SIZE건씩 같은 주문 ID로 환불을 다시 부른다
 * 예치금 환불은 주문 ID로 멱등이라(이미 환불·결제 취소된 주문이면 그대로 성공) 다시 불러도 돈은 한 번만 움직인다
 * - 성공: 취소로 마무리한다 (OrderCancelUseCase.completeCancel, 자리 복구 아웃박스 포함)
 * - 거절(4xx): 돈이 움직이지 않았으므로 결제 완료로 되돌린다. 다시 부르지 않는다
 * - 모름(시간 초과·5xx): 취소 중으로 두고 다음 실행에서 다시 부른다. 시작한 지 WARN_AFTER가 넘었으면 warn을 남긴다.
 *   연달아 MAX_CONSECUTIVE_UNAVAILABLE번 모르면 예치금 장애로 보고 이번 실행을 멈춘다 (건마다 2초씩 기다리지 않게)
 * GIVE_UP_AFTER가 지난 취소 중은 부르지 않고 건수만 error로 남긴다(건수가 바뀐 회차에만). 사람이 예치금 기록과 맞춰 마무리하거나 되돌린다
 * 환불 기한은 다시 보지 않는다. 취소를 시작할 때 이미 판정했고, 그 뒤에 기한이 지나도 회원이 요청한 취소다
 * 1분이 지나기 전의 취소 중은 사용자 취소가 아직 환불을 기다리는 중일 수 있어 건드리지 않는다
 * 마무리·되돌리기는 주문을 잠그고 아직 취소 중일 때만 바꾸므로, 사용자 취소와 겹쳐도 한 번만 처리된다
 * 트랜잭션은 열지 않는다. 환불 HTTP 동안 DB 커넥션을 쥐지 않고, 마무리·되돌리기가 건마다 트랜잭션을 연다
 * 앱을 여러 대 띄우면 같은 행을 함께 부를 수 있지만, 환불이 멱등이고 마무리가 잠근 뒤 확인하므로 결과는 같다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCancelRetryUseCase {
	// 취소를 시작한 뒤 이만큼 지나야 다시 부른다. 그 전에는 사용자 취소가 아직 환불을 기다리는 중일 수 있다
	static final Duration RETRY_AFTER = Duration.ofMinutes(1);
	// 시작한 뒤 이만큼 지나도 환불 결과를 모르면 warn을 남긴다
	static final Duration WARN_AFTER = Duration.ofMinutes(5);
	// 시작한 뒤 이만큼 지나면 더 부르지 않고 사람이 확인한다
	static final Duration GIVE_UP_AFTER = Duration.ofMinutes(30);
	// 한 번에 읽을 최대 건수
	static final int BATCH_SIZE = 100;
	// 연달아 이만큼 환불 결과를 모르면 이번 실행을 멈춘다
	static final int MAX_CONSECUTIVE_UNAVAILABLE = 3;

	// 직전 회차의 30분 지난 취소 중 건수. 스케줄러가 한 쓰레드에서 차례로 부르지만, 테스트가 여러 쓰레드에서 부르므로 volatile
	private volatile long lastGivenUp;

	private final OrderItemRepository orderItemRepository;
	private final OrderCancelUseCase orderCancelUseCase;
	private final WalletPort walletPort;

	// completed: 취소로 마무리, reverted: 결제 완료로 되돌림, pending: 아직 모름, givenUp: 30분이 지나 부르지 않은 건수
	public record RetryResult(int completed, int reverted, int pending, long givenUp) {
	}

	public RetryResult retry(LocalDateTime now) {
		long givenUp = orderItemRepository.countCancelingBefore(now.minus(GIVE_UP_AFTER));
		// 건수가 바뀐 회차에만 남긴다. 매분 같은 오류가 반복되면 새 장애 로그가 묻힌다
		long previousGivenUp = lastGivenUp;
		lastGivenUp = givenUp;
		if (givenUp > 0 && givenUp != previousGivenUp) {
			log.error("취소를 시작한 지 {}분이 지나도 환불 결과를 모르는 취소 중이 {}건 있습니다. 재처리가 더 부르지 않으니 "
				+ "예치금 환불 기록과 맞춰 마무리하거나 결제 완료로 되돌려야 합니다.", GIVE_UP_AFTER.toMinutes(), givenUp);
		}
		List<CancelingOrderItemDto> targets = orderItemRepository.findCancelingBetween(now.minus(RETRY_AFTER),
			now.minus(GIVE_UP_AFTER), PageRequest.of(0, BATCH_SIZE));

		int completed = 0;
		int reverted = 0;
		int pending = 0;
		int consecutiveUnavailable = 0;
		for (CancelingOrderItemDto target : targets) {
			Outcome outcome = retryOne(target, now);
			consecutiveUnavailable = outcome == Outcome.UNKNOWN ? consecutiveUnavailable + 1 : 0;
			switch (outcome) {
				case COMPLETED -> completed++;
				case REVERTED -> reverted++;
				case UNKNOWN -> pending++;
				default -> {
				}
			}
			if (consecutiveUnavailable >= MAX_CONSECUTIVE_UNAVAILABLE) {
				log.warn("예치금 환불 결과를 연달아 {}번 받지 못해 이번 취소 재처리를 멈춥니다. 남은 건은 다음 실행에서 부릅니다.",
					MAX_CONSECUTIVE_UNAVAILABLE);
				break;
			}
		}
		return new RetryResult(completed, reverted, pending, givenUp);
	}

	// 한 건의 결과. SKIPPED: 다른 쪽이 먼저 끝냈거나, 예상 못 한 오류라 다음 실행에서 다시 본다
	private enum Outcome {
		COMPLETED, REVERTED, UNKNOWN, SKIPPED
	}

	// 한 건을 다시 환불하고 마무리·되돌린다. 예상 못 한 오류는 이 건만 남기고 다음 건으로 넘어간다
	// (한 건이 늘 실패해도 맨 앞에 있어 뒤의 취소 중이 마무리되지 못하는 일을 막는다)
	private Outcome retryOne(CancelingOrderItemDto target, LocalDateTime now) {
		try {
			walletPort.refund(target.orderId(), target.buyerId());
		} catch (ExternalServiceRejectedException exception) {
			return revert(target);
		} catch (ExternalServiceUnavailableException exception) {
			warnIfLong(target, now);
			return Outcome.UNKNOWN;
		} catch (RuntimeException exception) {
			log.error("취소 재처리 중 환불 요청에서 예상 못 한 오류가 났습니다. orderId={}, orderItemId={}",
				target.orderId(), target.orderItemId(), exception);
			return Outcome.SKIPPED;
		}
		try {
			orderCancelUseCase.completeCancel(target.orderId(), target.orderItemId(), now);
			return Outcome.COMPLETED;
		} catch (RuntimeException exception) {
			log.error("환불은 확인했지만 취소를 마무리하지 못했습니다. 다음 실행에서 다시 마무리합니다. orderId={}, orderItemId={}",
				target.orderId(), target.orderItemId(), exception);
			return Outcome.SKIPPED;
		}
	}

	private Outcome revert(CancelingOrderItemDto target) {
		try {
			if (!orderCancelUseCase.revertCancel(target.orderId(), target.orderItemId())) {
				return Outcome.SKIPPED;
			}
		} catch (RuntimeException exception) {
			log.error("예치금이 환불을 거절했지만 결제 완료로 되돌리지 못했습니다. 다음 실행에서 다시 봅니다. orderId={}, orderItemId={}",
				target.orderId(), target.orderItemId(), exception);
			return Outcome.SKIPPED;
		}
		// 취소 중인 동안 대회가 취소됐으면 일괄 환불이 이 상품을 건너뛰었다. 되돌린 뒤에는 그 환불이 빠진다
		log.warn("예치금이 환불을 거절해 취소 중을 결제 완료로 되돌렸습니다. 대회가 취소됐으면 환불을 따로 확인해야 합니다. "
			+ "orderId={}, orderItemId={}", target.orderId(), target.orderItemId());
		return Outcome.REVERTED;
	}

	private void warnIfLong(CancelingOrderItemDto target, LocalDateTime now) {
		if (!target.cancelRequestedAt().plus(WARN_AFTER).isAfter(now)) {
			log.warn("취소를 시작한 지 {}분이 넘었는데 예치금 환불 결과를 모릅니다. orderId={}, orderItemId={}, cancelRequestedAt={}",
				WARN_AFTER.toMinutes(), target.orderId(), target.orderItemId(), target.cancelRequestedAt());
		}
	}
}
