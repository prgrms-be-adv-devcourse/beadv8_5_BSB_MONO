package com.bukang.order.app;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.domain.Order;
import com.bukang.order.domain.OrderStatus;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.Party;
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
import com.bukang.order.out.wallet.WalletBusyException;
import com.bukang.order.out.wallet.WalletPayResult;
import com.bukang.order.out.wallet.WalletPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 임시 확보한 주문을 예치금으로 결제하고 참가를 확정한다 (정책 4장 결제, ERD 추천안 v4 9쪽 결제 순서)
 * 순서: 키트 옵션 판매 수 +1 → 예치금 차감(D) → 자리 판매(B, 기한 안일 때만) → 주문 PAID·참가 신청 CONFIRMED·고른 옵션 사본
 * 기한이 지나 판매가 거절되면 차감한 예치금을 돌려준다. 중복 차감 방지·동시성은 D가 주문 ID로 맡는다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPayUseCase {
	static final String KIT_OPTIONS_UNAVAILABLE_MESSAGE = "상품 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.";

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

	/**
	 * 바깥 트랜잭션 없이 부른다. 상품 HTTP(종목·키트 옵션)와 판매 수 줄 만들기는 잠그기 전에 끝낸다
	 * C안 시제품(측정 브랜치): 판매 수 +1·옵션 사본·결제 중 표시를 짧은 트랜잭션으로 커밋해 잠금을 풀고,
	 * 예치금·자리 판매는 트랜잭션 밖에서 부른 뒤 다시 잠가 확정한다
	 */
	public OrderPayResult pay(long memberId, long orderId, List<Long> kitOptionIds, LocalDateTime now) {
		Prepared prepared = prepare(memberId, orderId, kitOptionIds, now);
		Started started = transactionTemplate.execute(status -> startPaying(memberId, orderId, prepared, now));
		if (started.alreadyPaid() != null) {
			return started.alreadyPaid();
		}
		return drive(orderId, memberId, started.amount(), started.participationId(), now, false);
	}

	/**
	 * 결제 중으로 오래 남은 주문을 다시 확인한다 (결제 재확인 배치). 예치금 결제·자리 판매가 멱등이라 같은 순서로 다시 부른다
	 * 결과를 또 모르면 결제 중으로 남고 다음 실행에서 다시 본다
	 */
	public void recheck(long orderId, LocalDateTime now) {
		Started paying = transactionTemplate.execute(status -> orderRepository.findById(orderId)
			.filter(Order::isPaying)
			.map(order -> new Started(null, order.getBuyerId(), order.getTotalAmount(), order.participationId()))
			.orElse(null));
		if (paying != null) {
			drive(orderId, paying.memberId(), paying.amount(), paying.participationId(), now, true);
		}
	}

	private record Started(OrderPayResult alreadyPaid, long memberId, long amount, long participationId) {
	}

	/**
	 * 잠금 밖에서 예치금 결제 → 자리 판매 → 확정. 요청과 결제 재확인 배치가 함께 쓴다
	 * 거절이 확실하면(잔액 부족, 4xx, 연결 거절, 기한 지남) 판매 수와 결제 중을 되돌린다. 결과를 모르면(시간 초과·5xx) 결제 중으로 남긴다
	 */
	private OrderPayResult drive(long orderId, long memberId, long amount, long participationId, LocalDateTime now,
		boolean recheck) {
		WalletPayResult payment;
		try {
			payment = walletPort.pay(orderId, memberId, amount);
		} catch (ExternalServiceUnavailableException exception) {
			throw exception;
		} catch (WalletBusyException exception) {
			// 동시 호출 자리를 못 받아 예치금을 부르지 않았다. 요청이면 되돌리고, 재확인 배치면 결제 중으로 두고 다음에 다시 본다
			if (!recheck) {
				revert(orderId);
			}
			throw exception;
		} catch (RuntimeException exception) {
			revert(orderId);
			throw exception;
		}
		if (payment.outcome() == WalletPayResult.Outcome.INSUFFICIENT_BALANCE) {
			// 주문과 자리는 그대로 둔다. 기한 안에 충전하고 같은 주문으로 다시 결제한다 (충전은 D PRO-81·82)
			revert(orderId);
			throw new InsufficientBalanceException(payment.shortage());
		}
		SeatSellResult sold;
		try {
			sold = seatHoldPort.sell(participationId);
		} catch (ExternalServiceUnavailableException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			walletPort.cancelPayment(orderId, memberId);
			revert(orderId);
			throw exception;
		}
		if (sold == SeatSellResult.EXPIRED) {
			// 차감은 됐는데 자리가 기한을 넘겼다. 돈을 돌려주고 결제를 거절한다. 주문은 만료 정리가 EXPIRED로 끝낸다
			walletPort.cancelPayment(orderId, memberId);
			revert(orderId);
			throw OrderNotPayableException.deadlinePassed("결제 기한이 지나 결제하지 못했습니다. 빠져나간 예치금은 돌려드렸습니다.");
		}
		return transactionTemplate.execute(status -> confirm(orderId, memberId, now));
	}

	private OrderPayResult confirm(long orderId, long memberId, LocalDateTime now) {
		Order order = orderRepository.findByIdForUpdate(orderId)
			.orElseThrow(() -> new OrderNotFoundException("존재하지 않는 주문입니다."));
		// 요청과 배치가 함께 확정하면 뒤쪽은 이미 결제한 것으로 돌려준다
		if (order.isPaid()) {
			return new OrderPayResult(order.toDto(), true);
		}
		if (!order.isPaying()) {
			// 다른 쪽이 되돌린 뒤에 차감됐다(일어나지 않아야 함). 빠진 돈을 돌려준다
			walletPort.cancelPayment(orderId, memberId);
			throw new OrderNotPayableException("결제 상태가 바뀌었습니다. 다시 시도해주세요.");
		}
		Participation participation = participationRepository.findByIdForUpdate(order.participationId())
			.orElseThrow(() -> new ParticipationNotFoundException("존재하지 않는 참가 신청입니다."));
		// 키트가 있으면 이 때 참가 신청의 배송지로 발송 대기 배송을 만든다 (BUK-6)
		order.markPaid(now, participation.getParticipant().getShippingAddress());
		participation.markConfirmed(now);
		return new OrderPayResult(order.toDto(), false);
	}

	// 판매 수 -1, 옵션 사본 지우기, 결제 중 → 결제 대기. 이미 확정·되돌린 주문은 그대로 둔다
	private void revert(long orderId) {
		transactionTemplate.executeWithoutResult(status -> orderRepository.findByIdForUpdate(orderId)
			.filter(Order::isPaying)
			.ifPresent(order -> {
				kitOptionSaleUseCase.unreserve(order.participationId());
				order.revertPaying();
			}));
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
		Order.checkPayable(target.getStatus(), target.getPaymentDueAt(), now);
		RaceCourseInfo course = null;
		if (target.getPartyId() != null) {
			course = findCourse(target.getRaceCourseId());
			// 취소된 대회는 옵션을 묻지 않고 409 (상품은 취소된 대회의 옵션 목록에 404를 준다)
			ParticipationApplyUseCase.checkNotCanceled(course);
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

	private record Prepared(RaceCourseInfo course, List<KitOption> kitOptions) {
	}

	private Started startPaying(long memberId, long orderId, Prepared prepared, LocalDateTime now) {
		// 주문을 먼저 잠근다. 같은 주문의 결제 요청과 만료 정리 배치가 주문 → 참가 신청 순서로 줄을 선다
		Order order = orderRepository.findByIdForUpdate(orderId)
			.orElseThrow(() -> new OrderNotFoundException("존재하지 않는 주문입니다."));
		if (!order.isOwnedBy(memberId)) {
			throw new OrderAccessDeniedException("본인의 주문만 결제할 수 있습니다.");
		}
		// 앞 요청이 이미 결제했으면 다시 차감하지 않고 그대로 돌려준다 (같은 결제 요청 두 번)
		if (order.isPaid()) {
			return new Started(new OrderPayResult(order.toDto(), true), memberId, 0, 0);
		}
		order.checkPayable(now);
		// 참가 신청 확인도 차감 전에 끝낸다. 차감 뒤에 실패하면 돈만 빠지고 주문은 CREATED로 남는다
		// 참가 신청도 잠근다. 참가자 정보 수정이 옛 상태로 덮어써 CONFIRMED가 사라지지 않게 한다
		Participation participation = participationRepository.findByIdForUpdate(order.participationId())
			.orElseThrow(() -> new ParticipationNotFoundException("존재하지 않는 참가 신청입니다."));
		participation.checkConfirmable();
		if (prepared == null) {
			// 잠그기 전에는 결제 완료였는데 지금은 아니다(일어나지 않아야 함). 확인 없이 진행하지 않는다
			throw new OrderNotPayableException("결제 상태가 바뀌었습니다. 다시 시도해주세요.");
		}
		if (participation.getPartyId() != null) {
			checkPartyPayable(participation, prepared.course(), memberId);
		}
		// 판매 수를 세고 옵션 사본을 남긴 뒤 결제 중으로 커밋한다. 판매 수 줄 잠금은 예치금 HTTP 전에 풀린다
		kitOptionSaleUseCase.reserve(prepared.kitOptions());
		kitOptionSaleUseCase.saveChoices(participation.getId(), prepared.kitOptions());
		order.startPaying();
		return new Started(null, memberId, order.getTotalAmount(), order.participationId());
	}

	/**
	 * 파티원 결제 전 확인 (BUK-38, 11/02 결정 ⑥). 예치금을 빼기 전에 끝낸다
	 * 결제 기한은 위 order.checkPayable이 본다(주문 기한 = 파티 결제 기한). 결제 진행 중 대회가 취소되면 결제할 수 없고,
	 * 크루를 떠난 파티원은 결제할 수 없다 (정책 6장: 탈퇴하면 미결제 자리는 풀림). 파티 상태는 마감 배치가 신청서를 세어 바꾼다
	 * 종목은 잠그기 전에 받은 값이라 그 사이 대회가 취소되면 여기서 못 잡지만, 대회 취소 환불(RaceCancelRefundUseCase)이 막는다:
	 * 환불이 먼저 이 주문을 지나가면 예치금 결제 취소(cancelPayment)로 뒤늦은 차감이 거절되고,
	 * 이 결제가 먼저 주문을 잠그면 환불이 기다렸다가 결제 완료된 주문을 환불한다
	 * shortcut: 회원 HTTP를 주문·신청서 잠금을 쥔 채 부른다(예치금 호출과 같음, pending 48). 결제가 몰리면 잠그기 전으로 옮긴다
	 */
	private void checkPartyPayable(Participation participation, RaceCourseInfo course, long memberId) {
		ParticipationApplyUseCase.checkNotCanceled(course);
		Party party = partyRepository.findById(participation.getPartyId())
			.orElseThrow(() -> new PartyNotFoundException("존재하지 않는 파티입니다."));
		boolean crewMember = crewPort.findMembership(party.getCrewId(), memberId)
			.map(CrewMembership::crewMember)
			.orElse(false);
		if (!crewMember) {
			throw PartyConflictException.payerNotCrew(party.getId());
		}
	}
}
