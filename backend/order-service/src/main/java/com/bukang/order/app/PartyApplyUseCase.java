package com.bukang.order.app;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.global.eventpublisher.EventPublisher;
import com.bukang.common.global.exception.BusinessException;
import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.Party;
import com.bukang.order.domain.PartyMember;
import com.bukang.order.domain.PartyStatus;
import com.bukang.order.domain.exception.CrewNotFoundException;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.domain.exception.ParticipationNotPayableException;
import com.bukang.order.domain.exception.PartyAccessDeniedException;
import com.bukang.order.domain.exception.PartyConflictException;
import com.bukang.order.domain.exception.PartyCrewSeatShortageException;
import com.bukang.order.domain.exception.PartyNotFoundException;
import com.bukang.order.domain.exception.QueueNotAdmittedException;
import com.bukang.order.domain.exception.RaceCourseNotFoundException;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.PartyMemberRepository;
import com.bukang.order.out.PartyRepository;
import com.bukang.order.out.PartyRepository.ApplyTarget;
import com.bukang.order.out.crew.CrewPort;
import com.bukang.order.out.crew.CrewPort.CrewMembership;
import com.bukang.order.out.queue.QueueAdmissionPort;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.out.race.RaceCourseInfo.CrewSale;
import com.bukang.order.out.race.RaceCoursePort;
import com.bukang.order.out.seat.CrewSeatHoldPort;
import com.bukang.order.out.seat.CrewSeatHoldPort.CrewSeatHoldResult;
import com.bukang.order.out.seat.CrewSeatHoldPort.Seat;
import com.bukang.order.out.seat.CrewSeatHoldPort.Shortage;

import lombok.RequiredArgsConstructor;

/**
 * 파티장 [신청] (BUK-37, 정책 3장 「파티 결제와 임시 확보」, 11/02 결정 ④⑤⑩, 11/05 회의 P2~P5)
 * 파티원 전원의 크루 자리를 상품에 한꺼번에 잡고(전부 아니면 실패), 신청서를 결제 대기로 바꿔 결제 기간을 연다
 * 상품·회원·대기열 호출(읽기)은 파티를 잠그기 전에 하고, 잠근 뒤 파티장·모집 중·파티원이 그대로인지 다시 본다 (pending 56)
 * 확인은 모두 크루 자리 확보 전에 끝낸다. 잠금 순서: 파티 → 파티원 → 참가 신청
 */
@Service
@RequiredArgsConstructor
public class PartyApplyUseCase {
	// 11/05 회의 P2: 파티원 결제 기한은 신청 뒤 24시간 (크루 마감일 자정·접수 마감이 더 이르면 그때)
	static final Duration PAYMENT_WINDOW = Duration.ofHours(24);
	// 11/05 회의 P3: 판매자가 정한 최소 인원이 이보다 작아도 4명은 있어야 한다
	static final int MIN_PARTY_SIZE = 4;
	private static final DateTimeFormatter DUE_FORMAT = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

	private final PartyRepository partyRepository;
	private final PartyMemberRepository partyMemberRepository;
	private final ParticipationRepository participationRepository;
	private final QueueAdmissionPort queueAdmissionPort;
	private final RaceCoursePort raceCoursePort;
	private final CrewPort crewPort;
	private final CrewSeatHoldPort crewSeatHoldPort;
	private final EventPublisher eventPublisher;
	private final TransactionTemplate transactionTemplate;

	/**
	 * 세 단계로 나눈다 (BUK-105)
	 * 1. 확인하고 파티에 「크루 자리 요청함」을 남겨 커밋한다 → 상품 응답을 못 받아도 그사이 합류·나가기가 막혀,
	 *    다시 신청하면 같은 자리 목록이 가고 상품이 처음 결과를 돌려준다 (상품은 목록이 다르면 409)
	 * 2. 잠금·DB 연결 없이 상품에 크루 자리를 요청한다 (되돌릴 수 없는 외부 호출, 파티 ID로 멱등)
	 * 3. 다시 잠그고 결제 기간을 연다. 한 석도 잡지 않았다는 답이면 표시를 지워 파티원을 다시 바꿀 수 있게 한다
	 * 상품 응답을 모를 때(503·예상 못 한 오류)는 표시를 남긴다. 파티장이 다시 신청하면 처음 결과로 이어진다
	 * 다른 요청이 상품을 부르는 중이면(두 번 누름) 1단계에서 409로 막는다 (Party.markCrewHoldRequested)
	 */
	public Party apply(long partyId, long memberId, LocalDateTime now) {
		Prepared prepared = transactionTemplate.execute(status -> prepare(partyId, memberId, now));
		CrewSeatHoldResult hold;
		try {
			hold = crewSeatHoldPort.holdAll(partyId, prepared.crewId(), prepared.raceId(), prepared.paymentDueAt(),
				prepared.seats());
		} catch (ExternalServiceUnavailableException exception) {
			endCrewHoldCall(partyId, now);
			throw exception;
		} catch (BusinessException exception) {
			// 상품이 거절했다(404·409). 이 요청으로 새로 잡힌 자리가 없으므로 파티원을 다시 바꿀 수 있게 한다
			clearCrewHoldRequested(partyId);
			throw exception;
		} catch (RuntimeException exception) {
			// 예상 못 한 오류는 상품에 잡혔는지 모른다. 표시는 남기고 파티장의 재시도만 받는다
			endCrewHoldCall(partyId, now);
			throw exception;
		}
		if (!hold.held()) {
			clearCrewHoldRequested(partyId);
			Shortage shortage = hold.shortages().get(prepared.raceCourseId());
			if (shortage == null) {
				throw new IllegalStateException("크루 자리를 잡지 못했는데 모자란 종목이 없습니다. partyId=%d, shortages=%s"
					.formatted(partyId, hold.shortages()));
			}
			throw new PartyCrewSeatShortageException(partyId, prepared.raceCourseId(), shortage.needed(),
				shortage.remaining());
		}
		// 상품이 실제로 잡아 둔 기한을 쓴다. 응답을 못 받고 다시 신청하면 상품은 처음 기한을 돌려주므로 보낸 값과 다를 수 있다
		LocalDateTime dueAt = hold.expiresAt();
		// 처음 신청의 기한이 지난 뒤 다시 누르면 상품은 지난 기한을 돌려준다. 결제 기간을 열지 않는다
		// shortcut: 상품이 같은 파티의 끝난 자리를 다시 잡지 않아 이 파티는 새로 만들어야 한다. 상품이 바꾸면 풀린다
		if (!dueAt.isAfter(now)) {
			// 상품이 확정해서 답했으므로 표시를 지운다. 남기면 파티원이 나가지 못해 새 파티도 만들 수 없다
			clearCrewHoldRequested(partyId);
			throw new ParticipationNotPayableException("이미 잡았던 크루 자리의 결제 기한이 지났습니다. 새 파티로 신청해 주세요.");
		}
		return transactionTemplate.execute(status -> startPayment(prepared, memberId, dueAt, now));
	}

	// 1단계: 확인을 모두 끝내고 「크루 자리 요청함」을 남긴다. 잠금 순서: 파티 → 파티원 → 참가 신청
	private Prepared prepare(long partyId, long memberId, LocalDateTime now) {
		// 잠그기 전 확인: 종목·크루는 파티를 만든 뒤 바뀌지 않으므로 값만 읽는다 (엔티티를 먼저 읽으면 잠금 조회가 옛 상태를 돌려준다)
		ApplyTarget target = partyRepository.findApplyTarget(partyId)
			.orElseThrow(() -> new PartyNotFoundException("존재하지 않는 파티입니다."));
		// 파티장이 아니거나 이미 신청한 파티는 상품·회원을 부르기 전에 거절한다 (기간이 지난 뒤 다시 눌러도 「이미 신청함」)
		if (target.leaderMemberId() != memberId) {
			throw PartyAccessDeniedException.leaderOnly();
		}
		if (target.status() != PartyStatus.RECRUITING) {
			throw PartyConflictException.alreadyApplied(partyId, target.status());
		}
		RaceCourseInfo course = raceCoursePort.findCourse(target.raceCourseId())
			.orElseThrow(() -> new RaceCourseNotFoundException("존재하지 않는 종목입니다."));
		CrewSale crewSale = checkSalePeriod(partyId, course, now);
		Set<Long> checkedMemberIds = new HashSet<>(partyMemberRepository.findActiveMemberIds(partyId));
		checkedMemberIds.forEach(id -> checkCrewMember(partyId, target.crewId(), id, crewSale));
		// 대기열도 HTTP라 잠그기 전에 묻는다. 파티장은 위에서 확인했고 잠근 뒤 checkApplicable로 다시 본다
		checkQueueAdmitted(memberId, course, now);

		Party party = partyRepository.findByIdForUpdate(partyId)
			.orElseThrow(() -> new PartyNotFoundException("존재하지 않는 파티입니다."));
		// 위 확인과 파티장 확인 API(pending 56)는 잠그지 않으므로, 그 뒤 파티장이 넘어갔거나 신청됐을 수 있어 다시 본다
		// shortcut: now는 요청을 받은 시각이라 잠금·HTTP를 기다린 몇 초만큼 기간·입장 판정이 이르다. 마감 경계 몇 초는 받아들인다
		party.checkApplicable(memberId);
		List<PartyMember> members = partyMemberRepository.findActiveByPartyIdForUpdate(partyId);
		Set<Long> memberIds = members.stream().map(PartyMember::getMemberId).collect(Collectors.toSet());
		// 회원 서비스에 물은 뒤 누가 합류하거나 나갔으면, 확인하지 않은 사람이 섞이므로 다시 누르게 한다
		if (!memberIds.equals(checkedMemberIds)) {
			throw PartyConflictException.membersChanged(partyId);
		}
		checkSize(partyId, members.size(), crewSale);
		List<Participation> participations = participationRepository.findActiveByPartyIdForUpdate(partyId);
		if (participations.size() != members.size()) {
			throw new IllegalStateException("파티원과 신청서 수가 다릅니다. partyId=%d, members=%d, participations=%d"
				.formatted(partyId, members.size(), participations.size()));
		}
		participations.forEach(Participation::checkPayable);

		// 상품 자리 기한도 결제 기한과 같은 시각으로 보낸다 (요청서 「확인할 것」 1번, 11/05 회의 P1·P2)
		LocalDateTime paymentDueAt = paymentDueAt(course, crewSale, now);
		List<Seat> seats = participations.stream()
			.map(participation -> new Seat(participation.getId(), participation.getRaceCourseId()))
			.toList();
		party.markCrewHoldRequested(now);
		return new Prepared(partyId, target.crewId(), course.raceId(), course.raceCourseId(), course.courseName(),
			paymentDueAt, seats);
	}

	// 3단계: 다시 잠그고 결제 기간을 연다. 1단계 뒤로 합류·나가기가 막혀 있어 파티원·신청서는 그대로여야 한다
	private Party startPayment(Prepared prepared, long memberId, LocalDateTime dueAt, LocalDateTime now) {
		long partyId = prepared.partyId();
		Party party = partyRepository.findByIdForUpdate(partyId)
			.orElseThrow(() -> new PartyNotFoundException("존재하지 않는 파티입니다."));
		// 파티장이 두 번 눌러 다른 요청이 먼저 결제 기간을 열었으면 「이미 신청함」이다
		party.checkApplicable(memberId);
		List<PartyMember> members = partyMemberRepository.findActiveByPartyIdForUpdate(partyId);
		List<Participation> participations = participationRepository.findActiveByPartyIdForUpdate(partyId);
		Set<Long> participationIds = participations.stream().map(Participation::getId).collect(Collectors.toSet());
		Set<Long> heldIds = prepared.seats().stream().map(Seat::participationId).collect(Collectors.toSet());
		if (!participationIds.equals(heldIds)) {
			throw new IllegalStateException("크루 자리를 잡은 뒤 신청서가 바뀌었습니다. partyId=%d, held=%s, now=%s"
				.formatted(partyId, heldIds, participationIds));
		}
		participations.forEach(participation -> participation.markApplied(dueAt));
		party.startPayment(dueAt, now);
		// 커밋된 뒤에만 나간다 (롤백되면 보내지 않는다). 잃어도 되는 메시지라 아웃박스 없이 보낸다 (11/04 결정)
		members.forEach(member -> eventPublisher.publish(NotificationRequestedEvent.of(member.getMemberId(),
			NotificationKind.PARTY_PAYMENT_REQUESTED, "파티 결제를 시작해 주세요",
			prepared.courseName() + " 파티 신청이 끝났어요.\n" + dueAt.format(DUE_FORMAT) + "까지 결제해 주세요.",
			// shortcut: 프론트 파티 상세 경로가 아직 없어 /parties/{id}로 둔다. 화면이 생기면 맞춘다
			"/parties/" + partyId)));
		return party;
	}

	private void endCrewHoldCall(long partyId, LocalDateTime now) {
		transactionTemplate.executeWithoutResult(status -> partyRepository.findByIdForUpdate(partyId)
			.ifPresent(party -> party.markCrewHoldCallEnded(now)));
	}

	private void clearCrewHoldRequested(long partyId) {
		transactionTemplate.executeWithoutResult(status -> partyRepository.findByIdForUpdate(partyId)
			.ifPresent(Party::clearCrewHoldRequested));
	}

	// 1단계에서 정한 값. 3단계는 이 자리 목록으로 신청서가 그대로인지 본다
	private record Prepared(long partyId, long crewId, long raceId, long raceCourseId, String courseName,
		LocalDateTime paymentDueAt, List<Seat> seats) {
	}

	// 11/05 회의 P2 + 11/09 호빈님 요청: min(신청 + 24시간, 크루 마감일 다음 날 0시, 접수 마감)
	// 크루 마감 시각(예: 19:00) 뒤에는 새 신청만 막고, 이미 신청한 파티는 그날 자정까지 결제한다 (상품이 이 기한으로 결제·반환을 판정)
	static LocalDateTime paymentDueAt(RaceCourseInfo course, CrewSale crewSale, LocalDateTime now) {
		LocalDateTime dueAt = now.plus(PAYMENT_WINDOW);
		LocalDateTime crewCloseAt = crewSale.closeAt();
		if (crewCloseAt.isBefore(dueAt)) {
			dueAt = crewCloseAt;
		}
		if (course.applicationCloseAt().isBefore(dueAt)) {
			dueAt = course.applicationCloseAt();
		}
		return dueAt;
	}

	private static CrewSale checkSalePeriod(long partyId, RaceCourseInfo course, LocalDateTime now) {
		ParticipationApplyUseCase.checkNotCanceled(course);
		CrewSale crewSale = course.crewSale();
		// 상품이 파티 예매 기간·최소 인원을 주지 않으면(크루 예매가 없는 대회, 옛 상품) 판정할 수 없다 (HttpRaceCourseAdapter)
		if (crewSale == null) {
			throw new ExternalServiceUnavailableException("파티 신청을 아직 쓸 수 없습니다. 잠시 뒤 다시 시도해주세요.");
		}
		if (!crewSale.isOpen(now) || !course.isBeforeClose(now)) {
			throw PartyConflictException.saleClosed(partyId);
		}
		return crewSale;
	}

	// 파티원 모두 지금 크루원이고, 파티 예매 시작 7일 전까지 가입했어야 한다 (11/05 회의 P4)
	private void checkCrewMember(long partyId, long crewId, long memberId, CrewSale crewSale) {
		CrewMembership membership = crewPort.findMembership(crewId, memberId)
			.orElseThrow(() -> new CrewNotFoundException("존재하지 않는 크루입니다."));
		if (!membership.crewMember()) {
			throw PartyConflictException.memberNotCrew(partyId);
		}
		if (membership.joinedAt() == null || membership.joinedAt().isAfter(crewSale.joinDeadline())) {
			throw PartyConflictException.memberJoinedLate(partyId);
		}
	}

	// 파티장이 파티 대기열(#347)을 통과해 입장 중이어야 한다. 개인 대기열 입장으로는 신청할 수 없다 (11/03 결정)
	private void checkQueueAdmitted(long leaderMemberId, RaceCourseInfo course, LocalDateTime now) {
		queueAdmissionPort.findPartyAdmissionEndsAt(leaderMemberId, course.raceId(), course.raceCourseId(), now)
			.orElseThrow(() -> new QueueNotAdmittedException("파티 대기열에서 입장한 뒤 파티를 신청할 수 있습니다."));
	}

	// 11/05 회의 P3: 최소 4명(판매자 최소 인원이 더 크면 그 수) ~ 판매자 최대 인원
	private static void checkSize(long partyId, int size, CrewSale crewSale) {
		int minCount = Math.max(MIN_PARTY_SIZE, crewSale.minCount());
		if (size < minCount || crewSale.exceedsMax(size)) {
			throw PartyConflictException.sizeOutOfRange(partyId, size, minCount, crewSale.maxCount());
		}
	}
}
