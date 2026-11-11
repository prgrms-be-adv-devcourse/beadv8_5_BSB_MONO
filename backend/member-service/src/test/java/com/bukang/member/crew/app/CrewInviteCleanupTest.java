package com.bukang.member.crew.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.member.crew.domain.Crew;
import com.bukang.member.crew.domain.CrewInvite;
import com.bukang.member.crew.domain.CrewMember;
import com.bukang.member.crew.out.CrewInviteRepository;
import com.bukang.member.crew.out.CrewMemberRepository;
import com.bukang.member.crew.out.CrewRepository;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;

/**
 * 만료·취소된 크루 초대 정리(CrewInviteCleanupUseCase)를 확인한다 (BUK-100)
 * - 만료되거나 취소된 지 7일 지난 초대만 지운다
 * - 아직 쓸 수 있는 초대, 7일이 안 된 초대, 지금 크루원이 가리키는 초대, 옛 방식으로 한 번 쓰인 초대는 남긴다
 * - 한 번에 BATCH_SIZE건씩 나눠 지우고, 지울 행이 남지 않을 때까지 되풀이한다
 * 초대의 시각은 엔티티를 과거 시각으로 만들어 정한다
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(InMemoryIdentityVerificationStore.Config.class)
class CrewInviteCleanupTest {
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 4, 30);
	private static final long LEADER_ID = 1L;

	@Autowired
	private CrewInviteCleanupUseCase crewInviteCleanupUseCase;
	@Autowired
	private CrewRepository crewRepository;
	@Autowired
	private CrewMemberRepository crewMemberRepository;
	@Autowired
	private CrewInviteRepository crewInviteRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private TransactionTemplate transactionTemplate;

	private long crewId;

	@BeforeEach
	void setUp() {
		cleanAll();
		crewId = crewRepository.save(Crew.create("정리크루", NOW.minusDays(30))).getId();
	}

	@AfterEach
	void tearDown() {
		cleanAll();
	}

	@Test
	@DisplayName("만료된 지 7일 지난 초대는 지우고, 7일이 안 된 초대와 아직 쓸 수 있는 초대는 남긴다")
	void deletesOnlyInvitesExpiredOverSevenDaysAgo() {
		long expiredLongAgo = invite(expiresAt(NOW.minusDays(7).minusSeconds(1)));
		long expiredRecently = invite(expiresAt(NOW.minusDays(7).plusSeconds(1)));
		long usable = invite(NOW.minusHours(1));

		int deleted = crewInviteCleanupUseCase.cleanup(NOW);

		assertThat(deleted).isEqualTo(1);
		assertThat(remainingIds()).containsExactlyInAnyOrder(expiredRecently, usable);
		assertThat(remainingIds()).doesNotContain(expiredLongAgo);
	}

	@Test
	@DisplayName("취소된 지 7일 지난 초대는 지우고, 취소된 지 7일이 안 된 초대는 남긴다")
	void deletesOnlyInvitesCanceledOverSevenDaysAgo() {
		long canceledLongAgo = canceledInvite(NOW.minusDays(8));
		long canceledRecently = canceledInvite(NOW.minusDays(6));

		int deleted = crewInviteCleanupUseCase.cleanup(NOW);

		assertThat(deleted).isEqualTo(1);
		assertThat(remainingIds()).containsExactly(canceledRecently);
		assertThat(remainingIds()).doesNotContain(canceledLongAgo);
	}

	@Test
	@DisplayName("지금 크루원이 가입에 쓴 초대는 만료된 지 7일이 지나도 남기고, 크루원 행도 그대로 둔다")
	void keepsInviteReferencedByCrewMember() {
		long joinedInvite = invite(expiresAt(NOW.minusDays(30)));
		crewMemberRepository.save(CrewMember.invited(crewId, 2L, joinedInvite, NOW.minusDays(30).minusHours(1)));

		int deleted = crewInviteCleanupUseCase.cleanup(NOW);

		assertThat(deleted).isZero();
		assertThat(remainingIds()).containsExactly(joinedInvite);
		assertThat(crewMemberRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("여러 명 가입 전에 한 번 쓰고 닫힌 옛 초대는 만료된 지 7일이 지나도 남긴다")
	void keepsLegacyUsedInvite() {
		long legacyUsed = invite(expiresAt(NOW.minusDays(30)));
		jdbcTemplate.update("update member_crew_invite set used_at = ?, used_by = ? where invite_id = ?",
			NOW.minusDays(30).minusHours(1), 3L, legacyUsed);

		int deleted = crewInviteCleanupUseCase.cleanup(NOW);

		assertThat(deleted).isZero();
		assertThat(remainingIds()).containsExactly(legacyUsed);
	}

	@Test
	@DisplayName("지울 초대가 한 번에 지우는 건수보다 많아도 나눠서 모두 지운다")
	void deletesInChunksUntilDone() {
		int total = CrewInviteCleanupUseCase.BATCH_SIZE + 1;
		LocalDateTime createdAt = expiresAt(NOW.minusDays(8));
		List<CrewInvite> invites = new ArrayList<>();
		for (int i = 0; i < total; i++) {
			invites.add(CrewInvite.create(crewId, hash(), LEADER_ID, createdAt));
		}
		crewInviteRepository.saveAll(invites);

		int deleted = crewInviteCleanupUseCase.cleanup(NOW);

		assertThat(deleted).isEqualTo(total);
		assertThat(crewInviteRepository.count()).isZero();
	}

	// 끝나는 시각이 expiresAt이 되도록 만든 시각을 돌려준다
	private static LocalDateTime expiresAt(LocalDateTime expiresAt) {
		return expiresAt.minus(CrewInvite.VALID_DURATION);
	}

	private long invite(LocalDateTime createdAt) {
		return crewInviteRepository.save(CrewInvite.create(crewId, hash(), LEADER_ID, createdAt)).getId();
	}

	// canceledAt에 취소된 초대. 취소는 끝나기 전에만 되므로 취소 1시간 전에 만든다
	private long canceledInvite(LocalDateTime canceledAt) {
		long id = invite(canceledAt.minusHours(1));
		Integer canceled = transactionTemplate.execute(
			status -> crewInviteRepository.markCanceled(id, LEADER_ID, canceledAt));
		assertThat(canceled).isEqualTo(1);
		return id;
	}

	private static String hash() {
		return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
	}

	private List<Long> remainingIds() {
		return crewInviteRepository.findAll().stream().map(CrewInvite::getId).toList();
	}

	private void cleanAll() {
		crewMemberRepository.deleteAll();
		crewInviteRepository.deleteAll();
		crewRepository.deleteAll();
	}
}
