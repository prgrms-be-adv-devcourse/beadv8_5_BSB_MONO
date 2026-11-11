package com.bukang.order.app;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.domain.Participation;
import com.bukang.order.domain.Party;
import com.bukang.order.domain.PartyStatus;
import com.bukang.order.out.OrderItemRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.PartyRepository;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.out.race.RaceCoursePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 대회 다음 날 결제 끝 파티를 종료로 바꾼다 (BUK-126, 11/09 결정 ⑥). 종료되면 그 파티원은 크루를 나갈 수 있다 (BUK-125)
 * 대회 일시는 상품(RaceCoursePort)에서 종목마다 한 번 묻는다. 상품 조회가 실패하거나 종목이 없으면 그 종목만 건너뛰고 다음 실행에서 다시 본다
 * shortcut: 상품에서 종목이 지워지면(404) 그 파티는 계속 결제 끝으로 남는다. 생기면 로그를 보고 손으로 종료한다
 * 멱등: 파티를 잠근 뒤 아직 결제 끝일 때만 바꾸므로, 두 번 돌거나 서버 둘이 함께 돌아도 한 번만 바뀐다
 *
 * 대회가 취소되면 그 대회의 끝나지 않은 파티(모집 중·결제 진행 중·결제 끝)를 바로 종료한다 (BUK-155, RaceCancelRefundUseCase)
 * 남겨 두면 파티원이 크루 나가기·개인 신청에 계속 막힌다 (11/09 결정)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartyCloseUseCase {
	private static final Set<PartyStatus> NOT_CLOSED =
		EnumSet.of(PartyStatus.RECRUITING, PartyStatus.PAYING, PartyStatus.PAID);

	private final PartyRepository partyRepository;
	private final ParticipationRepository participationRepository;
	private final OrderItemRepository orderItemRepository;
	private final RaceCoursePort raceCoursePort;
	private final TransactionTemplate transactionTemplate;

	// @return 이번에 종료한 파티 수. 바깥 트랜잭션 없이 부른다 (PartyCloseScheduler)
	public int closeFinishedRaces(LocalDateTime now) {
		int closed = 0;
		for (long raceCourseId : partyRepository.findRaceCourseIdsByStatus(PartyStatus.PAID)) {
			try {
				RaceCourseInfo course = raceCoursePort.findCourse(raceCourseId).orElse(null);
				if (course == null) {
					log.warn("종목을 찾지 못해 파티 종료를 건너뜁니다. raceCourseId={}", raceCourseId);
					continue;
				}
				// 대회 당일까지는 끝나지 않았다. 다음 날 00시부터 종료한다. 취소된 대회는 열리지 않으므로 바로 종료한다
				if (!course.canceled() && !now.toLocalDate().isAfter(course.raceDate().toLocalDate())) {
					continue;
				}
				closed += closeCourse(raceCourseId, now);
			} catch (RuntimeException exception) {
				log.warn("파티 종료를 하지 못해 다음에 다시 시도합니다. raceCourseId={}, cause={}", raceCourseId,
					exception.toString());
			}
		}
		if (closed > 0) {
			log.info("대회가 끝난 파티 종료: {}건", closed);
		}
		return closed;
	}

	/**
	 * 취소된 대회의 끝나지 않은 파티를 종료하고, 결제하지 않은 파티원 신청서를 끝낸다. 파티마다 트랜잭션을 따로 연다
	 * - 확보 전(모집 중 파티): 결제 전 취소. 상품에 자리 해제를 보내지만 상품은 없거나 이미 푼 자리면 아무것도 하지 않는다
	 * - 결제 대기이고 주문이 없음: 미결제 해제. 크루 자리는 상품이 대회를 취소하며 이미 풀어서 해제 메시지를 보내지 않는다
	 * - 결제 대기이고 주문이 있음: 그대로 둔다. 환불이 예치금 결제를 막아 두고 주문 만료가 기한에 끝낸다
	 * - 참가 확정: 그대로 둔다. 대회 취소 환불이 취소·환불한다
	 * 잠금 순서: 파티 → 참가 신청 (결제 마감과 같다). 종료된 파티는 건너뛰어 두 번 불러도 한 번만 바뀐다
	 * 한 파티가 실패해도(잠금 대기 초과 등) 그 파티만 롤백하고 다음 파티를 종료한다. 실패 수를 돌려주면 부르는 쪽이
	 * 대회 취소 메시지를 다시 처리하게 하고, 그때 종료된 파티는 건너뛰어 실패한 파티만 다시 본다. 바깥 트랜잭션 없이 부른다
	 */
	public CanceledRaceCloseResult closeCanceledRace(long raceId, LocalDateTime now) {
		int closed = 0;
		int failed = 0;
		for (long partyId : partyRepository.findIdsByRaceIdAndStatusIn(raceId, NOT_CLOSED)) {
			try {
				if (Boolean.TRUE.equals(transactionTemplate.execute(status -> closeCanceledLocked(partyId, now)))) {
					closed++;
				}
			} catch (RuntimeException exception) {
				failed++;
				log.warn("취소된 대회의 파티를 종료하지 못했습니다. raceId={}, partyId={}, cause={}", raceId, partyId,
					exception.toString());
			}
		}
		if (closed > 0 || failed > 0) {
			log.info("취소된 대회의 파티 종료: raceId={}, 종료 {}건, 실패 {}건", raceId, closed, failed);
		}
		return new CanceledRaceCloseResult(closed, failed);
	}

	// failed: 이번에 종료하지 못한 파티 수. 0보다 크면 대회 취소 메시지를 다시 처리해야 한다
	public record CanceledRaceCloseResult(int closed, int failed) {
	}

	private boolean closeCanceledLocked(long partyId, LocalDateTime now) {
		Party party = partyRepository.findByIdForUpdate(partyId).orElse(null);
		if (party == null || party.getStatus() == PartyStatus.CLOSED) {
			return false;
		}
		for (Participation participation : participationRepository.findActiveByPartyIdForUpdate(partyId)) {
			switch (participation.getStatus()) {
				case PENDING -> participation.cancelBeforePayment(now);
				case APPLIED -> {
					if (orderItemRepository.findOrderIdByParticipationId(participation.getId()).isEmpty()) {
						participation.markReleased(now);
					}
				}
				default -> {
				}
			}
		}
		party.close(now);
		return true;
	}

	private int closeCourse(long raceCourseId, LocalDateTime now) {
		int closed = 0;
		for (long partyId : partyRepository.findIdsByRaceCourseIdAndStatus(raceCourseId, PartyStatus.PAID)) {
			if (Boolean.TRUE.equals(transactionTemplate.execute(status -> closeLocked(partyId, now)))) {
				closed++;
			}
		}
		return closed;
	}

	private boolean closeLocked(long partyId, LocalDateTime now) {
		Party party = partyRepository.findByIdForUpdate(partyId).orElse(null);
		if (party == null || party.getStatus() != PartyStatus.PAID) {
			return false;
		}
		party.close(now);
		return true;
	}
}
