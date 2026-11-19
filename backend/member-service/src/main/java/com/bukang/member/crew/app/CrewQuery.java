package com.bukang.member.crew.app;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.member.crew.domain.Crew;
import com.bukang.member.crew.domain.CrewInvite;
import com.bukang.member.crew.domain.CrewMember;
import com.bukang.member.crew.domain.CrewRole;
import com.bukang.member.crew.domain.CrewStatus;
import com.bukang.member.crew.domain.exception.CrewNotMemberException;
import com.bukang.member.crew.dto.CrewHomeDto;
import com.bukang.member.crew.dto.CrewInviteDto;
import com.bukang.member.crew.dto.CrewInvitePreviewDto;
import com.bukang.member.crew.dto.CrewMemberCountDto;
import com.bukang.member.crew.dto.CrewMemberStatusDto;
import com.bukang.member.crew.dto.CrewPenaltyDto;
import com.bukang.member.crew.dto.MyCrewDto;
import com.bukang.member.crew.out.CrewInviteRepository;
import com.bukang.member.crew.out.CrewMemberProfilePort;
import com.bukang.member.crew.out.CrewMemberRepository;
import com.bukang.member.crew.out.CrewPenaltyRepository;
import com.bukang.member.crew.out.CrewRepository;

import lombok.RequiredArgsConstructor;

/**
 * 크루 조회: 내 크루 목록, 크루 홈, 초대 목록·미리보기, 내부 API (BE-027~030, BE-032)
 * 데이터를 바꾸지 않는다
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CrewQuery {
	private final CrewLoader loader;
	private final CrewRepository crewRepository;
	private final CrewMemberRepository crewMemberRepository;
	private final CrewInviteRepository crewInviteRepository;
	private final CrewPenaltyRepository crewPenaltyRepository;
	private final CrewMemberProfilePort profilePort;

	public List<MyCrewDto> findMyCrews(long memberId) {
		List<CrewMember> memberships = crewMemberRepository.findAllByMemberIdOrderByJoinedAtAscIdAsc(memberId);
		if (memberships.isEmpty()) {
			return List.of();
		}
		List<Long> crewIds = memberships.stream().map(CrewMember::getCrewId).toList();
		Map<Long, Crew> crews = new HashMap<>();
		crewRepository.findAllByIdInAndStatus(crewIds, CrewStatus.ACTIVE)
			.forEach(crew -> crews.put(crew.getId(), crew));
		Map<Long, Integer> counts = countMembers(crewIds);
		return memberships.stream()
			.filter(membership -> crews.containsKey(membership.getCrewId()))
			.map(membership -> MyCrewDto.of(membership, crews.get(membership.getCrewId()),
				counts.getOrDefault(membership.getCrewId(), 0)))
			.toList();
	}

	public CrewHomeDto findCrew(long memberId, long crewId) {
		Crew crew = loader.findActiveCrew(crewId);
		loader.findMembership(crewId, memberId);
		return homeOf(crew, memberId);
	}

	// 카카오톡 초대 (kakao-crew-invite): 초대 링크 만들기와 같은 검사(크루 있음 → 크루원 → 초대 권한)를 하고 크루 이름을 돌려준다
	public String requireInviter(long memberId, long crewId) {
		Crew crew = loader.findActiveCrew(crewId);
		loader.requireCanInvite(loader.findMembership(crewId, memberId));
		return crew.getName();
	}

	public List<CrewInviteDto> findInvites(long memberId, long crewId) {
		loader.findActiveCrew(crewId);
		CrewMember membership = loader.findMembership(crewId, memberId);
		loader.requireCanInvite(membership);

		LocalDateTime now = loader.now();
		// 크루장은 크루의 모든 초대, 운영진은 자기가 보낸 초대만
		List<CrewInvite> invites = membership.getRole().seesAllInvites()
			? crewInviteRepository.findUsableByCrewId(crewId, now)
			: crewInviteRepository.findUsableByCrewIdAndCreatedBy(crewId, memberId, now);
		Map<Long, String> nicknames = profilePort.findNicknames(
			invites.stream().map(CrewInvite::getCreatedBy).distinct().toList());
		return invites.stream()
			.map(invite -> CrewInviteDto.of(invite, nicknames.get(invite.getCreatedBy())))
			.toList();
	}

	public CrewInvitePreviewDto previewInvite(long memberId, String inviteToken) {
		CrewInvite invite = loader.findInviteByToken(inviteToken);
		Crew crew = loader.findCrewOfInvite(invite);
		// 이미 크루원이면 링크를 쓸 수 없어도 200으로 답해 화면이 크루 홈으로 보내게 한다
		// (자기가 가입에 쓴 링크를 다시 열어도 「이미 쓰인 링크」 대신 크루 홈으로 간다)
		boolean alreadyMember = crewMemberRepository.existsByCrewIdAndMemberId(crew.getId(), memberId);
		if (!alreadyMember) {
			loader.throwIfUnusableWhenOpeningLink(invite, loader.now());
			loader.throwIfRemovedAfterInvite(invite, memberId);
		}
		return CrewInvitePreviewDto.of(crew, (int)crewMemberRepository.countByCrewId(crew.getId()), invite,
			alreadyMember);
	}

	// 내부 API: 크루원이 아니어도 200이다. 크루가 없거나 없앤 크루면 404
	public CrewMemberStatusDto findMemberStatus(long crewId, long memberId) {
		loader.findActiveCrew(crewId);
		return crewMemberRepository.findByCrewIdAndMemberId(crewId, memberId)
			.map(CrewMemberStatusDto::of)
			.orElseGet(() -> CrewMemberStatusDto.notMember(crewId, memberId));
	}

	// 내부 API: 크루장, 운영진, 크루원을 모두 센다
	public CrewMemberCountDto findMemberCount(long crewId) {
		Crew crew = loader.findActiveCrew(crewId);
		return CrewMemberCountDto.of(crew, (int)crewMemberRepository.countByCrewId(crewId));
	}

	// 주문 서비스가 파티를 만들 때 묻는다. 크루가 없어도 제한 기록이 없으니 restricted=false다 (BUK-187)
	public CrewPenaltyDto findPenalty(long crewId) {
		return CrewPenaltyDto.of(crewPenaltyRepository.findRestrictedUntil(crewId, loader.now()));
	}

	// 크루 만들기·이름 바꾸기·크루장 넘기기의 응답도 이 메서드로 만든다 (그 쓰기 트랜잭션 안에서 부른다)
	public CrewHomeDto homeOf(Crew crew, long memberId) {
		List<CrewMember> members = crewMemberRepository.findAllByCrewId(crew.getId()).stream()
			.sorted(CrewMember.HOME_ORDER)
			.toList();
		Map<Long, String> nicknames = profilePort.findNicknames(members.stream().map(CrewMember::getMemberId).toList());
		CrewRole myRole = members.stream()
			.filter(member -> member.getMemberId() == memberId)
			.findFirst()
			.map(CrewMember::getRole)
			.orElseThrow(CrewNotMemberException::new);
		return CrewHomeDto.of(crew, myRole, members, nicknames,
			crewPenaltyRepository.findRestrictedUntil(crew.getId(), loader.now()));
	}

	private Map<Long, Integer> countMembers(List<Long> crewIds) {
		Map<Long, Integer> counts = new HashMap<>();
		for (Object[] row : crewMemberRepository.countByCrewIds(Set.copyOf(crewIds))) {
			counts.put((Long)row[0], ((Long)row[1]).intValue());
		}
		return counts;
	}
}
