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
	 * 바깥 트랜잭션 없이 부른다. 상품 HTTP(종목·키트 옵션)와 판매 수 줄 만들기는 잠그기 전에 끝내고,
	 * 잠금·차감·확정은 한 트랜잭션(transactionTemplate)으로 묶는다
	 */
	public OrderPayResult pay(long memberId, long orderId, List<Long> kitOptionIds, LocalDateTime now) {
		Prepared prepared = prepare(memberId, orderId, kitOptionIds);
		return transactionTemplate.execute(status -> payLocked(memberId, orderId, prepared, now));
	}

	/**
	 * 잠그기 전 확인. 엔티티를 읽지 않고 값만 읽는다(잠근 뒤 옛 상태가 돌아오지 않게). 이미 결제한 주문이면 아무것도 하지 않는다
	 * 결제 대기가 아니면 상품을 묻기 전에 409로 끝낸다(빈 판매 수 줄도 만들지 않는다)
	 * 종목은 파티원 결제만 묻는다(대회 취소·크루원 확인). 개인 결제는 주문 상품의 대회 ID로 옵션만 묻는다
	 */
	private Prepared prepare(long memberId, long orderId, List<Long> kitOptionIds) {
		OrderItemRepository.PayTarget target = orderItemRepository.findPayTargets(orderId).stream().findFirst()
			.orElseThrow(() -> new OrderNotFoundException("존재하지 않는 주문입니다."));
		if (target.getBuyerId() != memberId) {
			throw new OrderAccessDeniedException("본인의 주문만 결제할 수 있습니다.");
		}
		if (target.getStatus() == OrderStatus.PAID) {
			return null;
		}
		Order.checkPayableStatus(target.getStatus());
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

	private OrderPayResult payLocked(long memberId, long orderId, Prepared prepared, LocalDateTime now) {
		// 주문을 먼저 잠근다. 같은 주문의 결제 요청과 만료 정리 배치가 주문 → 참가 신청 순서로 줄을 선다
		Order order = orderRepository.findByIdForUpdate(orderId)
			.orElseThrow(() -> new OrderNotFoundException("존재하지 않는 주문입니다."));
		if (!order.isOwnedBy(memberId)) {
			throw new OrderAccessDeniedException("본인의 주문만 결제할 수 있습니다.");
		}
		// 앞 요청이 이미 결제했으면 다시 차감하지 않고 그대로 돌려준다 (같은 결제 요청 두 번)
		if (order.isPaid()) {
			return new OrderPayResult(order.toDto(), true);
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
		return new OrderPayResult(order.toDto(), false);
	}

	/**
	 * 파티원 결제 전 확인 (BUK-38, 11/02 결정 ⑥). 예치금을 빼기 전에 끝낸다
	 * 결제 기한은 위 order.checkPayable이 본다(주문 기한 = 파티 결제 기한). 결제 진행 중 대회가 취소되면 결제할 수 없고,
	 * 크루를 떠난 파티원은 결제할 수 없다 (정책 6장: 탈퇴하면 미결제 자리는 풀림). 파티 상태는 마감 배치가 신청서를 세어 바꾼다
	 * 종목은 잠그기 전에 받은 값이다(그 사이 대회가 취소되면 여기서 못 잡는다. 대회 취소 환불 배치가 다시 잡는지는 PR #370 리뷰 답 참고)
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
