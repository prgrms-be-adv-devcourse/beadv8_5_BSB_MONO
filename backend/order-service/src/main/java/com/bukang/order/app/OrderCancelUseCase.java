package com.bukang.order.app;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.domain.Order;
import com.bukang.order.domain.OrderItem;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.exception.ExternalServiceRejectedException;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.domain.exception.OrderAccessDeniedException;
import com.bukang.order.domain.exception.OrderNotFoundException;
import com.bukang.order.domain.exception.ParticipationNotFoundException;
import com.bukang.order.domain.exception.RaceCourseNotFoundException;
import com.bukang.order.domain.exception.RefundNotAllowedException;
import com.bukang.order.dto.OrderDto;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.payout.RefundEligibilityPort;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.out.race.RaceCoursePort;
import com.bukang.order.out.wallet.WalletPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제한 참가권을 주문 상품 단위로 취소·환불한다 (정책 5장). 키트는 같은 주문 상품이라 함께 취소된다
 * 환불 기한 안이거나, 대회 일정이 바뀐 뒤 7일 안이면 자동 승인 (ERD v4 환불 판정). 정산 대상일이 지난 참가권은 정산 서비스(E)가 거절한다
 * 판정 뒤 세 단계로 나눈다 (BUK-9). 예치금 환불 HTTP를 기다리는 동안 주문 잠금과 DB 커넥션을 쥐지 않는다
 * 1. 트랜잭션 1: 주문을 잠가 다시 확인하고 주문 상품을 취소 중(CANCELING) + 시작 시각으로 커밋한다
 * 2. 트랜잭션 밖: 예치금 환불(D). 주문 ID로 멱등이라 몇 번 불러도 돈은 한 번만 움직인다
 * 3. 트랜잭션 2 (completeCancel): 주문 상품·주문 취소, 참가 신청 취소, 자리 복구 메시지를 아웃박스에 저장
 *    (B에는 커밋 뒤 Kafka product.hold.restore로 간다, PRO-270). 환불이 확인된 취소만 자리를 되돌려 정원보다 더 팔리지 않는다
 * 환불 결과에 따라
 * - 성공: 트랜잭션 2로 마무리한다
 * - 거절(4xx, ExternalServiceRejectedException): 돈이 움직이지 않았으므로 결제 완료로 되돌리고(revertCancel) 예외를 그대로 던진다
 * - 모름(시간 초과·5xx, ExternalServiceUnavailableException): D가 이미 환불했을 수 있어 취소 중으로 두고 "취소 처리 중"으로 답한다.
 *   1분 뒤부터 OrderCancelRetryUseCase가 같은 주문 ID로 환불을 다시 불러 마무리한다
 * 트랜잭션은 이 클래스가 단계마다 연다. 바깥 트랜잭션 없이 부른다 (OrderFacade.cancelItem, OrderCancelRetryUseCase)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCancelUseCase {
	private final OrderRepository orderRepository;
	private final ParticipationRepository participationRepository;
	private final RaceCoursePort raceCoursePort;
	private final RefundEligibilityPort refundEligibilityPort;
	private final OrderOutboxKafkaAppendUseCase orderOutboxKafkaAppendUseCase;
	private final WalletPort walletPort;
	private final TransactionTemplate transactionTemplate;

	// refundPending: 환불 결과를 몰라 취소 중으로 남았다 (컨트롤러가 202 "취소 처리 중"으로 답한다)
	public record CancelResult(OrderDto order, long refundedAmount, boolean refundPending) {
	}

	/**
	 * 다른 서비스에 묻는 판정(종목 정보, 정산 대상일)은 잠금과 DB 커넥션을 쥐기 전에 하고,
	 * 그다음 주문을 잠가 상태를 다시 확인한 뒤 취소 중으로 바꾼다. 정산 서비스가 느려도 주문 행 잠금이 그동안 붙잡히지 않는다
	 * 판정과 잠금 사이에 같은 상품이 먼저 취소되면 잠근 뒤의 확인(checkCancelable)이 409로 막는다
	 */
	public CancelResult cancel(long memberId, long orderId, long orderItemId, LocalDateTime now) {
		CancelTarget target = transactionTemplate.execute(status -> readTarget(memberId, orderId, orderItemId));
		RaceCourseInfo course = raceCoursePort.findCourse(target.raceCourseId())
			.orElseThrow(() -> new RaceCourseNotFoundException("존재하지 않는 종목입니다."));
		if (!course.isRefundableOn(now.toLocalDate())) {
			throw new RefundNotAllowedException(refundPeriodPassedMessage(course));
		}
		if (!isBeforePayout(target.copiedRaceDate(), course.raceDate().toLocalDate())) {
			throw RefundNotAllowedException.payoutStarted("정산이 시작된 참가권이라 취소할 수 없습니다.");
		}
		transactionTemplate.executeWithoutResult(status -> startCancel(memberId, orderId, orderItemId, now));

		try {
			// 주문 하나 = 참가권 하나라, 주문 단위 전액 환불이 이 상품의 환불이다. 금액은 D가 결제 기록으로 정한다
			walletPort.refund(orderId, memberId);
		} catch (ExternalServiceRejectedException exception) {
			revertCancel(orderId, orderItemId);
			throw exception;
		} catch (ExternalServiceUnavailableException exception) {
			log.warn("예치금 환불 결과를 몰라 취소 중으로 둡니다. 재처리가 마무리합니다. orderId={}, orderItemId={}, cause={}",
				orderId, orderItemId, exception.getMessage());
			return pending(orderId);
		}
		try {
			return completeCancel(orderId, orderItemId, now);
		} catch (RuntimeException exception) {
			// 돈은 이미 돌려줬다. 실패로 답하면 회원이 다시 눌러도 409라, 취소 중으로 두고 재처리가 마무리하게 한다
			log.error("환불은 됐지만 취소를 마무리하지 못해 취소 중으로 둡니다. 재처리가 마무리합니다. orderId={}, orderItemId={}",
				orderId, orderItemId, exception);
			return pending(orderId);
		}
	}

	private CancelResult pending(long orderId) {
		OrderDto order = transactionTemplate.execute(status -> orderRepository.findById(orderId)
			.orElseThrow(() -> new OrderNotFoundException("존재하지 않는 주문입니다.")).toDto());
		return new CancelResult(order, 0, true);
	}

	// 판정에 필요한 값만 잠그지 않고 읽는다. 없는 주문·남의 주문·결제 전 상품은 다른 서비스를 부르기 전에 거른다
	private CancelTarget readTarget(long memberId, long orderId, long orderItemId) {
		Order order = findOwnedOrder(orderRepository.findById(orderId), memberId);
		OrderItem item = order.findItem(orderItemId);
		item.checkCancelable();
		return new CancelTarget(item.getRaceCourseId(), item.getRaceDate().toLocalDate());
	}

	// 주문을 잠가 다시 확인한다. 같은 상품 취소가 동시에 와도 한 요청만 취소 중으로 바꾸고, 나머지는 409로 막혀 환불을 부르지 않는다
	private void startCancel(long memberId, long orderId, long orderItemId, LocalDateTime now) {
		Order order = findOwnedOrder(orderRepository.findByIdForUpdate(orderId), memberId);
		OrderItem item = order.findItem(orderItemId);
		order.startCancelItem(item, now);
	}

	/**
	 * 환불이 확인된 취소 중을 마무리한다. 사용자 취소와 재처리(OrderCancelRetryUseCase)가 함께 쓴다
	 * 주문을 먼저 잠그고, 아직 취소 중일 때만 바꾼다. 둘이 겹쳐도 먼저 잠근 쪽만 취소하고 뒤쪽은 그대로 돌아간다
	 * 참가 신청도 잠근다. 참가자 정보 수정과 겹쳐도 CANCELED가 옛 상태로 덮이지 않는다 (잠금 순서: 주문 → 참가 신청)
	 */
	public CancelResult completeCancel(long orderId, long orderItemId, LocalDateTime now) {
		return transactionTemplate.execute(status -> {
			Order order = orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new OrderNotFoundException("존재하지 않는 주문입니다."));
			OrderItem item = order.findItem(orderItemId);
			if (!item.isCanceling()) {
				return new CancelResult(order.toDto(), item.getCanceledAmount(), false);
			}
			Participation participation = participationRepository.findByIdForUpdate(item.getParticipationId())
				.orElseThrow(() -> new ParticipationNotFoundException("존재하지 않는 참가 신청입니다."));
			order.cancelItem(item, now);
			participation.markCanceled(now);
			// 이 트랜잭션이 커밋돼야 보내진다 (롤백되면 메시지도 없다)
			orderOutboxKafkaAppendUseCase.appendSeatRestore(item.getParticipationId());
			return new CancelResult(order.toDto(), item.getAmount(), false);
		});
	}

	// 예치금이 환불을 거절했다(4xx). 아직 취소 중일 때만 결제 완료로 되돌린다. 되돌렸으면 true
	public boolean revertCancel(long orderId, long orderItemId) {
		return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
			Order order = orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new OrderNotFoundException("존재하지 않는 주문입니다."));
			OrderItem item = order.findItem(orderItemId);
			if (!item.isCanceling()) {
				return false;
			}
			order.revertCancelItem(item);
			return true;
		}));
	}

	private Order findOwnedOrder(Optional<Order> found, long memberId) {
		Order order = found.orElseThrow(() -> new OrderNotFoundException("존재하지 않는 주문입니다."));
		if (!order.isOwnedBy(memberId)) {
			throw new OrderAccessDeniedException("본인의 주문만 취소할 수 있습니다.");
		}
		return order;
	}

	// 잠그기 전에 읽어 둔 판정 재료. 주문 상품의 대회 날짜는 주문 때 복사한 값이라 잠근 뒤에도 바뀌지 않는다
	private record CancelTarget(long raceCourseId, LocalDate copiedRaceDate) {
	}

	/**
	 * 정산 대상일 전인지 두 날짜로 모두 묻는다. MVP는 회수가 없어, 정산에 들어간 참가권을 환불하면 판매자 지급과
	 * 구매자 환불이 둘 다 나간다 (payout PayoutTargetDate)
	 * - 주문 상품의 대회 일시: 정산이 판매 내역을 고를 때 쓰는 날짜다. 주문 때 복사해 일정이 바뀌어도 그대로라,
	 *   대회가 미뤄졌으면 이 옛 날짜로 이미 정산됐을 수 있다
	 * - 상품 서비스의 지금 대회 일시: 대회가 당겨졌으면 실제 대회가 이미 끝났을 수 있다
	 * 일정이 바뀔 때 주문 상품의 대회 일시도 함께 고치면 한 날짜로 줄일 수 있다 (pending-items 코드 14)
	 */
	private boolean isBeforePayout(LocalDate copiedRaceDate, LocalDate currentRaceDate) {
		if (!refundEligibilityPort.isRefundable(copiedRaceDate)) {
			return false;
		}
		return copiedRaceDate.equals(currentRaceDate) || refundEligibilityPort.isRefundable(currentRaceDate);
	}

	// 일정 변경 뒤 전액 환불 기간이 환불 기한보다 늦게 끝났을 때만 그 기간을 함께 알린다 (먼저 끝났으면 헷갈리기만 한다)
	private String refundPeriodPassedMessage(RaceCourseInfo course) {
		LocalDate scheduleChangeLastDay = course.scheduleChangeRefundLastDay();
		if (scheduleChangeLastDay == null || !scheduleChangeLastDay.isAfter(course.refundDeadline())) {
			return "환불 기한(%s)이 지나 취소할 수 없습니다.".formatted(course.refundDeadline());
		}
		return "환불 기한(%s)과 일정 변경 뒤 전액 환불 기간(%s까지)이 지나 취소할 수 없습니다."
			.formatted(course.refundDeadline(), scheduleChangeLastDay);
	}
}
