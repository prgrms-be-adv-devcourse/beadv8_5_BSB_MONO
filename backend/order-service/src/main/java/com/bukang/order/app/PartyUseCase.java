package com.bukang.order.app;

import static com.bukang.order.app.ParticipationApplyUseCase.checkAge;
import static com.bukang.order.app.ParticipationApplyUseCase.checkNotCanceled;
import static com.bukang.order.app.ParticipationApplyUseCase.checkShippingAddress;
import static com.bukang.order.app.ParticipationApplyUseCase.checkTshirtSize;
import static com.bukang.order.app.ParticipationApplyUseCase.isActiveParticipationConflict;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.bukang.order.domain.ParticipantInfo;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.ParticipationType;
import com.bukang.order.domain.Party;
import com.bukang.order.domain.PartyMember;
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

	private final PartyRepository partyRepository;
	private final PartyMemberRepository partyMemberRepository;
	private final ParticipationRepository participationRepository;
	private final RaceCoursePort raceCoursePort;
	private final CrewPort crewPort;

	public record Created(Party party, Participation participation) {
	}

	// 파티장도 참가자다. 만들 때 자기 종목·참가자 정보를 넣어 자기 신청서를 가진다
	public Created create(long memberId, long crewId, long raceCourseId, ParticipantInfo participant,
		LocalDateTime now) {
		RaceCourseInfo course = findOpenCourse(raceCourseId, now);
		checkCrewMember(crewId, memberId);
		ParticipantInfo checked = checkParticipant(course, participant);
		checkNoLiveParticipation(course.raceId(), memberId, null);
		Party party = partyRepository.save(Party.create(crewId, memberId, course.raceId(), now));
		partyMemberRepository.save(PartyMember.join(party.getId(), memberId, crewId, now));
		return new Created(party, saveParticipation(party, memberId, course, checked, now));
	}

	public Party regenerateJoinLink(long partyId, long memberId, LocalDateTime now) {
		Party party = lockParty(partyId);
		party.regenerateJoinToken(memberId, now);
		return party;
	}

	// shortcut: 최대 인원(crewMaxCount)은 상품 종목 응답에 아직 없어 확인하지 않는다. 그 칸이 오면(파티 신청 이슈) 여기서 막는다
	public Participation join(String joinToken, long memberId, long raceCourseId, ParticipantInfo participant,
		LocalDateTime now) {
		long partyId = partyRepository.findIdByJoinToken(joinToken)
			.orElseThrow(PartyUseCase::joinLinkNotFound);
		// 상품·회원 서비스를 부르는 확인은 파티를 잠그기 전에 한다. 잠근 채 기다리면 같은 파티의 다른 요청과 DB 연결이 묶인다
		// 대회·크루는 파티를 만든 뒤 바뀌지 않으므로 잠그지 않고 읽어도 된다. 엔티티가 아니라 값만 읽는다
		// (엔티티를 먼저 읽으면 아래 잠금 조회가 같은 엔티티를 돌려줘 기다리는 사이 바뀐 토큰·상태를 못 본다)
		JoinTarget target = partyRepository.findJoinTarget(partyId).orElseThrow(PartyUseCase::joinLinkNotFound);
		RaceCourseInfo course = findOpenCourse(raceCourseId, now);
		if (course.raceId() != target.raceId()) {
			throw PartyConflictException.raceMismatch();
		}
		checkCrewMember(target.crewId(), memberId);
		Party party = lockParty(partyId);
		// 잠그기를 기다리는 사이 파티장이 링크를 다시 만들었으면 옛 링크다
		if (!party.getJoinToken().equals(joinToken)) {
			throw joinLinkNotFound();
		}
		party.checkRecruiting();
		party.checkJoinLinkAlive(now);
		ParticipantInfo checked = checkParticipant(course, participant);
		Optional<PartyMember> row = partyMemberRepository.findByPartyIdAndMemberId(partyId, memberId);
		if (row.filter(PartyMember::isActive).isPresent()) {
			throw PartyConflictException.alreadyJoined(partyId);
		}
		checkNoLiveParticipation(party.getRaceId(), memberId, partyId);
		row.ifPresentOrElse(left -> left.rejoin(now), () -> saveMember(partyId, memberId, party.getCrewId(), now));
		return saveParticipation(party, memberId, course, checked, now);
	}

	// 신청 전에만 나갈 수 있다. 신청서는 끝난 상태가 되어 그 대회에 다시 신청할 수 있다
	// 파티장이 나가면 가장 먼저 합류한 파티원에게 넘기고, 남은 사람이 없으면 파티를 끝낸다
	public Participation leave(long partyId, long memberId, LocalDateTime now) {
		Party party = lockParty(partyId);
		party.checkRecruiting();
		PartyMember member = partyMemberRepository.findByPartyIdAndMemberId(partyId, memberId)
			.filter(PartyMember::isActive)
			.orElseThrow(PartyAccessDeniedException::notMember);
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

	private void checkCrewMember(long crewId, long memberId) {
		CrewMembership membership = crewPort.findMembership(crewId, memberId)
			.orElseThrow(() -> new CrewNotFoundException("존재하지 않는 크루입니다."));
		if (!membership.crewMember()) {
			throw PartyAccessDeniedException.crewMemberRequired();
		}
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
	private void saveMember(long partyId, long memberId, long crewId, LocalDateTime now) {
		try {
			partyMemberRepository.saveAndFlush(PartyMember.join(partyId, memberId, crewId, now));
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
