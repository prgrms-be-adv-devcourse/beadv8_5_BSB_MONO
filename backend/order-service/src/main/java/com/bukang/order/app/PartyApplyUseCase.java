package com.bukang.order.app;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.bukang.common.global.eventpublisher.EventPublisher;
import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.Party;
import com.bukang.order.domain.PartyMember;
import com.bukang.order.domain.PartyStatus;
import com.bukang.order.domain.exception.CrewNotFoundException;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
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
import com.bukang.order.out.QueueAdmissionRepository;
import com.bukang.order.out.crew.CrewPort;
import com.bukang.order.out.crew.CrewPort.CrewMembership;
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
 * 상품·회원 호출(읽기)은 파티를 잠그기 전에 하고, 잠근 뒤 파티장·모집 중·파티원이 그대로인지 다시 본다 (pending 56)
 * 확인은 모두 크루 자리 확보 전에 끝낸다. 잠금 순서: 파티 → 파티원 → 참가 신청
 */
@Service
@RequiredArgsConstructor
public class PartyApplyUseCase {
	// 11/05 회의 P2: 파티원 결제 기한은 신청 뒤 24시간 (크루 마감·접수 마감이 더 이르면 그때)
	static final Duration PAYMENT_WINDOW = Duration.ofHours(24);
	// 11/05 회의 P3: 판매자가 정한 최소 인원이 이보다 작아도 4명은 있어야 한다
	static final int MIN_PARTY_SIZE = 4;
	private static final DateTimeFormatter DUE_FORMAT = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

	private final PartyRepository partyRepository;
	private final PartyMemberRepository partyMemberRepository;
	private final ParticipationRepository participationRepository;
	private final QueueAdmissionRepository queueAdmissionRepository;
	private final RaceCoursePort raceCoursePort;
	private final CrewPort crewPort;
	private final CrewSeatHoldPort crewSeatHoldPort;
	private final EventPublisher eventPublisher;

	public Party apply(long partyId, long memberId, LocalDateTime now) {
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

		Party party = partyRepository.findByIdForUpdate(partyId)
			.orElseThrow(() -> new PartyNotFoundException("존재하지 않는 파티입니다."));
		// 위 확인과 파티장 확인 API(pending 56)는 잠그지 않으므로, 그 뒤 파티장이 넘어갔거나 신청됐을 수 있어 다시 본다
		// shortcut: now는 요청을 받은 시각이라 잠금·HTTP를 기다린 몇 초만큼 기간·입장 판정이 이르다. 마감 경계 몇 초는 받아들인다
		party.checkApplicable(memberId);
		checkQueueAdmitted(memberId, course.raceCourseId(), now);
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
		// 되돌릴 수 없는 외부 호출. 같은 파티 ID로 다시 부르면 처음 결과를 돌려준다(멱등)
		CrewSeatHoldResult hold = crewSeatHoldPort.holdAll(partyId, paymentDueAt, participations.stream()
			.map(participation -> new Seat(participation.getId(), participation.getRaceCourseId()))
			.toList());
		if (!hold.held()) {
			Shortage shortage = hold.shortages().get(course.raceCourseId());
			if (shortage == null) {
				throw new IllegalStateException("크루 자리를 잡지 못했는데 모자란 종목이 없습니다. partyId=%d, shortages=%s"
					.formatted(partyId, hold.shortages()));
			}
			throw new PartyCrewSeatShortageException(partyId, course.raceCourseId(), shortage.needed(),
				shortage.remaining());
		}
		participations.forEach(participation -> participation.markApplied(paymentDueAt));
		party.startPayment(paymentDueAt, now);
		// 커밋된 뒤에만 나간다 (롤백되면 보내지 않는다). 잃어도 되는 메시지라 아웃박스 없이 보낸다 (11/04 결정)
		members.forEach(member -> eventPublisher.publish(NotificationRequestedEvent.of(member.getMemberId(),
			NotificationKind.PARTY_PAYMENT_REQUESTED, "파티 결제를 시작해 주세요",
			course.courseName() + " 파티 신청이 끝났어요.\n" + paymentDueAt.format(DUE_FORMAT) + "까지 결제해 주세요.",
			// shortcut: 프론트 파티 상세 경로가 아직 없어 /parties/{id}로 둔다. 화면이 생기면 맞춘다
			"/parties/" + partyId)));
		return party;
	}

	// 11/05 회의 P2: min(신청 + 24시간, 크루 마감, 접수 마감)
	static LocalDateTime paymentDueAt(RaceCourseInfo course, CrewSale crewSale, LocalDateTime now) {
		LocalDateTime dueAt = now.plus(PAYMENT_WINDOW);
		if (crewSale.endAt().isBefore(dueAt)) {
			dueAt = crewSale.endAt();
		}
		if (course.applicationCloseAt().isBefore(dueAt)) {
			dueAt = course.applicationCloseAt();
		}
		return dueAt;
	}

	private static CrewSale checkSalePeriod(long partyId, RaceCourseInfo course, LocalDateTime now) {
		ParticipationApplyUseCase.checkNotCanceled(course);
		CrewSale crewSale = course.crewSale();
		// shortcut: 상품 종목 조회에 파티 예매 칸이 아직 없어 실제 상품(http)에서는 판정할 수 없다. 호빈님이 칸을 더하면 풀린다
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

	// shortcut: 파티 대기열이 아직 없어(호빈님) 개인 예매와 같은 입장 기록(회원 × 종목)을 본다. 개인·파티 대기열을 따로 두면
	// (11/03 결정) 파티 대기열 입장 기록으로 바꾼다
	private void checkQueueAdmitted(long memberId, long raceCourseId, LocalDateTime now) {
		queueAdmissionRepository.findByMemberIdAndRaceCourseId(memberId, raceCourseId)
			.filter(admission -> admission.isAdmittedAt(now))
			.orElseThrow(() -> new QueueNotAdmittedException("대기열에서 입장한 뒤 파티를 신청할 수 있습니다."));
	}

	// 11/05 회의 P3: 최소 4명(판매자 최소 인원이 더 크면 그 수) ~ 판매자 최대 인원
	private static void checkSize(long partyId, int size, CrewSale crewSale) {
		int minCount = Math.max(MIN_PARTY_SIZE, crewSale.minCount());
		if (size < minCount || size > crewSale.maxCount()) {
			throw PartyConflictException.sizeOutOfRange(partyId, size, minCount, crewSale.maxCount());
		}
	}
}
