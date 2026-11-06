package com.bukang.order.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.bukang.order.domain.Order;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.Party;
import com.bukang.order.domain.exception.InsufficientBalanceException;
import com.bukang.order.domain.exception.OrderAccessDeniedException;
import com.bukang.order.domain.exception.OrderNotFoundException;
import com.bukang.order.domain.exception.OrderNotPayableException;
import com.bukang.order.domain.exception.ParticipationNotFoundException;
import com.bukang.order.domain.exception.PartyConflictException;
import com.bukang.order.domain.exception.PartyNotFoundException;
import com.bukang.order.domain.exception.RaceCourseNotFoundException;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.PartyRepository;
import com.bukang.order.out.crew.CrewPort;
import com.bukang.order.out.crew.CrewPort.CrewMembership;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.out.race.RaceCoursePort;
import com.bukang.order.out.seat.SeatHoldPort;
import com.bukang.order.out.seat.SeatSellResult;
import com.bukang.order.out.wallet.WalletPayResult;
import com.bukang.order.out.wallet.WalletPort;

import lombok.RequiredArgsConstructor;

/**
 * 임시 확보한 주문을 예치금으로 결제하고 참가를 확정한다 (정책 4장 결제, ERD 추천안 v4 9쪽 결제 순서)
 * 순서: 예치금 차감(D) → 자리 판매(B, 기한 안일 때만) → 주문 PAID·참가 신청 CONFIRMED
 * 기한이 지나 판매가 거절되면 차감한 예치금을 돌려준다. 중복 차감 방지·동시성은 D가 주문 ID로 맡는다
 */
@Service
@RequiredArgsConstructor
public class OrderPayUseCase {
	private final OrderRepository orderRepository;
	private final ParticipationRepository participationRepository;
	private final WalletPort walletPort;
	private final SeatHoldPort seatHoldPort;
	private final RaceCoursePort raceCoursePort;
	private final PartyRepository partyRepository;
	private final CrewPort crewPort;

	public OrderPayResult pay(long memberId, long orderId, LocalDateTime now) {
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
		if (participation.getPartyId() != null) {
			checkPartyPayable(participation, memberId);
		}

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
		return new OrderPayResult(order.toDto(), false);
	}

	/**
	 * 파티원 결제 전 확인 (BUK-38, 11/02 결정 ⑥). 예치금을 빼기 전에 끝낸다
	 * 결제 기한은 위 order.checkPayable이 본다(주문 기한 = 파티 결제 기한). 결제 진행 중 대회가 취소되면 결제할 수 없고,
	 * 크루를 떠난 파티원은 결제할 수 없다 (정책 6장: 탈퇴하면 미결제 자리는 풀림). 파티 상태는 마감 배치가 신청서를 세어 바꾼다
	 * shortcut: 회원·상품 HTTP를 주문·신청서 잠금을 쥔 채 부른다(예치금 호출과 같음, pending 48). 결제가 몰리면 잠그기 전으로 옮긴다
	 */
	private void checkPartyPayable(Participation participation, long memberId) {
		RaceCourseInfo course = raceCoursePort.findCourse(participation.getRaceCourseId())
			.orElseThrow(() -> new RaceCourseNotFoundException("존재하지 않는 종목입니다."));
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
