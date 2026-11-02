package com.bukang.order.app;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.bukang.order.domain.Order;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.QueueAdmission;
import com.bukang.order.domain.ShippingAddress;
import com.bukang.order.domain.exception.ApplicationPeriodException;
import com.bukang.order.domain.exception.OrderConflictException;
import com.bukang.order.domain.exception.ParticipationAccessDeniedException;
import com.bukang.order.domain.exception.ParticipationNotFoundException;
import com.bukang.order.domain.exception.QueueNotAdmittedException;
import com.bukang.order.domain.exception.RaceCourseNotFoundException;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationRepository;
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

	public OrderCreateResult create(long memberId, long participationId, String idempotencyKey, LocalDateTime now) {
		// 같은 신청서로 들어온 요청을 줄 세운다. 먼저 끝난 요청이 만든 주문을 뒤 요청이 아래에서 찾는다
		Participation participation = participationRepository.findByIdForUpdate(participationId)
			.orElseThrow(() -> new ParticipationNotFoundException("존재하지 않는 참가 신청입니다."));
		if (!participation.isOwnedBy(memberId)) {
			throw new ParticipationAccessDeniedException("본인의 참가 신청만 결제할 수 있습니다.");
		}
		Optional<Order> existing = orderRepository.findByBuyerIdAndIdempotencyKey(memberId, idempotencyKey);
		if (existing.isPresent()) {
			// 같은 키를 다른 신청서에 다시 쓰면 처음 주문을 돌려주지 않는다 (엉뚱한 주문을 성공으로 알리지 않게)
			if (!existing.get().isFor(participationId)) {
				throw OrderConflictException.keyReused("이미 다른 주문에 쓴 Idempotency-Key입니다. 새 키로 다시 요청해주세요.");
			}
			return OrderCreateResult.existing(existing.get().toDto(applicationShippingAddress(participation)));
		}

		// 매진으로 이미 끝난 신청서로 다시 요청하면(같은 키 재시도 포함) 처음과 같은 매진 결과를 돌려준다
		if (participation.isHoldFailed()) {
			return OrderCreateResult.soldOut();
		}
		participation.checkPayable();
		RaceCourseInfo course = raceCoursePort.findCourse(participation.getRaceCourseId())
			.orElseThrow(() -> new RaceCourseNotFoundException("존재하지 않는 종목입니다."));
		ParticipationApplyUseCase.checkNotCanceled(course);
		if (!course.isAccepting(now)) {
			throw new ApplicationPeriodException("접수 기간이 아닙니다.");
		}

		// 개인 예매는 모두 대기열을 거친다. 장바구니 결제도 항목마다 여기를 지나므로 대기열을 건너뛰지 못한다
		// 대기열에는 다시 묻지 않고 신청서 진입 때 저장한 입장 끝 시각을 쓴다 (10/17 회의 2번). 저장값이 없으면(주소 직접 입력) 막는다
		// 입장 끝 시각까지 상품 서비스에 자리를 잡아 달라고 보내고, 이 시각이 결제 기한이 된다
		LocalDateTime admissionEndsAt = queueAdmissionRepository.findByMemberIdAndRaceCourseId(memberId,
				course.raceCourseId())
			.filter(admission -> admission.isAdmittedAt(now))
			.map(QueueAdmission::getAdmissionEndsAt)
			.orElseThrow(() -> new QueueNotAdmittedException("대기열에서 입장한 뒤 결제할 수 있습니다."));
		SeatHoldResult hold = seatHoldPort.hold(course.raceCourseId(), participation.getId(),
			capAdmissionEndsAt(admissionEndsAt, now, memberId, course.raceCourseId()));
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
