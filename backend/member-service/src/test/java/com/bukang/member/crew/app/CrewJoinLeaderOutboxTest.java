package com.bukang.member.crew.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

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
import com.bukang.common.shared.crew.event.CrewLeaderChangedEvent;
import com.bukang.common.shared.crew.event.CrewMemberJoinedEvent;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.crew.domain.exception.CrewAlreadyMemberException;
import com.bukang.member.crew.out.CrewInviteRepository;
import com.bukang.member.crew.out.CrewMemberRepository;
import com.bukang.member.crew.out.CrewRemovalRepository;
import com.bukang.member.crew.out.CrewRepository;
import com.bukang.member.domain.MemberOutboxEvent;
import com.bukang.member.domain.MemberOutboxStatus;
import com.bukang.member.out.MemberOutboxEventRepository;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.RefreshTokenRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.cash.CashBalancePort;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.out.order.ActiveTicketPort;
import com.bukang.member.support.MemberFixture;

/**
 * 크루 가입과 크루장 변경이 같은 트랜잭션에서 아웃박스 행을 남기는지 확인한다 (BUK-99)
 * - 가입·크루장 변경마다 한 줄만 남고, 요청이 실패하거나 롤백되면 남지 않는다
 * - 동시에 같은 요청이 와도 성공한 만큼만 남는다
 * - Kafka key는 크루 ID이고, 본문에는 ID와 시각만 있다 (개인정보 없음). 받는 쪽(알림 서비스)이 읽을 이름을 고정한다
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(InMemoryIdentityVerificationStore.Config.class)
class CrewJoinLeaderOutboxTest {
	private static final Instant CREATED = Instant.parse("2026-11-01T03:00:00Z");
	private static final Instant NOW = Instant.parse("2026-11-04T03:00:00Z");
	// 토픽 규칙: <message-type>.<dataset>.<data>, 한 단어씩 소문자, '_'·'-' 없음
	private static final String TOPIC_FORMAT = "[a-z]+\\.[a-z]+\\.[a-z]+";

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
	private CrewRemovalRepository crewRemovalRepository;
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
	private long joinerId;
	private long crewId;
	private String inviteToken;

	// 크루장과 크루원이 있는 크루 하나, 아직 가입하지 않은 회원 하나, 크루장이 만든 초대 링크 하나. 그 뒤 요청은 NOW에 한다
	@BeforeEach
	void setUp() {
		given(clock.instant()).willReturn(CREATED);
		leaderId = joinMember();
		memberId = joinMember();
		joinerId = joinMember();
		crewId = crewFacade.createCrew(leaderId, "새벽한강크루").crewId();
		crewFacade.join(memberId, crewFacade.createInvite(leaderId, crewId).inviteToken());
		inviteToken = crewFacade.createInvite(leaderId, crewId).inviteToken();
		memberOutboxEventRepository.deleteAll();
		given(clock.instant()).willReturn(NOW);
	}

	@AfterEach
	void tearDown() {
		memberOutboxEventRepository.deleteAll();
		// 초대·크루원·내보낸 기록이 크루를 참조하므로(FK) 크루보다 먼저 지운다
		crewInviteRepository.deleteAll();
		crewRemovalRepository.deleteAll();
		crewMemberRepository.deleteAll();
		crewRepository.deleteAll();
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("토픽 이름은 규칙 형식이고 크루 토픽끼리 겹치지 않는다")
	void topicNamesFollowConvention() {
		assertThat(List.of(KafkaTopics.CREW_MEMBER_JOINED, KafkaTopics.CREW_LEADER_CHANGED))
			.allSatisfy(topic -> assertThat(topic).matches(TOPIC_FORMAT).startsWith("queuing.crew."))
			.doesNotContain(KafkaTopics.CREW_MEMBER_LEFT)
			.doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("초대 링크로 가입하면 크루 가입 행이 보낼 차례로 하나 남고, 대상은 크루, 본문은 크루·회원·가입 시각이다")
	void joinAppendsRow() {
		crewFacade.join(joinerId, inviteToken);

		List<MemberOutboxEvent> rows = rows(KafkaTopics.CREW_MEMBER_JOINED);
		assertThat(rows).hasSize(1);
		MemberOutboxEvent row = rows.get(0);
		CrewMemberJoinedEvent body = jsonConverter.fromJson(row.getPayload(), CrewMemberJoinedEvent.class);
		assertThat(row.getStatus()).isEqualTo(MemberOutboxStatus.PENDING);
		assertThat(row.getRefType()).isEqualTo("Crew");
		assertThat(row.getRefId()).isEqualTo(crewId);
		assertThat(body.eventId()).isEqualTo(row.getEventId());
		assertThat(body.crewId()).isEqualTo(crewId);
		assertThat(body.memberId()).isEqualTo(joinerId);
		assertThat(body.joinedAt()).isEqualTo(at(NOW));
	}

	@Test
	@DisplayName("크루 가입 본문에는 이벤트 ID, 크루 ID, 회원 ID, 가입 시각만 있다")
	void joinBodyKeys() {
		crewFacade.join(joinerId, inviteToken);

		assertThat(keysOf(rows(KafkaTopics.CREW_MEMBER_JOINED).get(0)))
			.containsExactlyInAnyOrder("eventId", "crewId", "memberId", "joinedAt");
	}

	@Test
	@DisplayName("이미 크루원이 같은 링크로 다시 가입하면 409로 롤백되고 가입 행이 남지 않는다")
	void alreadyMemberJoinAppendsNothing() {
		assertThatThrownBy(() -> crewFacade.join(memberId, inviteToken))
			.isInstanceOf(CrewAlreadyMemberException.class);

		assertThat(rows(KafkaTopics.CREW_MEMBER_JOINED)).isEmpty();
	}

	@Test
	@DisplayName("없는 링크나 내보낸 뒤 옛 링크로 가입하려 하면 가입 행이 남지 않는다")
	void failedJoinAppendsNothing() {
		assertThatThrownBy(() -> crewFacade.join(joinerId, "없는-토큰")).isInstanceOf(RuntimeException.class);
		crewFacade.removeMember(leaderId, crewId, memberId);

		assertThatThrownBy(() -> crewFacade.join(memberId, inviteToken)).isInstanceOf(RuntimeException.class);

		assertThat(rows(KafkaTopics.CREW_MEMBER_JOINED)).isEmpty();
	}

	@Test
	@DisplayName("같은 회원이 같은 링크로 동시에 두 번 가입해도 가입 행은 하나만 남는다")
	void concurrentJoinAppendsOnce() throws Exception {
		atOnce(
			() -> crewFacade.join(joinerId, inviteToken),
			() -> crewFacade.join(joinerId, inviteToken));

		assertThat(crewMemberRepository.existsByCrewIdAndMemberId(crewId, joinerId)).isTrue();
		assertThat(rows(KafkaTopics.CREW_MEMBER_JOINED)).hasSize(1);
	}

	@Test
	@DisplayName("크루장을 넘기면 크루장 변경 행이 하나 남고, 본문은 크루·이전 크루장·새 크루장·바꾼 시각이다")
	void transferAppendsRow() {
		crewFacade.transferLeader(leaderId, crewId, memberId);

		List<MemberOutboxEvent> rows = rows(KafkaTopics.CREW_LEADER_CHANGED);
		assertThat(rows).hasSize(1);
		MemberOutboxEvent row = rows.get(0);
		CrewLeaderChangedEvent body = jsonConverter.fromJson(row.getPayload(), CrewLeaderChangedEvent.class);
		assertThat(row.getStatus()).isEqualTo(MemberOutboxStatus.PENDING);
		assertThat(row.getRefType()).isEqualTo("Crew");
		assertThat(row.getRefId()).isEqualTo(crewId);
		assertThat(body.eventId()).isEqualTo(row.getEventId());
		assertThat(body.crewId()).isEqualTo(crewId);
		assertThat(body.previousLeaderId()).isEqualTo(leaderId);
		assertThat(body.newLeaderId()).isEqualTo(memberId);
		assertThat(body.changedAt()).isEqualTo(at(NOW));
	}

	@Test
	@DisplayName("크루장 변경 본문에는 이벤트 ID, 크루 ID, 이전 크루장, 새 크루장, 바꾼 시각만 있다")
	void transferBodyKeys() {
		crewFacade.transferLeader(leaderId, crewId, memberId);

		assertThat(keysOf(rows(KafkaTopics.CREW_LEADER_CHANGED).get(0)))
			.containsExactlyInAnyOrder("eventId", "crewId", "previousLeaderId", "newLeaderId", "changedAt");
	}

	@Test
	@DisplayName("크루장 넘기기가 실패하면(자기에게, 크루장이 아닌 사람이, 크루원이 아닌 사람에게) 크루장 변경 행이 남지 않는다")
	void failedTransferAppendsNothing() {
		assertThatThrownBy(() -> crewFacade.transferLeader(leaderId, crewId, leaderId))
			.isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> crewFacade.transferLeader(memberId, crewId, leaderId))
			.isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> crewFacade.transferLeader(leaderId, crewId, joinerId))
			.isInstanceOf(RuntimeException.class);

		assertThat(rows(KafkaTopics.CREW_LEADER_CHANGED)).isEmpty();
	}

	@Test
	@DisplayName("크루장이 두 사람에게 동시에 넘겨도 크루장 변경 행은 하나만 남는다")
	void concurrentTransferAppendsOnce() throws Exception {
		crewFacade.join(joinerId, inviteToken);
		memberOutboxEventRepository.deleteAll();

		atOnce(
			() -> crewFacade.transferLeader(leaderId, crewId, memberId),
			() -> crewFacade.transferLeader(leaderId, crewId, joinerId));

		assertThat(rows(KafkaTopics.CREW_LEADER_CHANGED)).hasSize(1);
	}

	private LocalDateTime at(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
	}

	private long joinMember() {
		return memberFacade.join(MemberFixture.joinRequest().build(identityStore)).getId();
	}

	private List<MemberOutboxEvent> rows(String topic) {
		return memberOutboxEventRepository.findAll().stream()
			.filter(row -> row.getTopic().equals(topic))
			.sorted(Comparator.comparing(MemberOutboxEvent::getId))
			.toList();
	}

	private List<String> keysOf(MemberOutboxEvent row) {
		return jsonConverter.fromJson(row.getPayload(), Map.class).keySet().stream().map(String::valueOf).toList();
	}

	// 두 작업을 동시에 출발시킨다. 진 쪽 예외(이미 크루원, 크루장 아님 등)는 무시하고 남은 행으로 확인한다
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
						// 진 쪽은 409·403 같은 예외를 낸다
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
