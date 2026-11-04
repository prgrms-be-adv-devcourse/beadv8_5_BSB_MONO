package com.bukang.member.crew.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.crew.event.CrewMemberLeftEvent;
import com.bukang.common.shared.crew.event.CrewMemberLeftReason;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.crew.out.CrewInviteRepository;
import com.bukang.member.crew.out.CrewMemberRepository;
import com.bukang.member.crew.out.CrewRepository;
import com.bukang.member.domain.MemberOutboxEvent;
import com.bukang.member.domain.MemberOutboxStatus;
import com.bukang.member.domain.exception.WithdrawCrewLeaderException;
import com.bukang.member.out.MemberOutboxEventRepository;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.RefreshTokenRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.cash.CashBalancePort;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.out.order.ActiveTicketPort;
import com.bukang.member.support.MemberFixture;

/**
 * 크루원 행이 지워지는 네 경우(떠나기, 내보내기, 없애기, 회원 탈퇴)에 같은 트랜잭션에서 크루원 이탈 아웃박스 행이 남는지 확인한다 (BUK-73, BE-032)
 * - 크루원 행이 실제로 지워진 만큼만 남고, 요청이 실패하거나 롤백되면 남지 않는다
 * - 같은 크루원을 두 요청이 동시에 지워도 한 줄만 남는다
 * - 본문에는 이벤트 ID, 크루 ID, 회원 ID, 이유, 크루 가입 시각, 지운 시각만 있다 (개인정보 없음)
 * - 크루 가입 시각은 지운 크루원 행의 값이다. 받는 쪽이 늦게 온 이벤트가 어느 가입에 대한 것인지 가린다
 * 예치금·주문 서비스와 Redis는 띄우지 않으므로 탈퇴 확인 포트와 Refresh Token 저장소를 목으로 바꾼다
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(InMemoryIdentityVerificationStore.Config.class)
class CrewMemberLeftOutboxTest {
	private static final Instant JOINED = Instant.parse("2026-11-01T03:00:00Z");
	private static final Instant NOW = Instant.parse("2026-11-04T03:00:00Z");

	@Autowired
	private InMemoryIdentityVerificationStore identityStore;
	@Autowired
	private MemberFacade memberFacade;
	@Autowired
	private CrewFacade crewFacade;
	@Autowired
	private MemberOutboxEventRepository memberOutboxEventRepository;
	@Autowired
	private MemberRepository memberRepository;
	@Autowired
	private TermsAgreementRepository termsAgreementRepository;
	@Autowired
	private CrewRepository crewRepository;
	@Autowired
	private CrewMemberRepository crewMemberRepository;
	@Autowired
	private CrewInviteRepository crewInviteRepository;
	@Autowired
	private JsonConverter jsonConverter;

	@MockitoBean
	private CashBalancePort cashBalancePort;
	@MockitoBean
	private ActiveTicketPort activeTicketPort;
	@MockitoBean
	private RefreshTokenRepository refreshTokenRepository;
	@MockitoBean
	private Clock clock;

	private long leaderId;
	private long memberId;
	private long crewId;

	// 크루장과 크루원이 있는 크루 하나. 가입은 JOINED에 하고, 그 뒤 요청은 NOW에 한다
	@BeforeEach
	void setUp() {
		given(clock.instant()).willReturn(JOINED);
		leaderId = joinMember();
		memberId = joinMember();
		crewId = crewFacade.createCrew(leaderId, "새벽한강크루").crewId();
		joinCrew(crewId, leaderId, memberId);
		// 가입 이벤트 행은 이 테스트가 보는 것이 아니므로 비우고 시작한다
		memberOutboxEventRepository.deleteAll();
		given(clock.instant()).willReturn(NOW);
	}

	@AfterEach
	void tearDown() {
		memberOutboxEventRepository.deleteAll();
		// 초대·크루원이 크루를 참조하므로(FK) 크루보다 먼저 지운다
		crewInviteRepository.deleteAll();
		crewMemberRepository.deleteAll();
		crewRepository.deleteAll();
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("크루원이 떠나면 크루원 이탈 토픽 행이 보낼 차례로 하나 남고, 대상은 크루, 이유는 LEFT, 시각은 지운 시각이다")
	void leaveAppendsRow() {
		crewFacade.leave(memberId, crewId);

		List<MemberOutboxEvent> rows = crewLeftRows();
		assertThat(rows).hasSize(1);
		MemberOutboxEvent row = rows.get(0);
		CrewMemberLeftEvent body = bodyOf(row);
		assertThat(row.getStatus()).isEqualTo(MemberOutboxStatus.PENDING);
		assertThat(row.getRefType()).isEqualTo("Crew");
		assertThat(row.getRefId()).isEqualTo(crewId);
		assertThat(body.eventId()).isEqualTo(row.getEventId());
		assertThat(body.crewId()).isEqualTo(crewId);
		assertThat(body.memberId()).isEqualTo(memberId);
		assertThat(body.reason()).isEqualTo(CrewMemberLeftReason.LEFT);
		assertThat(body.joinedAt()).isEqualTo(at(JOINED));
		assertThat(body.leftAt()).isEqualTo(at(NOW));
	}

	@Test
	@DisplayName("본문에는 이벤트 ID, 크루 ID, 회원 ID, 이유, 가입 시각, 지운 시각만 있고 이메일·이름·휴대폰은 없다")
	void bodyHasNoPersonalData() {
		crewFacade.leave(memberId, crewId);

		Map<?, ?> body = jsonConverter.fromJson(crewLeftRows().get(0).getPayload(), Map.class);
		assertThat(body.keySet().stream().map(String::valueOf).toList())
			.containsExactlyInAnyOrder("eventId", "crewId", "memberId", "reason", "joinedAt", "leftAt");
	}

	@Test
	@DisplayName("크루장이 내보내면 내보낸 크루원의 행이 이유 REMOVED, 그 크루원의 가입 시각으로 하나 남는다")
	void removeAppendsRow() {
		crewFacade.removeMember(leaderId, crewId, memberId);

		List<MemberOutboxEvent> rows = crewLeftRows();
		assertThat(rows).hasSize(1);
		CrewMemberLeftEvent body = bodyOf(rows.get(0));
		assertThat(body.memberId()).isEqualTo(memberId);
		assertThat(body.reason()).isEqualTo(CrewMemberLeftReason.REMOVED);
		assertThat(body.joinedAt()).isEqualTo(at(JOINED));
	}

	@Test
	@DisplayName("크루장 혼자 남은 크루를 없애면 크루장의 행이 이유 DISBANDED, 크루장의 가입 시각으로 하나 남는다")
	void disbandAppendsLeaderRow() {
		crewFacade.removeMember(leaderId, crewId, memberId);
		memberOutboxEventRepository.deleteAll();

		crewFacade.disband(leaderId, crewId);

		List<MemberOutboxEvent> rows = crewLeftRows();
		assertThat(rows).hasSize(1);
		CrewMemberLeftEvent body = bodyOf(rows.get(0));
		assertThat(body.crewId()).isEqualTo(crewId);
		assertThat(body.memberId()).isEqualTo(leaderId);
		assertThat(body.reason()).isEqualTo(CrewMemberLeftReason.DISBANDED);
		assertThat(body.joinedAt()).isEqualTo(at(JOINED));
	}

	@Test
	@DisplayName("두 크루의 크루원이 회원 탈퇴하면 크루마다 이유 MEMBER_WITHDRAWN, 그 크루의 가입 시각으로 행이 하나씩 남는다")
	void withdrawAppendsRowPerCrew() {
		Instant otherJoined = Instant.parse("2026-11-02T03:00:00Z");
		given(clock.instant()).willReturn(otherJoined);
		long otherCrewId = crewFacade.createCrew(leaderId, "저녁한강크루").crewId();
		joinCrew(otherCrewId, leaderId, memberId);
		memberOutboxEventRepository.deleteAll();
		given(clock.instant()).willReturn(NOW);

		memberFacade.withdrawMe(memberId);

		assertThat(crewMemberRepository.existsByCrewIdAndMemberId(crewId, memberId)).isFalse();
		assertThat(crewMemberRepository.existsByCrewIdAndMemberId(otherCrewId, memberId)).isFalse();
		List<CrewMemberLeftEvent> bodies = crewLeftRows().stream().map(this::bodyOf).toList();
		assertThat(bodies).extracting(CrewMemberLeftEvent::crewId).containsExactlyInAnyOrder(crewId, otherCrewId);
		assertThat(bodies).allSatisfy(body -> {
			assertThat(body.memberId()).isEqualTo(memberId);
			assertThat(body.reason()).isEqualTo(CrewMemberLeftReason.MEMBER_WITHDRAWN);
			assertThat(body.leftAt()).isNotNull();
		});
		assertThat(bodies).filteredOn(body -> body.crewId() == crewId)
			.extracting(CrewMemberLeftEvent::joinedAt).containsExactly(at(JOINED));
		assertThat(bodies).filteredOn(body -> body.crewId() == otherCrewId)
			.extracting(CrewMemberLeftEvent::joinedAt).containsExactly(at(otherJoined));
	}

	@Test
	@DisplayName("크루장인 회원이 탈퇴하려 하면 409로 막히고 크루원 이탈 행이 남지 않는다")
	void leaderWithdrawRejectedAppendsNothing() {
		assertThatThrownBy(() -> memberFacade.withdrawMe(leaderId)).isInstanceOf(WithdrawCrewLeaderException.class);

		assertThat(crewLeftRows()).isEmpty();
	}

	@Test
	@DisplayName("크루에 없는 회원이 탈퇴하면 크루원 이탈 행이 남지 않는다")
	void withdrawWithoutCrewAppendsNothing() {
		long outsiderId = joinMember();
		memberOutboxEventRepository.deleteAll();

		memberFacade.withdrawMe(outsiderId);

		assertThat(crewLeftRows()).isEmpty();
	}

	@Test
	@DisplayName("회원 탈퇴가 뒤에서 실패해 롤백되면 크루원 행도 남고 크루원 이탈 행도 남지 않는다")
	void withdrawRollbackLeavesNoRow() {
		willThrow(new IllegalStateException("Redis 연결 실패")).given(refreshTokenRepository).deleteAll(anyLong());

		assertThatThrownBy(() -> memberFacade.withdrawMe(memberId)).isInstanceOf(IllegalStateException.class);

		assertThat(crewMemberRepository.existsByCrewIdAndMemberId(crewId, memberId)).isTrue();
		assertThat(crewLeftRows()).isEmpty();
	}

	@Test
	@DisplayName("크루장이 떠나려 하거나, 없는 크루원을 내보내거나, 다른 크루원이 있는데 없애려 하면 행이 남지 않는다")
	void failedRequestsAppendNothing() {
		assertThatThrownBy(() -> crewFacade.leave(leaderId, crewId)).isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> crewFacade.removeMember(leaderId, crewId, memberId + 999))
			.isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> crewFacade.disband(leaderId, crewId)).isInstanceOf(RuntimeException.class);

		assertThat(crewLeftRows()).isEmpty();
	}

	@Test
	@DisplayName("내보내기와 그 크루원의 회원 탈퇴가 동시에 와도 그 크루원의 행은 하나만 남는다")
	void concurrentRemoveAndWithdrawAppendsOnce() throws Exception {
		atOnce(
			() -> crewFacade.removeMember(leaderId, crewId, memberId),
			() -> memberFacade.withdrawMe(memberId));

		assertThat(crewMemberRepository.existsByCrewIdAndMemberId(crewId, memberId)).isFalse();
		assertThat(crewLeftRows()).hasSize(1);
	}

	@Test
	@DisplayName("떠나기와 내보내기가 동시에 와도 그 크루원의 행은 하나만 남는다")
	void concurrentLeaveAndRemoveAppendsOnce() throws Exception {
		atOnce(
			() -> crewFacade.leave(memberId, crewId),
			() -> crewFacade.removeMember(leaderId, crewId, memberId));

		assertThat(crewLeftRows()).hasSize(1);
	}

	private LocalDateTime at(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
	}

	private long joinMember() {
		return memberFacade.join(MemberFixture.joinRequest().build(identityStore)).getId();
	}

	// 초대를 만들어 그 링크로 가입시킨다
	private void joinCrew(long targetCrewId, long senderId, long joinerId) {
		crewFacade.join(joinerId, crewFacade.createInvite(senderId, targetCrewId).inviteToken());
	}

	private List<MemberOutboxEvent> crewLeftRows() {
		return memberOutboxEventRepository.findAll().stream()
			.filter(row -> row.getTopic().equals(KafkaTopics.CREW_MEMBER_LEFT))
			.sorted(Comparator.comparing(MemberOutboxEvent::getId))
			.toList();
	}

	private CrewMemberLeftEvent bodyOf(MemberOutboxEvent row) {
		return jsonConverter.fromJson(row.getPayload(), CrewMemberLeftEvent.class);
	}

	// 두 작업을 동시에 출발시킨다. 한쪽이 진 쪽 예외(이미 지워짐 등)를 내도 괜찮다
	private void atOnce(Runnable first, Runnable second) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<?>> results = new ArrayList<>();
			for (Runnable task : List.of(first, second)) {
				Callable<Void> call = () -> {
					start.await();
					try {
						task.run();
					} catch (RuntimeException ignored) {
						// 진 쪽은 404·403 같은 예외를 낸다. 결과는 남은 행으로 확인한다
					}
					return null;
				};
				results.add(executor.submit(call));
			}
			start.countDown();
			for (Future<?> result : results) {
				result.get(10, TimeUnit.SECONDS);
			}
		} finally {
			executor.shutdownNow();
		}
	}
}
