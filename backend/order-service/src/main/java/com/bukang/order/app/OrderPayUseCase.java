package com.bukang.order.app;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.domain.Order;
import com.bukang.order.domain.OrderStatus;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.domain.Party;
import com.bukang.order.domain.exception.ExternalServiceRejectedException;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.domain.exception.InsufficientBalanceException;
import com.bukang.order.domain.exception.OrderAccessDeniedException;
import com.bukang.order.domain.exception.OrderNotFoundException;
import com.bukang.order.domain.exception.OrderNotPayableException;
import com.bukang.order.domain.exception.ParticipationNotFoundException;
import com.bukang.order.domain.exception.PartyConflictException;
import com.bukang.order.domain.exception.PartyNotFoundException;
import com.bukang.order.domain.exception.RaceCourseNotFoundException;
import com.bukang.order.out.OrderItemRepository;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.PartyRepository;
import com.bukang.order.out.crew.CrewPort;
import com.bukang.order.out.crew.CrewPort.CrewMembership;
import com.bukang.order.out.kit.KitOptionPort;
import com.bukang.order.out.kit.KitOptionPort.KitOption;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.out.race.RaceCoursePort;
import com.bukang.order.out.seat.SeatHoldPort;
import com.bukang.order.out.seat.SeatSellResult;
import com.bukang.order.out.wallet.WalletPayResult;
import com.bukang.order.out.wallet.WalletPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 임시 확보한 주문을 예치금으로 결제하고 참가를 확정한다 (정책 4장 결제, ERD 추천안 v4 9쪽 결제 순서)
 * 순서: 키트 옵션 판매 수 +1 → 예치금 차감(D) → 자리 판매(B, 기한 안일 때만) → 주문 PAID·참가 신청 CONFIRMED·고른 옵션 사본
 * 기한이 지나 판매가 거절되면 차감한 예치금을 돌려준다. 중복 차감 방지·동시성은 D가 주문 ID로 맡는다
 * 결제 나누기(BUK-232, order.pay.split.enabled=true): 판매 수 +1·옵션 사본·주문 결제 중(PAYING)을 짧은 트랜잭션으로 커밋해
 * 판매 수 줄 잠금을 풀고, 예치금·자리 판매는 잠금 밖에서 부른 뒤 주문을 다시 잠가 확정한다. 인기 옵션 한 줄에 결제가 몰려도
 * 예치금·상품 HTTP 동안 줄을 잡지 않는다. 결과를 모르면 결제 중으로 남기고 결제 재확인 배치(recheck)가 같은 순서로 끝낸다
 * 스위치가 꺼져 있으면 옛 흐름(한 트랜잭션)으로 결제한다. 재확인 배치·결제 중 만료는 스위치와 상관없이 돈다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPayUseCase {
	static final String KIT_OPTIONS_UNAVAILABLE_MESSAGE = "상품 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.";
	static final String STATE_CHANGED_MESSAGE = "결제 상태가 바뀌었습니다. 다시 시도해주세요.";
	// 결제 중 한 건에 예치금·자리 판매를 이만큼 넘게 부르면 알림 로그를 남긴다 (추천값(미확정)). 부르는 것은 멈추지 않는다
	static final int PAY_ATTEMPT_ALERT = 10;

	private final OrderRepository orderRepository;
	private final ParticipationRepository participationRepository;
	private final WalletPort walletPort;
	private final SeatHoldPort seatHoldPort;
	private final RaceCoursePort raceCoursePort;
	private final PartyRepository partyRepository;
	private final CrewPort crewPort;
	private final KitOptionSaleUseCase kitOptionSaleUseCase;
	private final OrderItemRepository orderItemRepository;
	private final TransactionTemplate transactionTemplate;
	private final KitOptionPort kitOptionPort;
	private final OrderQueryUseCase orderQueryUseCase;
	private final OrderOutboxKafkaAppendUseCase orderOutboxKafkaAppendUseCase;

	// 결제 나누기 스위치 (BUK-232). 프론트 「결제 확인 중」 화면과 예치금 재결제 계약이 맞춰질 때까지 꺼 둔다
	@Value("${order.pay.split.enabled:false}")
	private boolean splitEnabled;

	/**
	 * 바깥 트랜잭션 없이 부른다. 상품 HTTP(종목·키트 옵션)·회원 HTTP(크루원 확인)와 판매 수 줄 만들기는 잠그기 전에 끝낸다
	 * 이미 결제 중인 주문이면 아무것도 부르지 않고 지금 주문을 돌려준다(202, 스위치와 상관없이)
	 */
	public OrderPayResult pay(long memberId, long orderId, List<Long> kitOptionIds, LocalDateTime now) {
		Prepared prepared = prepare(memberId, orderId, kitOptionIds, now);
		if (prepared == Prepared.PAYING) {
			return pending(memberId, orderId);
		}
		if (!splitEnabled) {
			OrderPayResult result = transactionTemplate.execute(status -> payLocked(memberId, orderId, prepared, now));
			return result != null ? result : pending(memberId, orderId);
		}
		Paying paying = transactionTemplate.execute(status -> startPaying(memberId, orderId, prepared, now));
		if (paying.done() != null) {
			return paying.done();
		}
		if (paying.alreadyPaying()) {
			return pending(memberId, orderId);
		}
		OrderPayResult result = drive(paying, now);
		return result != null ? result : pending(memberId, orderId);
	}

	/**
	 * 결제 중으로 오래 남은 주문을 다시 맞춘다 (결제 재확인 배치, BUK-232). 예치금 결제·자리 판매가 멱등이라 요청과 같은 순서로 다시 부른다
	 * 결과를 또 모르면 결제 중으로 남고 다음 실행에서 다시 본다. 잔액 부족·자리 기한 지남처럼 확실히 실패하면 되돌리고 예외를 던진다
	 * 바깥 트랜잭션 없이 부른다 (OrderPayRecheckScheduler)
	 * @return 결제 완료로 끝냈으면 true
	 */
	public boolean recheck(long orderId, LocalDateTime now) {
		Paying paying = transactionTemplate.execute(status -> {
			Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
			if (order == null || !order.isPaying()) {
				return null;
			}
			int attempts = order.countPayAttempt();
			if (attempts > PAY_ATTEMPT_ALERT) {
				// 예치금이나 상품이 오래 답하지 않는다. 기한 + 여유가 지나면 만료 정리가 끝내지만 사람이 원인을 본다
				log.error("결제 중 주문을 {}번 다시 확인했지만 끝나지 않았습니다. orderId={}, 결제 시작={}",
					attempts, orderId, order.getPayStartedAt());
			}
			return Paying.of(order);
		});
		return paying != null && drive(paying, now) != null;
	}

	/**
	 * 결제 중이 된 주문(paying)의 예치금 결제 → 자리 판매 → 확정을 잠금 밖에서 한다. 요청과 결제 재확인 배치가 함께 쓴다
	 * 결과를 모르면(예치금 시간 초과·5xx, 자리 판매 연결 실패·5xx·시간 초과) null을 돌려주고 결제 중으로 둔다
	 * 확실히 실패하면 판매 수·옵션·결제 중을 되돌리고 예외를 던진다:
	 * 예치금 거절(잔액 부족, 4xx) → 결제 취소는 부르지 않는다(차감되지 않았다)
	 * 자리 기한 지남 → 결제 취소 뒤 되돌린다. 주문은 결제 대기로 돌아가 만료 정리가 기존처럼 끝낸다
	 * (예치금 결제 취소는 같은 주문의 결제를 영구히 막으므로, 결과를 모를 때는 부르지 않는다)
	 */
	private OrderPayResult drive(Paying paying, LocalDateTime now) {
		long orderId = paying.orderId();
		WalletPayResult payment;
		try {
			payment = walletPort.pay(orderId, paying.memberId(), paying.amount());
		} catch (OrderNotPayableException | ExternalServiceRejectedException exception) {
			revert(orderId);
			throw exception;
		} catch (RuntimeException exception) {
			log.warn("예치금 결제 결과를 몰라 결제 중으로 둡니다. orderId={}, cause={}", orderId, exception.toString());
			return null;
		}
		if (payment.outcome() == WalletPayResult.Outcome.INSUFFICIENT_BALANCE) {
			// 주문과 자리는 그대로 둔다. 기한 안에 충전하고 같은 주문으로 다시 결제한다 (충전은 D PRO-81·82)
			revert(orderId);
			throw new InsufficientBalanceException(payment.shortage());
		}
		SeatSellResult sold;
		try {
			sold = seatHoldPort.sell(paying.participationId());
		} catch (RuntimeException exception) {
			// 판매했는지 모른다. 판매는 멱등이라 재확인 배치가 판매만 다시 부른다(예치금 결제는 이미 된 주문이라 다시 빼지 않는다)
			log.warn("자리 판매 결과를 몰라 결제 중으로 둡니다. orderId={}, cause={}", orderId, exception.toString());
			return null;
		}
		if (sold == SeatSellResult.EXPIRED) {
			try {
				walletPort.cancelPayment(orderId, paying.memberId());
			} catch (RuntimeException exception) {
				// 돈을 돌려받지 못했다. 결제 중으로 두면 재확인 배치가 다시 판매(기한 지남) → 결제 취소를 부른다
				log.warn("자리 기한이 지나 결제를 취소하려 했지만 실패해 결제 중으로 둡니다. orderId={}, cause={}",
					orderId, exception.toString());
				return null;
			}
			revert(orderId);
			throw OrderNotPayableException.deadlinePassed("결제 기한이 지나 결제하지 못했습니다. 빠져나간 예치금은 돌려드렸습니다.");
		}
		Confirmed confirmed = transactionTemplate.execute(status -> confirm(paying, now));
		if (confirmed.rejectMessage() != null) {
			throw new OrderNotPayableException(confirmed.rejectMessage());
		}
		return confirmed.result();
	}

	/**
	 * 주문을 다시 잠가 결제 중이면 결제 완료로 확정한다. 요청과 재확인 배치가 함께 확정하면 뒤쪽은 이미 결제한 것으로 돌려준다
	 * 그사이 만료 정리가 먼저 끝냈으면(결제 중 아님) 결제 취소에 더해 자리 복구를 같은 트랜잭션의 아웃박스에 남긴다:
	 * 만료가 남긴 복구가 이 판매보다 먼저 상품에 닿았으면 자리가 팔린 채 남기 때문이다
	 * 거절은 예외 대신 rejectMessage로 돌려준다(예외로 롤백하면 아웃박스도 지워진다)
	 */
	private Confirmed confirm(Paying paying, LocalDateTime now) {
		Order order = orderRepository.findByIdForUpdate(paying.orderId())
			.orElseThrow(() -> new OrderNotFoundException("존재하지 않는 주문입니다."));
		if (order.isPaid()) {
			return new Confirmed(OrderPayResult.alreadyPaid(order.toDto()), null);
		}
		if (!order.isPaying()) {
			log.error("결제가 확정되기 전에 주문이 끝났습니다. 결제를 취소하고 자리 복구를 남깁니다. orderId={}, 상태={}",
				order.getId(), order.getStatus());
			undoLate(order);
			return new Confirmed(null, STATE_CHANGED_MESSAGE);
		}
		Participation participation = participationRepository.findByIdForUpdate(order.participationId())
			.orElseThrow(() -> new ParticipationNotFoundException("존재하지 않는 참가 신청입니다."));
		if (participation.getStatus() != ParticipationStatus.APPLIED) {
			// 결제 중에는 참가 신청이 바뀌지 않아야 한다(신청서 취소·파티 마감이 결제 대기를 건너뜀). 사람이 원인을 본다
			log.error("결제 중인 주문의 참가 신청이 결제 대기가 아닙니다. 결제를 취소하고 되돌립니다. orderId={}, 신청 상태={}",
				order.getId(), participation.getStatus());
			undoLate(order);
			kitOptionSaleUseCase.unreserve(order.participationId());
			order.revertPaying();
			logPayingTime(order, now, "되돌림");
			return new Confirmed(null, STATE_CHANGED_MESSAGE);
		}
		// 키트가 있으면 이 때 참가 신청의 배송지로 발송 대기 배송을 만든다 (BUK-6)
		order.markPaid(now, participation.getParticipant().getShippingAddress());
		participation.markConfirmed(now);
		logPayingTime(order, now, "확정");
		return new Confirmed(OrderPayResult.paid(order.toDto()), null);
	}

	// 늦게 닿은 판매를 무른다. 결제 취소가 실패해도 자리 복구는 남긴다: 만료 정리가 이미 결제를 취소했다(D가 막아 둠)
	private void undoLate(Order order) {
		try {
			walletPort.cancelPayment(order.getId(), order.getBuyerId());
		} catch (RuntimeException exception) {
			log.error("늦게 닿은 결제를 취소하지 못했습니다. 예치금 기록과 맞춰 봐야 합니다. orderId={}, cause={}",
				order.getId(), exception.toString());
		}
		orderOutboxKafkaAppendUseCase.appendSeatRestore(order.participationId());
	}

	// 판매 수 -1, 옵션 사본 지우기, 결제 중 → 결제 대기. 이미 확정·만료된 주문은 그대로 둔다
	private void revert(long orderId) {
		transactionTemplate.executeWithoutResult(status -> orderRepository.findByIdForUpdate(orderId)
			.filter(Order::isPaying)
			.ifPresent(order -> {
				kitOptionSaleUseCase.unreserve(order.participationId());
				order.revertPaying();
				logPayingTime(order, LocalDateTime.now(), "되돌림");
			}));
	}

	// 결제 중에 머문 시간 (지표). 재확인 배치까지 간 결제만 info로 남긴다
	// shortcut: order-service에 Micrometer가 없어 로그로 남긴다. actuator를 넣으면 Timer로 바꾼다
	private static void logPayingTime(Order order, LocalDateTime now, String result) {
		if (order.getPayStartedAt() == null) {
			return;
		}
		long millis = Duration.between(order.getPayStartedAt(), now).toMillis();
		if (order.getPayAttempts() > 1) {
			log.info("결제 중 {}. orderId={}, 결제 중에 머문 시간={}ms, 시도={}", result, order.getId(), millis,
				order.getPayAttempts());
		} else {
			log.debug("결제 중 {}. orderId={}, 결제 중에 머문 시간={}ms", result, order.getId(), millis);
		}
	}

	// 결제 중 주문의 지금 상태 (202). 주문 조회와 같은 모양으로 돌려준다
	private OrderPayResult pending(long memberId, long orderId) {
		return OrderPayResult.pending(transactionTemplate.execute(
			status -> orderQueryUseCase.findOwned(memberId, orderId)));
	}

	/**
	 * 결제 나누기 트랜잭션 1: 주문 → 참가 신청 → 판매 수 줄(옵션 ID 오름차순) 잠금 → 확인 → 판매 수 +1·옵션 사본 → 결제 중. 커밋하면 잠금이 풀린다
	 */
	private Paying startPaying(long memberId, long orderId, Prepared prepared, LocalDateTime now) {
		Order order = lockPayable(memberId, orderId);
		if (order.isPaid()) {
			return Paying.done(OrderPayResult.alreadyPaid(order.toDto()));
		}
		if (order.isPaying()) {
			return Paying.ALREADY_PAYING;
		}
		Participation participation = checkLocked(order, prepared, now);
		kitOptionSaleUseCase.reserve(prepared.kitOptions());
		kitOptionSaleUseCase.saveChoices(participation.getId(), prepared.kitOptions());
		order.startPaying(now);
		return Paying.of(order);
	}

	// 결제 중이 된 주문 하나. done은 이미 결제한 주문의 응답, alreadyPaying은 다른 요청이 먼저 결제 중으로 만든 것
	private record Paying(OrderPayResult done, boolean alreadyPaying, long orderId, long memberId, long amount,
		long participationId) {
		static final Paying ALREADY_PAYING = new Paying(null, true, 0, 0, 0, 0);

		static Paying done(OrderPayResult result) {
			return new Paying(result, false, 0, 0, 0, 0);
		}

		static Paying of(Order order) {
			return new Paying(null, false, order.getId(), order.getBuyerId(), order.getTotalAmount(),
				order.participationId());
		}
	}

	private record Confirmed(OrderPayResult result, String rejectMessage) {
	}

	/**
	 * 잠그기 전 확인. 엔티티를 읽지 않고 값만 읽는다(잠근 뒤 옛 상태가 돌아오지 않게). 이미 결제한 주문이면 아무것도 하지 않는다
	 * 결제 대기가 아니거나 기한이 지났으면 상품을 묻기 전에 409로 끝낸다(빈 판매 수 줄도 만들지 않는다)
	 * 종목은 파티원 결제만 묻는다(대회 취소·크루원 확인). 개인 결제는 주문 상품의 대회 ID로 옵션만 묻는다
	 */
	private Prepared prepare(long memberId, long orderId, List<Long> kitOptionIds, LocalDateTime now) {
		OrderItemRepository.PayTarget target = orderItemRepository.findPayTargets(orderId).stream().findFirst()
			.orElseThrow(() -> new OrderNotFoundException("존재하지 않는 주문입니다."));
		if (target.getBuyerId() != memberId) {
			throw new OrderAccessDeniedException("본인의 주문만 결제할 수 있습니다.");
		}
		if (target.getStatus() == OrderStatus.PAID) {
			return null;
		}
		if (target.getStatus() == OrderStatus.PAYING) {
			return Prepared.PAYING;
		}
		Order.checkPayable(target.getStatus(), target.getPaymentDueAt(), now);
		RaceCourseInfo course = null;
		if (target.getPartyId() != null) {
			course = findCourse(target.getRaceCourseId());
			// 취소된 대회는 옵션을 묻지 않고 409 (상품은 취소된 대회의 옵션 목록에 404를 준다)
			ParticipationApplyUseCase.checkNotCanceled(course);
			// 크루원 확인(회원 HTTP)도 잠그기 전에 한다 (BUK-232, pending 48)
			checkPartyPayable(target.getPartyId(), memberId);
		}
		RaceCourseInfo knownCourse = course;
		List<KitOption> raceOptions = kitOptionPort.findKitOptions(target.getRaceId())
			.orElseGet(() -> raceNotFound(target, knownCourse));
		return new Prepared(course, kitOptionSaleUseCase.choose(target.getRaceId(), raceOptions, kitOptionIds));
	}

	/**
	 * 상품이 옵션 목록에 404를 줬다. 종목이 있는 대회라 취소된 대회(409)이거나 경로·주소가 어긋난 것(503)이다
	 * 옵션 없음으로 넘기면 옵션을 고르지 않은 결제가 통과하므로 결제를 막는다
	 */
	private List<KitOption> raceNotFound(OrderItemRepository.PayTarget target, RaceCourseInfo knownCourse) {
		RaceCourseInfo course = knownCourse != null ? knownCourse : findCourse(target.getRaceCourseId());
		ParticipationApplyUseCase.checkNotCanceled(course);
		log.error("상품 서비스가 취소되지 않은 대회의 키트 옵션 목록에 404를 줬습니다. 경로·주소 계약을 확인해야 합니다. raceId={}",
			target.getRaceId());
		throw new ExternalServiceUnavailableException(KIT_OPTIONS_UNAVAILABLE_MESSAGE);
	}

	private RaceCourseInfo findCourse(long raceCourseId) {
		return raceCoursePort.findCourse(raceCourseId)
			.orElseThrow(() -> new RaceCourseNotFoundException("존재하지 않는 종목입니다."));
	}

	// 잠그기 전에 확인한 값. PAYING은 이미 결제 중인 주문이라는 표시다
	private record Prepared(RaceCourseInfo course, List<KitOption> kitOptions) {
		static final Prepared PAYING = new Prepared(null, List.of());
	}

	// 주문을 먼저 잠근다. 같은 주문의 결제 요청과 만료 정리 배치가 주문 → 참가 신청 순서로 줄을 선다
	private Order lockPayable(long memberId, long orderId) {
		Order order = orderRepository.findByIdForUpdate(orderId)
			.orElseThrow(() -> new OrderNotFoundException("존재하지 않는 주문입니다."));
		if (!order.isOwnedBy(memberId)) {
			throw new OrderAccessDeniedException("본인의 주문만 결제할 수 있습니다.");
		}
		return order;
	}

	// 잠근 주문의 결제 가능 확인과 참가 신청 잠금. 차감 전에 끝낸다(차감 뒤에 실패하면 돈만 빠지고 주문은 CREATED로 남는다)
	// 참가 신청도 잠근다. 참가자 정보 수정이 옛 상태로 덮어써 CONFIRMED가 사라지지 않게 한다
	private Participation checkLocked(Order order, Prepared prepared, LocalDateTime now) {
		order.checkPayable(now);
		Participation participation = participationRepository.findByIdForUpdate(order.participationId())
			.orElseThrow(() -> new ParticipationNotFoundException("존재하지 않는 참가 신청입니다."));
		participation.checkConfirmable();
		if (prepared == null) {
			// 잠그기 전에는 결제 완료였는데 지금은 아니다(일어나지 않아야 함). 확인 없이 진행하지 않는다
			throw new OrderNotPayableException(STATE_CHANGED_MESSAGE);
		}
		return participation;
	}

	// 옛 결제 흐름(스위치 꺼짐): 판매 수 +1부터 확정까지 한 트랜잭션. 결제 중인 주문을 만나면 null(202로 지금 주문을 준다)
	private OrderPayResult payLocked(long memberId, long orderId, Prepared prepared, LocalDateTime now) {
		Order order = lockPayable(memberId, orderId);
		// 앞 요청이 이미 결제했으면 다시 차감하지 않고 그대로 돌려준다 (같은 결제 요청 두 번)
		if (order.isPaid()) {
			return OrderPayResult.alreadyPaid(order.toDto());
		}
		if (order.isPaying()) {
			return null;
		}
		Participation participation = checkLocked(order, prepared, now);
		// 다른 확인을 모두 끝낸 뒤, 예치금 바로 전에 판매 수를 센다. 늘린 판매 수 줄은 이 트랜잭션이 끝날 때까지 잠겨
		// 같은 옵션의 다른 결제가 기다리므로(예치금·자리 판매 HTTP 동안), 잠그는 시간을 줄이려고 맨 뒤에 둔다
		kitOptionSaleUseCase.reserve(prepared.kitOptions());

		WalletPayResult payment = walletPort.pay(order.getId(), memberId, order.getTotalAmount());
		if (payment.outcome() == WalletPayResult.Outcome.INSUFFICIENT_BALANCE) {
			// 주문과 자리는 그대로 둔다. 기한 안에 충전하고 같은 주문으로 다시 결제한다 (충전은 D PRO-81·82)
			throw new InsufficientBalanceException(payment.shortage());
		}

		if (seatHoldPort.sell(order.participationId()) == SeatSellResult.EXPIRED) {
			// 차감은 됐는데 자리가 기한을 넘겼다. 돈을 돌려주고 결제를 거절한다. 주문은 만료 정리가 EXPIRED로 끝낸다
			walletPort.cancelPayment(order.getId(), memberId);
			throw OrderNotPayableException.deadlinePassed("결제 기한이 지나 결제하지 못했습니다. 빠져나간 예치금은 돌려드렸습니다.");
		}

		// 키트가 있으면 이 때 참가 신청의 배송지로 발송 대기 배송을 만든다 (BUK-6)
		order.markPaid(now, participation.getParticipant().getShippingAddress());
		participation.markConfirmed(now);
		kitOptionSaleUseCase.saveChoices(participation.getId(), prepared.kitOptions());
		return OrderPayResult.paid(order.toDto());
	}

	/**
	 * 파티원 결제 전 확인 (BUK-38, 11/02 결정 ⑥). 예치금을 빼기 전에 끝낸다
	 * 결제 기한은 위 order.checkPayable이 본다(주문 기한 = 파티 결제 기한). 결제 진행 중 대회가 취소되면 결제할 수 없고,
	 * 크루를 떠난 파티원은 결제할 수 없다 (정책 6장: 탈퇴하면 미결제 자리는 풀림). 파티 상태는 마감 배치가 신청서를 세어 바꾼다
	 * 종목은 잠그기 전에 받은 값이라 그 사이 대회가 취소되면 여기서 못 잡지만, 대회 취소 환불(RaceCancelRefundUseCase)이 막는다:
	 * 환불이 먼저 이 주문을 지나가면 예치금 결제 취소(cancelPayment)로 뒤늦은 차감이 거절되고,
	 * 이 결제가 먼저 주문을 잠그면 환불이 기다렸다가 결제 완료된 주문을 환불한다 (결제 중이면 환불이 실패로 세어 다시 처리한다)
	 * 잠그기 전에 부른다(BUK-232). 확인한 뒤 잠그기 전에 크루를 떠나면 결제되지만, 크루 탈퇴 처리가 기존처럼 다룬다
	 */
	private void checkPartyPayable(long partyId, long memberId) {
		Party party = partyRepository.findById(partyId)
			.orElseThrow(() -> new PartyNotFoundException("존재하지 않는 파티입니다."));
		boolean crewMember = crewPort.findMembership(party.getCrewId(), memberId)
			.map(CrewMembership::crewMember)
			.orElse(false);
		if (!crewMember) {
			throw PartyConflictException.payerNotCrew(party.getId());
		}
	}
}
