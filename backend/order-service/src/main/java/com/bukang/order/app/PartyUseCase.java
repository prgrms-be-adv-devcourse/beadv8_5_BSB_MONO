package com.bukang.order.app;

import static com.bukang.order.app.ParticipationApplyUseCase.checkAge;
import static com.bukang.order.app.ParticipationApplyUseCase.checkNotCanceled;
import static com.bukang.order.app.ParticipationApplyUseCase.checkShippingAddress;
import static com.bukang.order.app.ParticipationApplyUseCase.checkTshirtSize;
import static com.bukang.order.app.ParticipationApplyUseCase.isActiveParticipationConflict;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.bukang.order.domain.ParticipantInfo;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.domain.ParticipationType;
import com.bukang.order.domain.Party;
import com.bukang.order.domain.PartyMember;
import com.bukang.order.domain.PartyStatus;
import com.bukang.order.domain.exception.ApplicationPeriodException;
import com.bukang.order.domain.exception.CrewNotFoundException;
import com.bukang.order.domain.exception.DuplicateParticipationException;
import com.bukang.order.domain.exception.IndividualParticipationExistsException;
import com.bukang.order.domain.exception.PartyAccessDeniedException;
import com.bukang.order.domain.exception.PartyConflictException;
import com.bukang.order.domain.exception.PartyNotFoundException;
import com.bukang.order.domain.exception.RaceCourseNotFoundException;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.PartyMemberRepository;
import com.bukang.order.out.PartyRepository;
import com.bukang.order.out.PartyRepository.JoinTarget;
import com.bukang.order.out.crew.CrewPort;
import com.bukang.order.out.crew.CrewPort.CrewMembership;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.out.race.RaceCoursePort;

import lombok.RequiredArgsConstructor;

/**
 * 파티 만들기·참가 링크·합류·나가기 (BUK-36, 정책 3장 「파티 만들기와 합류」, 11/02·11/03 결정)
 * 합류를 확정하면 그 사람의 파티원 신청서(결제 전)를 만든다. "한 대회 한 신청" 유니크가 개인 신청·다른 파티 합류를 막는다
 * 파티원을 바꾸는 요청은 파티 줄을 먼저 잠근다 (잠금 순서: 파티 → 참가 신청)
 * 파티를 만드는 기간은 제한하지 않는다. 대회가 취소됐거나 접수가 마감됐으면 막는다
 */
@Service
@RequiredArgsConstructor
public class PartyUseCase {
	// PartyMember의 @UniqueConstraint 이름과 같아야 한다
	private static final String PARTY_MEMBER_CONSTRAINT = "uk_party_member";

	// 내 파티 상태에서 보는 상태. 종료(와 앞으로 생길 끝난 상태)는 빼고, 이미 신청한 파티도 알려 주려고 결제 중·끝을 넣는다
	// BUK-38에서 파티 상태가 새로 생기면 여기에 넣을지 같이 본다 (안 넣으면 그 파티는 「파티 없음」으로 보임)
	private static final EnumSet<PartyStatus> LIVE_STATUSES =
		EnumSet.of(PartyStatus.RECRUITING, PartyStatus.PAYING, PartyStatus.PAID);
	// 정책 최소·최대 인원 (11/05 P3). 합류·신청은 판매자 값(RaceCourseInfo.crewSale)을 쓰고,
	// shortcut: 내 파티 상태 응답(PartyFacade)은 아직 이 정책값을 보여 준다. 상태 조회에서 종목을 읽게 되면 판매자 값으로 바꾼다
	public static final int MIN_PARTY_SIZE = 4;
	public static final int MAX_PARTY_SIZE = RaceCourseInfo.CrewSale.MAX_COUNT_CAP;

	private final PartyRepository partyRepository;
	private final PartyMemberRepository partyMemberRepository;
	private final ParticipationRepository participationRepository;
	private final RaceCoursePort raceCoursePort;
	private final CrewPort crewPort;

	public record Created(Party party, Participation participation) {
	}

	// 파티장도 참가자다. 만들 때 고른 종목이 파티 종목이 되고, 파티장 참가자 정보로 자기 신청서를 가진다
	public Created create(long memberId, long crewId, long raceCourseId, ParticipantInfo participant,
		LocalDateTime now) {
		RaceCourseInfo course = findOpenCourse(raceCourseId, now);
		LocalDateTime crewJoinedAt = checkCrewMember(crewId, memberId);
		ParticipantInfo checked = checkParticipant(course, participant);
		checkNoLiveParticipation(course.raceId(), memberId, null);
		Party party = partyRepository.save(Party.create(crewId, memberId, course.raceId(), course.raceCourseId(), now));
		partyMemberRepository.save(PartyMember.join(party.getId(), memberId, crewId, crewJoinedAt, now));
		return new Created(party, saveParticipation(party, memberId, course, checked, now));
	}

	public Party regenerateJoinLink(long partyId, long memberId, LocalDateTime now) {
		Party party = lockParty(partyId);
		party.regenerateJoinToken(memberId, now);
		return party;
	}

	// 종목은 고르지 않는다. 파티 종목으로 신청서를 만들고 나이·티셔츠 확인도 그 종목 기준이다 (11/05)
	public Participation join(String joinToken, long memberId, ParticipantInfo participant, LocalDateTime now) {
		long partyId = partyRepository.findIdByJoinToken(joinToken)
			.orElseThrow(PartyUseCase::joinLinkNotFound);
		// 상품·회원 서비스를 부르는 확인은 파티를 잠그기 전에 한다. 잠근 채 기다리면 같은 파티의 다른 요청과 DB 연결이 묶인다
		// 종목·크루는 파티를 만든 뒤 바뀌지 않으므로 잠그지 않고 읽어도 된다. 엔티티가 아니라 값만 읽는다
		// (엔티티를 먼저 읽으면 아래 잠금 조회가 같은 엔티티를 돌려줘 기다리는 사이 바뀐 토큰·상태를 못 본다)
		JoinTarget target = partyRepository.findJoinTarget(partyId).orElseThrow(PartyUseCase::joinLinkNotFound);
		RaceCourseInfo course = findOpenCourse(target.raceCourseId(), now);
		LocalDateTime crewJoinedAt = checkCrewMember(target.crewId(), memberId);
		Party party = lockParty(partyId);
		// 잠그기를 기다리는 사이 파티장이 링크를 다시 만들었으면 옛 링크다
		if (!party.getJoinToken().equals(joinToken)) {
			throw joinLinkNotFound();
		}
		party.checkMembersChangeable();
		party.checkJoinLinkAlive(now);
		ParticipantInfo checked = checkParticipant(course, participant);
		Optional<PartyMember> row = partyMemberRepository.findByPartyIdAndMemberId(partyId, memberId);
		if (row.filter(PartyMember::isActive).isPresent()) {
			throw PartyConflictException.alreadyJoined(partyId);
		}
		checkNotFull(party, course);
		checkNoLiveParticipation(party.getRaceId(), memberId, partyId);
		row.ifPresentOrElse(left -> left.rejoin(crewJoinedAt, now),
			() -> saveMember(partyId, memberId, party.getCrewId(), crewJoinedAt, now));
		return saveParticipation(party, memberId, course, checked, now);
	}

	// 신청 전에만 나갈 수 있다. 신청서는 끝난 상태가 되어 그 대회에 다시 신청할 수 있다
	// 파티장이 나가면 가장 먼저 합류한 파티원에게 넘기고, 남은 사람이 없으면 파티를 끝낸다
	public Participation leave(long partyId, long memberId, LocalDateTime now) {
		Party party = lockParty(partyId);
		// 파티원인지 먼저 본다. 모집이 끝난 거절(409)은 파티 상태를 알려 주므로 파티원이 아닌 사람에게 주지 않는다
		PartyMember member = partyMemberRepository.findByPartyIdAndMemberId(partyId, memberId)
			.filter(PartyMember::isActive)
			.orElseThrow(PartyAccessDeniedException::notMember);
		party.checkMembersChangeable();
		Participation participation = participationRepository.findActiveIdByPartyIdAndMemberId(partyId, memberId)
			.flatMap(participationRepository::findByIdForUpdate)
			.orElseThrow(() -> new IllegalStateException("파티원의 신청서가 없습니다. partyId=%d, memberId=%d"
				.formatted(partyId, memberId)));
		participation.cancelBeforePayment(now);
		member.leave(now);
		if (party.isLeader(memberId)) {
			partyMemberRepository.findFirstByPartyIdAndLeftAtIsNullAndMemberIdNotOrderByJoinedAtAscIdAsc(
					partyId, memberId)
				.ifPresentOrElse(next -> party.handOverLeader(next.getMemberId()), () -> party.close(now));
		}
		return participation;
	}

	// 판매자가 정한 최대 인원(crewMaxCount)이 찼으면 막는다. 동시에 들어온 합류도 세도록 파티원을 잠가 읽는다
	// shortcut: 상품 종목 조회가 아직 파티 예매 칸을 주지 않으면(crewSale null) 막지 않는다. 신청 때 다시 확인한다
	private void checkNotFull(Party party, RaceCourseInfo course) {
		if (course.crewSale() == null) {
			return;
		}
		int maxCount = course.crewSale().maxCount();
		if (partyMemberRepository.findActiveByPartyIdForUpdate(party.getId()).size() >= maxCount) {
			throw PartyConflictException.full(party.getId(), maxCount);
		}
	}

	// raceOpen: 대회가 취소되지 않았고 접수 마감 전인지. 파티장·모집 중일 때만 상품에 묻고, 그 밖에는 쓰지 않아 true로 둔다
	public record MyRaceParty(Party party, int memberCount, int paidCount, boolean myPaid, Long participationId,
		boolean raceOpen) {
	}

	// 대회 상세 화면이 [파티로 신청] 버튼을 그릴 때 묻는다. 파티 대기열은 파티장 회원 ID로만 줄을 세우므로 파티장인지는 주문이 답한다 (BUK-91)
	// 이 대회에서 내가 지금 들어가 있는(나가지 않은) 종료 안 된 파티를 찾는다. 파티장도 파티원 줄이 있다. 읽기만 해서 잠그지 않는다
	// 안내용이다. 확인과 신청 사이에 파티장·인원이 바뀔 수 있으니, 파티 신청(BUK-37)은 파티 줄을 잠근 뒤 다시 확인해야 한다
	public Optional<MyRaceParty> findMyRaceParty(long raceId, long memberId, LocalDateTime now) {
		return partyRepository.findJoinedByRaceIdAndMemberId(raceId, memberId, LIVE_STATUSES).stream()
			.findFirst()
			.map(party -> new MyRaceParty(party,
				(int)partyMemberRepository.countByPartyIdAndLeftAtIsNull(party.getId()),
				(int)participationRepository.countByPartyIdAndStatus(party.getId(), ParticipationStatus.CONFIRMED),
				participationRepository.existsByPartyIdAndMemberIdAndStatus(party.getId(), memberId,
					ParticipationStatus.CONFIRMED),
				participationRepository.findActiveIdByPartyIdAndMemberId(party.getId(), memberId).orElse(null),
				!canApplyNow(party, memberId) || isRaceOpen(party.getRaceCourseId(), now)));
	}

	private static boolean canApplyNow(Party party, long memberId) {
		return party.isLeader(memberId) && party.getStatus() == PartyStatus.RECRUITING;
	}

	// shortcut: 크루 예매 기간·판매자 최소·최대 인원·대기열 입장·파티원 7일 가입은 아직 보지 않는다 (#337이 들여옴, pending 56)
	private boolean isRaceOpen(long raceCourseId, LocalDateTime now) {
		return raceCoursePort.findCourse(raceCourseId).filter(course -> course.isBeforeClose(now)).isPresent();
	}

	private static PartyNotFoundException joinLinkNotFound() {
		return new PartyNotFoundException("참가 링크를 찾을 수 없습니다. 파티장에게 새 링크를 받아주세요.");
	}

	private Party lockParty(long partyId) {
		return partyRepository.findByIdForUpdate(partyId)
			.orElseThrow(() -> new PartyNotFoundException("존재하지 않는 파티입니다."));
	}

	private RaceCourseInfo findOpenCourse(long raceCourseId, LocalDateTime now) {
		RaceCourseInfo course = raceCoursePort.findCourse(raceCourseId)
			.orElseThrow(() -> new RaceCourseNotFoundException("존재하지 않는 종목입니다."));
		checkNotCanceled(course);
		if (!course.isBeforeClose(now)) {
			throw new ApplicationPeriodException("접수가 마감된 대회입니다.");
		}
		return course;
	}

	// 크루 가입 시각을 돌려준다. 파티원 줄에 남겨 크루 탈퇴 이벤트와 비교한다
	private LocalDateTime checkCrewMember(long crewId, long memberId) {
		CrewMembership membership = crewPort.findMembership(crewId, memberId)
			.orElseThrow(() -> new CrewNotFoundException("존재하지 않는 크루입니다."));
		if (!membership.crewMember()) {
			throw PartyAccessDeniedException.crewMemberRequired();
		}
		return membership.joinedAt();
	}

	private static ParticipantInfo checkParticipant(RaceCourseInfo course, ParticipantInfo participant) {
		checkAge(course, participant);
		return checkTshirtSize(course, checkShippingAddress(course, participant));
	}

	// 같은 대회에 살아 있는 신청이 있으면 막는다. 개인 신청은 자동으로 취소하지 않고 그 ID를 알려 준다 (11/03)
	private void checkNoLiveParticipation(long raceId, long memberId, Long partyId) {
		Optional<Participation> live = participationRepository.findActiveIdByRaceIdAndMemberId(raceId, memberId)
			.flatMap(participationRepository::findById);
		if (live.isEmpty()) {
			return;
		}
		Participation participation = live.get();
		if (participation.getParticipationType() == ParticipationType.INDIVIDUAL) {
			throw new IndividualParticipationExistsException(participation.getId());
		}
		if (partyId != null && partyId.equals(participation.getPartyId())) {
			throw PartyConflictException.alreadyJoined(partyId);
		}
		throw PartyConflictException.otherPartyJoined(participation.getPartyId());
	}

	// 같은 사람이 합류를 두 번 빠르게 누르면, 뒤 요청은 잠금을 얻은 뒤에도 트랜잭션 처음 읽기 기준이라(MySQL REPEATABLE READ)
	// 앞 요청이 만든 파티원 줄을 못 본다. 유니크 제약(uk_party_member)이 막은 것을 「이미 합류」로 바꾼다
	private void saveMember(long partyId, long memberId, long crewId, LocalDateTime crewJoinedAt, LocalDateTime now) {
		try {
			partyMemberRepository.saveAndFlush(PartyMember.join(partyId, memberId, crewId, crewJoinedAt, now));
		} catch (DataIntegrityViolationException exception) {
			String message = exception.getMostSpecificCause().getMessage();
			if (message != null && message.toLowerCase().contains(PARTY_MEMBER_CONSTRAINT)) {
				throw PartyConflictException.alreadyJoined(partyId);
			}
			throw exception;
		}
	}

	private Participation saveParticipation(Party party, long memberId, RaceCourseInfo course,
		ParticipantInfo participant, LocalDateTime now) {
		Participation participation = Participation.partyMember(
			memberId, course.raceId(), course.raceCourseId(), party.getId(), participant, now);
		try {
			// 위 확인과 저장 사이에 같은 대회 신청이 먼저 들어오면 유니크 제약(uk_participation_active)이 막는다
			return participationRepository.saveAndFlush(participation);
		} catch (DataIntegrityViolationException exception) {
			if (isActiveParticipationConflict(exception)) {
				throw new DuplicateParticipationException("이미 이 대회에 신청했습니다.");
			}
			throw exception;
		}
	}
}
