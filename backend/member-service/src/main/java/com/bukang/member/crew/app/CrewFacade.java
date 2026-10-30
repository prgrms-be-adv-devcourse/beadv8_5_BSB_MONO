package com.bukang.member.crew.app;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.member.crew.domain.Crew;
import com.bukang.member.crew.domain.CrewInvite;
import com.bukang.member.crew.domain.CrewMember;
import com.bukang.member.crew.domain.CrewRole;
import com.bukang.member.crew.domain.CrewStatus;
import com.bukang.member.crew.domain.exception.CrewAlreadyMemberException;
import com.bukang.member.crew.domain.exception.CrewInviteAlreadyUsedException;
import com.bukang.member.crew.domain.exception.CrewInviteCancelForbiddenException;
import com.bukang.member.crew.domain.exception.CrewInviteCanceledException;
import com.bukang.member.crew.domain.exception.CrewInviteExpiredException;
import com.bukang.member.crew.domain.exception.CrewInviteForbiddenException;
import com.bukang.member.crew.domain.exception.CrewInviteNotFoundException;
import com.bukang.member.crew.domain.exception.CrewNotFoundException;
import com.bukang.member.crew.domain.exception.CrewNotMemberException;
import com.bukang.member.crew.dto.CrewHomeDto;
import com.bukang.member.crew.dto.CrewInviteCreatedDto;
import com.bukang.member.crew.dto.CrewInviteDto;
import com.bukang.member.crew.dto.CrewInvitePreviewDto;
import com.bukang.member.crew.dto.CrewInviteSenderDto;
import com.bukang.member.crew.dto.CrewJoinedDto;
import com.bukang.member.crew.dto.CrewMemberCountDto;
import com.bukang.member.crew.dto.CrewMemberDto;
import com.bukang.member.crew.dto.CrewMemberStatusDto;
import com.bukang.member.crew.dto.MyCrewDto;
import com.bukang.member.crew.out.CrewInviteRepository;
import com.bukang.member.crew.out.CrewMemberProfilePort;
import com.bukang.member.crew.out.CrewMemberRepository;
import com.bukang.member.crew.out.CrewRepository;

import lombok.RequiredArgsConstructor;

/**
 * 크루 만들기·조회, 초대 링크 만들기·취소·가입 (BE-027~030)
 * 현재 시각은 Clock으로 읽어 테스트에서 시간을 바꿀 수 있게 한다
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CrewFacade {
	private final CrewRepository crewRepository;
	private final CrewMemberRepository crewMemberRepository;
	private final CrewInviteRepository crewInviteRepository;
	private final CrewMemberProfilePort profilePort;
	private final Clock clock;

	@Transactional
	public CrewHomeDto createCrew(long memberId, String name) {
		LocalDateTime now = now();
		Crew crew = crewRepository.save(Crew.create(name, now));
		// 만든 회원이 크루장이고, 가입 시각은 크루를 만든 시각과 같다
		crewMemberRepository.save(CrewMember.leader(crew.getId(), memberId, now));
		return home(crew, memberId);
	}

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
			.map(membership -> new MyCrewDto(
				membership.getCrewId(),
				crews.get(membership.getCrewId()).getName(),
				membership.getRole(),
				counts.getOrDefault(membership.getCrewId(), 0)))
			.toList();
	}

	public CrewHomeDto findCrew(long memberId, long crewId) {
		Crew crew = findActiveCrew(crewId);
		findMembership(crewId, memberId);
		return home(crew, memberId);
	}

	@Transactional
	public CrewInviteCreatedDto createInvite(long memberId, long crewId) {
		findActiveCrew(crewId);
		requireInviter(findMembership(crewId, memberId));

		LocalDateTime now = now();
		String token = CrewInviteToken.generate();
		CrewInvite invite = crewInviteRepository.save(
			CrewInvite.create(crewId, CrewInviteToken.hash(token), memberId, now));
		// 토큰 원문은 이 응답에만 담고 저장·로그 출력하지 않는다
		return new CrewInviteCreatedDto(invite.getId(), token, invite.getCreatedAt(), invite.getExpiresAt());
	}

	public List<CrewInviteDto> findInvites(long memberId, long crewId) {
		findActiveCrew(crewId);
		CrewMember membership = findMembership(crewId, memberId);
		requireInviter(membership);

		LocalDateTime now = now();
		// 크루장은 크루의 모든 초대, 운영진은 자기가 보낸 초대만
		List<CrewInvite> invites = membership.getRole() == CrewRole.LEADER
			? crewInviteRepository.findUsableByCrewId(crewId, now)
			: crewInviteRepository.findUsableByCrewIdAndCreatedBy(crewId, memberId, now);
		Map<Long, String> nicknames = profilePort.findNicknames(
			invites.stream().map(CrewInvite::getCreatedBy).distinct().toList());
		return invites.stream()
			.map(invite -> new CrewInviteDto(
				invite.getId(),
				new CrewInviteSenderDto(invite.getCreatedBy(), nicknames.get(invite.getCreatedBy())),
				invite.getCreatedAt(),
				invite.getExpiresAt()))
			.toList();
	}

	@Transactional
	public void cancelInvite(long memberId, long crewId, long inviteId) {
		findActiveCrew(crewId);
		CrewMember membership = findMembership(crewId, memberId);
		CrewInvite invite = crewInviteRepository.findByIdAndCrewId(inviteId, crewId)
			.orElseThrow(CrewInviteNotFoundException::new);
		// 보낸 사람이나 크루장만 취소한다
		if (invite.getCreatedBy() != memberId && membership.getRole() != CrewRole.LEADER) {
			throw new CrewInviteCancelForbiddenException();
		}
		// 가입과 동시에 일어나도 둘 중 하나만 성공하도록 조건을 건 UPDATE로 취소한다
		if (crewInviteRepository.markCanceled(inviteId, memberId, now()) == 0) {
			CrewInvite current = crewInviteRepository.findByIdForUpdate(inviteId)
				.orElseThrow(CrewInviteNotFoundException::new);
			throw unusableWhenCanceling(current);
		}
	}

	public CrewInvitePreviewDto findInviteByToken(long memberId, String inviteToken) {
		CrewInvite invite = findInviteByToken(inviteToken);
		Crew crew = crewRepository.findActiveById(invite.getCrewId())
			.orElseThrow(CrewInviteNotFoundException::new);
		// 이미 크루원이면 링크를 쓸 수 없어도 200으로 답해 화면이 크루 홈으로 보내게 한다
		// (자기가 가입에 쓴 링크를 다시 열어도 「이미 쓰인 링크」 대신 크루 홈으로 간다)
		boolean alreadyMember = crewMemberRepository.existsByCrewIdAndMemberId(crew.getId(), memberId);
		if (!alreadyMember) {
			throwIfUnusableWhenOpeningLink(invite, now());
		}
		return new CrewInvitePreviewDto(
			crew.getId(),
			crew.getName(),
			(int)crewMemberRepository.countByCrewId(crew.getId()),
			invite.getExpiresAt(),
			alreadyMember);
	}

	@Transactional
	public CrewJoinedDto join(long memberId, String inviteToken) {
		CrewInvite invite = findInviteByToken(inviteToken);
		Crew crew = crewRepository.findActiveById(invite.getCrewId())
			.orElseThrow(CrewInviteNotFoundException::new);
		// 이미 크루원이면 초대를 소모하지 않는다
		if (crewMemberRepository.existsByCrewIdAndMemberId(crew.getId(), memberId)) {
			throw new CrewAlreadyMemberException();
		}

		LocalDateTime now = now();
		// 바뀐 행이 1일 때만 가입한다. 같은 링크로 동시에 가입해도 한 명만 1을 받는다
		if (crewInviteRepository.markUsed(invite.getId(), memberId, now) == 0) {
			CrewInvite current = crewInviteRepository.findByIdForUpdate(invite.getId())
				.orElseThrow(CrewInviteNotFoundException::new);
			throwIfUnusableWhenOpeningLink(current, now);
			// 쓰임·취소·만료는 되돌릴 수 없으므로 여기에 오지 않는다
			throw new IllegalStateException("쓸 수 있는 초대인데 가입에 쓰지 못했습니다. inviteId=" + invite.getId());
		}
		try {
			CrewMember member = crewMemberRepository.saveAndFlush(
				CrewMember.invited(crew.getId(), memberId, invite.getId(), now));
			return new CrewJoinedDto(crew.getId(), crew.getName(), member.getRole(), member.getJoinedAt());
		} catch (DataIntegrityViolationException exception) {
			// 같은 회원의 동시 가입은 uk_crew_member가 막는다. 예외가 나가 트랜잭션이 롤백되므로 초대도 쓰이지 않은 채로 남는다
			throw new CrewAlreadyMemberException();
		}
	}

	// 내부 API: 크루원이 아니어도 200이다. 크루가 없거나 없앤 크루면 404
	public CrewMemberStatusDto findMemberStatus(long crewId, long memberId) {
		findActiveCrew(crewId);
		return crewMemberRepository.findByCrewIdAndMemberId(crewId, memberId)
			.map(member -> new CrewMemberStatusDto(crewId, memberId, true, member.getRole(), member.getJoinedAt()))
			.orElseGet(() -> new CrewMemberStatusDto(crewId, memberId, false, null, null));
	}

	// 내부 API: 크루장, 운영진, 크루원을 모두 센다
	public CrewMemberCountDto findMemberCount(long crewId) {
		findActiveCrew(crewId);
		return new CrewMemberCountDto(crewId, (int)crewMemberRepository.countByCrewId(crewId));
	}

	private CrewHomeDto home(Crew crew, long memberId) {
		List<CrewMember> members = crewMemberRepository.findAllByCrewId(crew.getId()).stream()
			// 크루장 → 운영진 → 크루원, 같은 역할은 먼저 가입한 순서
			.sorted(Comparator.comparing(CrewMember::getRole)
				.thenComparing(CrewMember::getJoinedAt)
				.thenComparing(CrewMember::getId))
			.toList();
		Map<Long, String> nicknames = profilePort.findNicknames(members.stream().map(CrewMember::getMemberId).toList());
		CrewRole myRole = members.stream()
			.filter(member -> member.getMemberId() == memberId)
			.findFirst()
			.map(CrewMember::getRole)
			.orElseThrow(CrewNotMemberException::new);
		return new CrewHomeDto(
			crew.getId(),
			crew.getName(),
			crew.getCreatedAt(),
			myRole,
			members.size(),
			members.stream()
				.map(member -> new CrewMemberDto(member.getMemberId(), nicknames.get(member.getMemberId()),
					member.getRole()))
				.toList());
	}

	private Map<Long, Integer> countMembers(List<Long> crewIds) {
		Map<Long, Integer> counts = new HashMap<>();
		for (Object[] row : crewMemberRepository.countByCrewIds(Set.copyOf(crewIds))) {
			counts.put((Long)row[0], ((Long)row[1]).intValue());
		}
		return counts;
	}

	private Crew findActiveCrew(long crewId) {
		return crewRepository.findActiveById(crewId).orElseThrow(CrewNotFoundException::new);
	}

	private CrewMember findMembership(long crewId, long memberId) {
		return crewMemberRepository.findByCrewIdAndMemberId(crewId, memberId)
			.orElseThrow(CrewNotMemberException::new);
	}

	private void requireInviter(CrewMember membership) {
		if (!membership.getRole().canInvite()) {
			throw new CrewInviteForbiddenException();
		}
	}

	private CrewInvite findInviteByToken(String inviteToken) {
		return crewInviteRepository.findByTokenHash(CrewInviteToken.hash(inviteToken))
			.orElseThrow(CrewInviteNotFoundException::new);
	}

	// 쓰임 → 취소 → 24시간 지남 순서로 본다
	private void throwIfUnusableWhenOpeningLink(CrewInvite invite, LocalDateTime now) {
		if (invite.getUsedAt() != null) {
			throw CrewInviteAlreadyUsedException.whenOpeningLink();
		}
		if (invite.getCanceledAt() != null) {
			throw CrewInviteCanceledException.whenOpeningLink();
		}
		if (!invite.getExpiresAt().isAfter(now)) {
			throw CrewInviteExpiredException.whenOpeningLink();
		}
	}

	private RuntimeException unusableWhenCanceling(CrewInvite invite) {
		if (invite.getUsedAt() != null) {
			return CrewInviteAlreadyUsedException.whenCanceling();
		}
		if (invite.getCanceledAt() != null) {
			return CrewInviteCanceledException.whenCanceling();
		}
		return CrewInviteExpiredException.whenCanceling();
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
	}
}
