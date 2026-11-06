package com.bukang.order.app;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.bukang.order.domain.Participation;
import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.domain.Party;
import com.bukang.order.domain.PartyMember;
import com.bukang.order.domain.PartyStatus;
import com.bukang.order.domain.exception.CrewNotFoundException;
import com.bukang.order.domain.exception.PartyAccessDeniedException;
import com.bukang.order.domain.exception.PartyNotFoundException;
import com.bukang.order.dto.PartyDetailDto;
import com.bukang.order.dto.PartyPreviewDto;
import com.bukang.order.dto.PartySummaryDto;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.PartyMemberRepository;
import com.bukang.order.out.PartyRepository;
import com.bukang.order.out.crew.CrewPort;
import com.bukang.order.out.crew.CrewPort.CrewMembership;
import com.bukang.order.out.member.MemberNicknamesPort;

import lombok.RequiredArgsConstructor;

/**
 * 파티 조회 (BUK-39): 상세, 내 파티, 참가 링크 미리 보기, 크루 홈 진행 중인 파티
 * 인원은 저장하지 않고 파티원 줄과 신청서에서 센다 (11/02 결정 ⑨). 결제한 사람 = 신청서가 참가 확정
 * 트랜잭션을 걸지 않는다. 회원 서비스(닉네임·크루원)를 기다리는 동안 DB 연결을 잡지 않으려고, 저장소 조회마다 짧게 읽는다
 */
@Service
@RequiredArgsConstructor
public class PartyQueryFacade {
	// 크루 홈 「진행 중」 = 모집 중 + 결제 진행 중 (11/05 P7 A: 크루원에게 모두 보여 주고 합류는 링크로만)
	static final List<PartyStatus> ACTIVE_STATUSES = List.of(PartyStatus.RECRUITING, PartyStatus.PAYING);

	private final PartyRepository partyRepository;
	private final PartyMemberRepository partyMemberRepository;
	private final ParticipationRepository participationRepository;
	private final CrewPort crewPort;
	private final MemberNicknamesPort memberNicknamesPort;

	// 파티원만 본다. 크루 홈에서 남의 파티 카드는 누를 수 없다 (11/05 ⑥)
	public PartyDetailDto find(long partyId, long memberId) {
		Party party = partyRepository.findById(partyId)
			.orElseThrow(() -> new PartyNotFoundException("존재하지 않는 파티입니다."));
		Rows rows = load(List.of(party));
		List<PartyMember> members = rows.members(partyId);
		if (members.stream().noneMatch(member -> member.getMemberId() == memberId)) {
			throw PartyAccessDeniedException.notMember();
		}
		Map<Long, String> nicknames = memberNicknamesPort.findNicknames(
			members.stream().map(PartyMember::getMemberId).toList());
		List<PartyDetailDto.Member> memberDtos = members.stream().map(member -> {
			ParticipationStatus status = rows.latestStatus(partyId, member.getMemberId());
			return new PartyDetailDto.Member(member.getMemberId(), nickname(nicknames, member.getMemberId()),
				party.isLeader(member.getMemberId()), member.getJoinedAt(), status,
				status == ParticipationStatus.CONFIRMED);
		}).toList();
		boolean leader = party.isLeader(memberId);
		return new PartyDetailDto(party.getId(), party.getCrewId(), party.getRaceId(), party.getRaceCourseId(),
			party.getLeaderMemberId(), party.getStatus(), party.getPaymentDueAt(),
			leader ? party.getJoinToken() : null, party.getJoinTokenExpiresAt(),
			members.size(), rows.paidCount(partyId), memberDtos);
	}

	// 내가 지금 들어 있는 파티. raceId를 주면 그 대회만 (대회 상세의 「내 파티 보기」)
	public List<PartySummaryDto> findMine(long memberId, Long raceId) {
		List<Long> partyIds = partyMemberRepository.findByMemberIdAndLeftAtIsNull(memberId).stream()
			.map(PartyMember::getPartyId).toList();
		List<Party> parties = partyRepository.findAllById(partyIds).stream()
			.filter(party -> raceId == null || party.getRaceId() == raceId)
			.sorted(Comparator.comparing(Party::getId).reversed())
			.toList();
		return summaries(parties, memberId);
	}

	// 링크를 연 순간 보여 줄 파티. 로그인한 누구나 본다(크루원 확인은 합류 때, 시안 09). 토큰이 없거나 옛 토큰이면 404
	public PartyPreviewDto preview(String joinToken, LocalDateTime now) {
		Party party = partyRepository.findByJoinToken(joinToken)
			.orElseThrow(() -> new PartyNotFoundException("참가 링크를 찾을 수 없습니다. 파티장에게 새 링크를 받아주세요."));
		Rows rows = load(List.of(party));
		String leaderNickname = nickname(memberNicknamesPort.findNicknames(List.of(party.getLeaderMemberId())),
			party.getLeaderMemberId());
		return new PartyPreviewDto(party.getId(), party.getCrewId(), party.getRaceId(), party.getRaceCourseId(),
			party.getStatus(), leaderNickname, rows.members(party.getId()).size(), party.getJoinTokenExpiresAt(),
			now.isBefore(party.getJoinTokenExpiresAt()));
	}

	// 그 크루의 크루원만 본다. 크루가 없으면 404, 크루원이 아니면 403
	public List<PartySummaryDto> findActiveInCrew(long crewId, long memberId) {
		CrewMembership membership = crewPort.findMembership(crewId, memberId)
			.orElseThrow(() -> new CrewNotFoundException("존재하지 않는 크루입니다."));
		if (!membership.crewMember()) {
			throw PartyAccessDeniedException.crewMemberRequired();
		}
		return summaries(partyRepository.findByCrewIdAndStatusInOrderByIdDesc(crewId, ACTIVE_STATUSES), memberId);
	}

	private List<PartySummaryDto> summaries(List<Party> parties, long memberId) {
		if (parties.isEmpty()) {
			return List.of();
		}
		Rows rows = load(parties);
		Map<Long, String> nicknames = memberNicknamesPort.findNicknames(
			parties.stream().map(Party::getLeaderMemberId).toList());
		return parties.stream().map(party -> {
			List<PartyMember> members = rows.members(party.getId());
			return new PartySummaryDto(party.getId(), party.getCrewId(), party.getRaceId(),
				party.getRaceCourseId(), party.getStatus(), party.getLeaderMemberId(),
				nickname(nicknames, party.getLeaderMemberId()), members.size(), rows.paidCount(party.getId()),
				party.getPaymentDueAt(), members.stream().anyMatch(member -> member.getMemberId() == memberId));
		}).toList();
	}

	private static String nickname(Map<Long, String> nicknames, long memberId) {
		return nicknames.getOrDefault(memberId, MemberNicknamesPort.WITHDRAWN_NICKNAME);
	}

	private Rows load(Collection<Party> parties) {
		List<Long> partyIds = parties.stream().map(Party::getId).toList();
		Map<Long, List<PartyMember>> members = partyMemberRepository
			.findByPartyIdInAndLeftAtIsNullOrderByJoinedAtAscIdAsc(partyIds).stream()
			.collect(Collectors.groupingBy(PartyMember::getPartyId));
		// 나갔다 다시 합류하면 한 사람에 신청서가 여러 장이다. 가장 최근 것이 지금 신청서다
		Map<String, Participation> latest = participationRepository.findByPartyIdIn(partyIds).stream()
			.collect(Collectors.toMap(p -> key(p.getPartyId(), p.getMemberId()), Function.identity(),
				(a, b) -> a.getId() > b.getId() ? a : b));
		return new Rows(members, latest);
	}

	private static String key(long partyId, long memberId) {
		return partyId + ":" + memberId;
	}

	private record Rows(Map<Long, List<PartyMember>> membersByParty, Map<String, Participation> latest) {
		List<PartyMember> members(long partyId) {
			return membersByParty.getOrDefault(partyId, List.of());
		}

		ParticipationStatus latestStatus(long partyId, long memberId) {
			Participation participation = latest.get(key(partyId, memberId));
			return participation == null ? null : participation.getStatus();
		}

		// 지금 파티원 중 결제한 사람. 나간 사람의 옛 신청서는 세지 않는다
		int paidCount(long partyId) {
			return (int)members(partyId).stream()
				.filter(member -> latestStatus(partyId, member.getMemberId()) == ParticipationStatus.CONFIRMED)
				.count();
		}
	}
}
