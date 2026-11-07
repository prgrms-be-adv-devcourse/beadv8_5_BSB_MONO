package com.bukang.order.app;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.global.eventpublisher.EventPublisher;
import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.domain.Party;
import com.bukang.order.domain.PartyStatus;
import com.bukang.order.out.OrderItemRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.PartyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 파티원 결제 기간의 마감 알림과 마감 (BUK-38, 11/02 결정 ⑥⑦⑨⑪, 11/05 회의 P1·P2)
 *
 * 마감: 기한이 지났거나 모두 결제한 파티를 끝낸다
 * - 기한이 지났으면 결제하지 않은 신청서(결제 대기)를 미결제 해제로 끝내고 상품에 자리 해제(product.hold.cancel)를 남긴다
 *   → 신청서가 살아 있지 않게 되어 그 사람은 같은 대회에 개인으로 다시 신청할 수 있다 (11/03 결정)
 *   [결제하기]로 주문을 만든 신청서는 건너뛴다. 주문 기한 = 파티 결제 기한이라 주문 만료(OrderExpiryUseCase)가
 *   예치금 결제 취소·자리 해제와 함께 끝낸다. 그래서 결제 대기가 하나라도 남으면 다음 실행에서 다시 본다
 * - 결제 대기가 없으면 결제한 사람이 있으면 결제 끝, 없으면 종료로 바꾸고 파티원에게 결제·미결제 수를 알린다
 *   수는 저장하지 않고 신청서에서 센다 (⑨). 결제 중에는 파티를 바꾸지 않아 결제끼리 파티 잠금을 다투지 않는다
 * 자리: 주문은 해제 메시지만 보낸다. 해제된 크루 자리를 크루 마감 다음 날 00시에 일반 자리로 옮기는 것은 상품 일이다 (P1)
 * 잠금 순서: 파티 → 참가 신청. 결제·주문 만료(주문 → 참가 신청)와 순서가 엇갈리지 않는다 (파티원 결제는 파티를 잠그지 않는다)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartyPaymentDeadlineUseCase {
	private static final DateTimeFormatter DUE_FORMAT = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

	private final PartyRepository partyRepository;
	private final ParticipationRepository participationRepository;
	private final OrderItemRepository orderItemRepository;
	private final OrderOutboxKafkaAppendUseCase orderOutboxKafkaAppendUseCase;
	private final EventPublisher eventPublisher;
	private final TransactionTemplate transactionTemplate;

	// 기한 3시간 전(추천값)에 결제하지 않은 파티원에게 한 번 알린다. 바깥 트랜잭션 없이 부른다 (PartyPaymentScheduler)
	public int remindDue(LocalDateTime now) {
		List<Long> partyIds = partyRepository.findIdsToRemind(PartyStatus.PAYING, now,
			now.plus(Party.PAYMENT_REMIND_BEFORE));
		return forEachParty(partyIds, "결제 마감 알림", partyId -> remindLocked(partyId, now));
	}

	// 기한이 지났거나 모두 결제한 파티를 끝낸다. 바깥 트랜잭션 없이 부른다 (PartyPaymentScheduler)
	public int finishDue(LocalDateTime now) {
		List<Long> partyIds = partyRepository.findIdsToFinishPayment(PartyStatus.PAYING, now,
			ParticipationStatus.APPLIED);
		return forEachParty(partyIds, "결제 마감", partyId -> finishLocked(partyId, now));
	}

	private boolean remindLocked(long partyId, LocalDateTime now) {
		Party party = partyRepository.findByIdForUpdate(partyId).orElse(null);
		// 조회와 잠금 사이에 다른 실행이 보냈거나 마감됐으면 건너뛴다
		if (party == null || !party.needsPaymentReminder(now)) {
			return false;
		}
		party.markPaymentReminded(now);
		participationRepository.findByPartyIdIn(List.of(partyId)).stream()
			.filter(participation -> participation.getStatus() == ParticipationStatus.APPLIED)
			.forEach(participation -> eventPublisher.publish(NotificationRequestedEvent.of(
				participation.getMemberId(), NotificationKind.PARTY_PAYMENT_DEADLINE, "파티 결제 마감이 다가와요",
				party.getPaymentDueAt().format(DUE_FORMAT) + "까지 결제하지 않으면 파티 신청이 끝나요.",
				"/parties/" + partyId)));
		return true;
	}

	private boolean finishLocked(long partyId, LocalDateTime now) {
		Party party = partyRepository.findByIdForUpdate(partyId).orElse(null);
		if (party == null || party.getStatus() != PartyStatus.PAYING) {
			return false;
		}
		List<Participation> active = participationRepository.findActiveByPartyIdForUpdate(partyId);
		if (!now.isBefore(party.getPaymentDueAt())) {
			active.stream()
				.filter(participation -> participation.getStatus() == ParticipationStatus.APPLIED)
				.filter(participation -> orderItemRepository.findOrderIdByParticipationId(participation.getId())
					.isEmpty())
				.forEach(participation -> {
					participation.markReleased(now);
					orderOutboxKafkaAppendUseCase.appendSeatRelease(participation.getId());
				});
		}
		// 주문을 만든 결제 대기가 남았거나 기한 전이면서 아직 결제 중인 사람이 있으면 다음 실행에서 다시 본다
		if (active.stream().anyMatch(participation -> participation.getStatus() == ParticipationStatus.APPLIED)) {
			return false;
		}
		// 신청 뒤의 파티원 신청서는 결제 대기 → 참가 확정 또는 미결제 해제로만 끝난다
		List<Participation> all = participationRepository.findByPartyIdIn(List.of(partyId));
		long paid = all.stream().filter(participation -> participation.getStatus() == ParticipationStatus.CONFIRMED)
			.count();
		long unpaid = all.stream().filter(participation -> participation.getStatus() == ParticipationStatus.RELEASED)
			.count();
		party.finishPayment(paid > 0, now);
		all.stream()
			.filter(participation -> participation.getStatus() == ParticipationStatus.CONFIRMED
				|| participation.getStatus() == ParticipationStatus.RELEASED)
			.forEach(participation -> eventPublisher.publish(NotificationRequestedEvent.of(
				participation.getMemberId(), NotificationKind.PARTY_PAYMENT_DEADLINE, "파티 결제가 마감됐어요",
				finishMessage(participation.getStatus(), paid, unpaid), "/parties/" + partyId)));
		return true;
	}

	private static String finishMessage(ParticipationStatus status, long paid, long unpaid) {
		String counts = "결제 %d명, 결제하지 않음 %d명으로 마감됐어요.".formatted(paid, unpaid);
		if (status == ParticipationStatus.RELEASED) {
			return counts + "\n결제하지 않아 파티 신청이 끝났어요. 개인으로 다시 신청할 수 있어요.";
		}
		return counts;
	}

	// 파티마다 트랜잭션을 따로 연다. 한 파티가 실패해도 그 파티만 롤백되고 다음 실행(1분 뒤)에 다시 본다
	private int forEachParty(List<Long> partyIds, String job, PartyJob action) {
		int done = 0;
		for (long partyId : partyIds) {
			try {
				if (Boolean.TRUE.equals(transactionTemplate.execute(status -> action.run(partyId)))) {
					done++;
				}
			} catch (RuntimeException exception) {
				log.warn("파티 {} 처리에 실패해 다음에 다시 시도합니다. partyId={}, cause={}", job, partyId,
					exception.toString());
			}
		}
		if (done > 0) {
			log.info("파티 {}: {}건", job, done);
		}
		return done;
	}

	@FunctionalInterface
	private interface PartyJob {
		boolean run(long partyId);
	}
}
