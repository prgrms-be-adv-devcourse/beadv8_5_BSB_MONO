package com.bukang.order.app;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.domain.Order;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.domain.ParticipationType;
import com.bukang.order.domain.QueueAdmission;
import com.bukang.order.domain.ShippingAddress;
import com.bukang.order.domain.exception.ApplicationPeriodException;
import com.bukang.order.domain.exception.OrderConflictException;
import com.bukang.order.domain.exception.OrderNotPayableException;
import com.bukang.order.domain.exception.ParticipationAccessDeniedException;
import com.bukang.order.domain.exception.ParticipationNotFoundException;
import com.bukang.order.domain.exception.ParticipationNotPayableException;
import com.bukang.order.domain.exception.QueueNotAdmittedException;
import com.bukang.order.domain.exception.RaceCourseNotFoundException;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.ParticipationRepository.CreateTarget;
import com.bukang.order.out.QueueAdmissionRepository;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.out.race.RaceCoursePort;
import com.bukang.order.out.seat.SeatHoldPort;
import com.bukang.order.out.seat.SeatHoldResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 바로 결제 예매 ([결제하기], 정책 3장·4장). 신청서 진입 때 저장한 입장 끝 시각이 남았는지 확인하고(PRO-191, PRO-249),
 * 상품 서비스(B)에 자리 1석을 확보받아 입장 끝 시각을 결제 기한으로 하는 주문을 만든다 (예매창 15분, 최종 정책 개요 3-1, 8장 2-1)
 * 확보하면 참가 신청 PENDING → APPLIED, 주문 CREATED. 매진이면 참가 신청 FAILED, 주문 없음 (ERD 추천안 v4 8·9쪽)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCreateUseCase {
	// 정책상 가장 긴 입장 시간 (예매창 15분, 최종 정책 개요 3-1, 8장 2-1). 대기열이 더 긴 끝 시각을 줘도 여기까지만 자리를 잡는다
	static final long MAX_ADMISSION_MINUTES = 15;

	// Order·OrderItem의 @UniqueConstraint 이름과 같아야 한다
	private static final List<String> ORDER_CONSTRAINTS = List.of("uk_order_idempotency",
		"uk_order_item_participation");

	private final ParticipationRepository participationRepository;
	private final OrderRepository orderRepository;
	private final RaceCoursePort raceCoursePort;
	private final SeatHoldPort seatHoldPort;
	private final QueueAdmissionRepository queueAdmissionRepository;
	private final TransactionTemplate transactionTemplate;

	/**
	 * 상품 HTTP(종목 조회·자리 확보)는 참가 신청을 잠그지 않은 채 부른다 (BUK-213). 순서:
	 * 1. 잠그지 않고 값만 읽어 거른다 (없는 신청·남의 신청·같은 키로 만든 주문·끝난 신청)
	 * 2. 상품에 종목을 묻는다 (취소된 대회, 접수 기간)
	 * 3. 잠그고 다시 확인한 뒤 「자리 잡으러 감」 표시(결제 기한 칸)를 남겨 커밋한다
	 * 4. 상품에 자리를 잡는다 (참가 신청 ID로 멱등: 다시 불러도 처음 결과)
	 * 5. 잠그고 결과를 반영해 주문을 만든다
	 * 4 뒤에 5가 실패하면(시간 초과·롤백) 자리는 잡혔는데 신청서는 확보 전으로 남는다. 같은 신청서로 다시 누르면 상품이 처음 결과를 주고,
	 * 그대로 두면 기한 뒤 만료 정리가 표시를 보고 자리를 풀어 준다 (OrderExpiryUseCase.releaseAbandonedHolds)
	 * 바깥 트랜잭션 없이 부른다. 바깥 트랜잭션 안에서 부르면 단계가 모두 그 트랜잭션에 묶여 HTTP 동안 잠금을 쥔다
	 */
	public OrderCreateResult create(long memberId, long participationId, String idempotencyKey, LocalDateTime now) {
		Precheck precheck = transactionTemplate.execute(status -> precheck(memberId, participationId, idempotencyKey,
			now));
		if (precheck.result() != null) {
			return precheck.result();
		}
		// 같은 키로 만든 주문이 있으면 상품을 묻지 않고 잠근 뒤 그 주문을 돌려준다
		if (precheck.existing()) {
			return transactionTemplate.execute(status -> {
				Participation participation = lock(participationId);
				return findExisting(participation, memberId, idempotencyKey).orElseThrow(
					() -> new IllegalStateException("같은 키로 만든 주문이 사라졌습니다. participationId=" + participationId));
			});
		}
		RaceCourseInfo course = raceCoursePort.findCourse(precheck.raceCourseId())
			.orElseThrow(() -> new RaceCourseNotFoundException("존재하지 않는 종목입니다."));
		ParticipationApplyUseCase.checkNotCanceled(course);
		// 파티원 신청서는 파티장이 신청할 때 크루 자리를 이미 잡았다. 자리 확보·대기열을 건너뛰고 주문만 만든다 (BUK-38)
		if (precheck.party()) {
			return transactionTemplate.execute(status -> createPartyOrder(memberId, participationId, idempotencyKey,
				course, now));
		}
		if (!course.isAccepting(now)) {
			throw new ApplicationPeriodException("접수 기간이 아닙니다.");
		}
		// 개인 예매는 모두 대기열을 거친다. 장바구니 결제도 항목마다 여기를 지나므로 대기열을 건너뛰지 못한다
		// 대기열에는 다시 묻지 않고 신청서 진입 때 저장한 입장 끝 시각을 쓴다 (10/17 회의 2번). 저장값이 없으면(주소 직접 입력) 막는다
		// 입장 끝 시각까지 상품 서비스에 자리를 잡아 달라고 보내고, 이 시각이 결제 기한이 된다
		LocalDateTime admissionEndsAt = precheck.admission()
			.filter(admission -> admission.isAdmittedAt(now))
			.map(QueueAdmission::getAdmissionEndsAt)
			.orElseThrow(() -> new QueueNotAdmittedException("대기열에서 입장한 뒤 결제할 수 있습니다."));
		LocalDateTime holdUntil = capAdmissionEndsAt(admissionEndsAt, now, memberId, course.raceCourseId());
		OrderCreateResult finished = transactionTemplate.execute(status -> {
			Participation participation = lock(participationId);
			Optional<OrderCreateResult> done = finishedResult(participation, memberId, idempotencyKey);
			if (done.isPresent()) {
				return done.get();
			}
			participation.markHoldRequested(holdUntil);
			return null;
		});
		if (finished != null) {
			return finished;
		}
		SeatHoldResult hold = seatHoldPort.hold(course.raceCourseId(), participationId, holdUntil);
		return transactionTemplate.execute(status -> applyHold(memberId, participationId, idempotencyKey, course,
			hold));
	}

	/**
	 * 1단계: 잠그지 않고 값만 읽는다. 오류 순서는 잠금 안에서 확인하던 때와 같다
	 * (없음 → 남의 신청 → 같은 키 주문 → 매진으로 끝난 신청 → 파티원·개인의 결제할 수 없는 상태)
	 */
	private Precheck precheck(long memberId, long participationId, String idempotencyKey, LocalDateTime now) {
		CreateTarget target = participationRepository.findCreateTarget(participationId)
			.orElseThrow(() -> new ParticipationNotFoundException("존재하지 않는 참가 신청입니다."));
		if (target.memberId() != memberId) {
			throw new ParticipationAccessDeniedException("본인의 참가 신청만 결제할 수 있습니다.");
		}
		if (orderRepository.existsByBuyerIdAndIdempotencyKey(memberId, idempotencyKey)) {
			return Precheck.existingOrder();
		}
		// 매진으로 이미 끝난 신청서로 다시 요청하면(같은 키 재시도 포함) 처음과 같은 매진 결과를 돌려준다
		if (target.status() == ParticipationStatus.FAILED) {
			return Precheck.finished(OrderCreateResult.soldOut());
		}
		if (target.participationType() == ParticipationType.PARTY) {
			checkPartyPayable(target.status(), target.paymentDueAt(), now);
			return Precheck.party(target.raceCourseId());
		}
		Participation.checkPayable(target.status());
		return Precheck.individual(target.raceCourseId(),
			queueAdmissionRepository.findByMemberIdAndRaceCourseId(memberId, target.raceCourseId()));
	}

	private record Precheck(OrderCreateResult result, boolean existing, boolean party, long raceCourseId,
		Optional<QueueAdmission> admission) {
		static Precheck existingOrder() {
			return new Precheck(null, true, false, 0, Optional.empty());
		}

		static Precheck finished(OrderCreateResult result) {
			return new Precheck(result, false, false, 0, Optional.empty());
		}

		static Precheck party(long raceCourseId) {
			return new Precheck(null, false, true, raceCourseId, Optional.empty());
		}

		static Precheck individual(long raceCourseId, Optional<QueueAdmission> admission) {
			return new Precheck(null, false, false, raceCourseId, admission);
		}
	}

	// 같은 신청서로 들어온 요청을 줄 세운다. 먼저 끝난 요청이 만든 주문을 뒤 요청이 찾는다
	private Participation lock(long participationId) {
		return participationRepository.findByIdForUpdate(participationId)
			.orElseThrow(() -> new ParticipationNotFoundException("존재하지 않는 참가 신청입니다."));
	}

	private Optional<OrderCreateResult> findExisting(Participation participation, long memberId,
		String idempotencyKey) {
		Optional<Order> existing = orderRepository.findByBuyerIdAndIdempotencyKey(memberId, idempotencyKey);
		if (existing.isEmpty()) {
			return Optional.empty();
		}
		// 같은 키를 다른 신청서에 다시 쓰면 처음 주문을 돌려주지 않는다 (엉뚱한 주문을 성공으로 알리지 않게)
		if (!existing.get().isFor(participation.getId())) {
			throw OrderConflictException.keyReused("이미 다른 주문에 쓴 Idempotency-Key입니다. 새 키로 다시 요청해주세요.");
		}
		return Optional.of(OrderCreateResult.existing(existing.get().toDto(applicationShippingAddress(participation))));
	}

	// 잠근 뒤: 잠그기 전 확인과 이 사이에 다른 요청이 끝냈으면 그 결과를 돌려준다. 결제할 수 없는 상태면 409
	private Optional<OrderCreateResult> finishedResult(Participation participation, long memberId,
		String idempotencyKey) {
		Optional<OrderCreateResult> existing = findExisting(participation, memberId, idempotencyKey);
		if (existing.isPresent()) {
			return existing;
		}
		if (participation.isHoldFailed()) {
			return Optional.of(OrderCreateResult.soldOut());
		}
		participation.checkPayable();
		return Optional.empty();
	}

	// 5단계: 상품의 확보 결과를 반영해 주문을 만든다
	private OrderCreateResult applyHold(long memberId, long participationId, String idempotencyKey,
		RaceCourseInfo course, SeatHoldResult hold) {
		Participation participation = lock(participationId);
		Optional<OrderCreateResult> done = finishedResult(participation, memberId, idempotencyKey);
		if (done.isPresent()) {
			return done.get();
		}
		if (!hold.held()) {
			participation.markHoldFailed();
			return OrderCreateResult.soldOut();
		}
		participation.markApplied(hold.expiresAt());
		Order order = Order.forParticipation(participation, course, idempotencyKey, hold.expiresAt());
		try {
			orderRepository.saveAndFlush(order);
		} catch (DataIntegrityViolationException exception) {
			// 줄 세우기를 거쳐도 남는 경합(다른 신청서에 같은 키 등)은 유니크 제약이 막는다
			if (isOrderConflict(exception)) {
				throw new OrderConflictException("같은 주문 요청을 처리하고 있습니다. 잠시 뒤 주문 내역을 확인해주세요.");
			}
			throw exception;
		}
		order.assignOrderNo();
		return OrderCreateResult.created(order.toDto(applicationShippingAddress(participation)));
	}

	/**
	 * 파티원 결제의 주문 만들기 (BUK-38, 11/02 결정 ⑥). 결제 기한은 파티 결제 기한(신청서에 저장)이다
	 * 크루원인지·대회 취소는 결제(OrderPayUseCase)에서 예치금을 빼기 전에 다시 본다
	 */
	private OrderCreateResult createPartyOrder(long memberId, long participationId, String idempotencyKey,
		RaceCourseInfo course, LocalDateTime now) {
		Participation participation = lock(participationId);
		Optional<OrderCreateResult> existing = findExisting(participation, memberId, idempotencyKey);
		if (existing.isPresent()) {
			return existing.get();
		}
		checkPartyPayable(participation.getStatus(), participation.getPaymentDueAt(), now);
		Order order = Order.forParticipation(participation, course, idempotencyKey, participation.getPaymentDueAt());
		try {
			orderRepository.saveAndFlush(order);
		} catch (DataIntegrityViolationException exception) {
			// 같은 신청서로 다른 키를 써서 다시 만들면 주문 상품 유니크 제약이 막는다
			if (isOrderConflict(exception)) {
				throw new OrderConflictException("이미 이 신청서로 만든 주문이 있습니다. 주문 내역에서 결제해주세요.");
			}
			throw exception;
		}
		order.assignOrderNo();
		return OrderCreateResult.created(order.toDto(applicationShippingAddress(participation)));
	}

	// 파티장이 아직 신청하지 않았으면(확보 전) 결제할 자리가 없다. 기한이 지나 끝난 신청서도 여기서 막힌다
	private static void checkPartyPayable(ParticipationStatus status, LocalDateTime paymentDueAt, LocalDateTime now) {
		if (status != ParticipationStatus.APPLIED) {
			throw new ParticipationNotPayableException(
				"파티장이 신청한 뒤 결제 기간에만 결제할 수 있습니다(현재 상태: %s).".formatted(status.getLabel()));
		}
		if (!now.isBefore(paymentDueAt)) {
			throw OrderNotPayableException.deadlinePassed("파티 결제 기한이 지났습니다.");
		}
	}

	// 결제 전이라 배송이 없을 때 주문 응답에 보여 줄 배송지 (Order.toDto)
	private static ShippingAddress applicationShippingAddress(Participation participation) {
		return participation.getParticipant().getShippingAddress();
	}

	// 대기열 데이터가 잘못돼 끝 시각이 너무 멀면, 매진 자리를 오래 묶지 않게 정책상 최대 입장 시간으로 자른다
	private LocalDateTime capAdmissionEndsAt(LocalDateTime admissionEndsAt, LocalDateTime now, long memberId,
		long raceCourseId) {
		LocalDateTime max = now.plusMinutes(MAX_ADMISSION_MINUTES);
		if (admissionEndsAt.isAfter(max)) {
			log.warn("대기열 입장 끝 시각이 정책상 최대 입장 시간보다 멀어 줄입니다. memberId={}, raceCourseId={}, admissionEndsAt={}",
				memberId, raceCourseId, admissionEndsAt);
			return max;
		}
		return admissionEndsAt;
	}

	private boolean isOrderConflict(DataIntegrityViolationException exception) {
		String message = exception.getMostSpecificCause().getMessage();
		if (message == null) {
			return false;
		}
		String lowerCaseMessage = message.toLowerCase();
		return ORDER_CONSTRAINTS.stream().anyMatch(lowerCaseMessage::contains);
	}
}
