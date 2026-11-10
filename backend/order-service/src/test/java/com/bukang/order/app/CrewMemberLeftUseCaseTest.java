package com.bukang.order.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.domain.Participation;
import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.domain.Party;
import com.bukang.order.domain.PartyMember;
import com.bukang.order.domain.PartyStatus;
import com.bukang.order.domain.exception.PartyConflictException;
import com.bukang.order.out.OrderOutboxKafkaEventRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.PartyMemberRepository;
import com.bukang.order.out.PartyRepository;
import com.bukang.order.support.StubCrewPort;
import com.bukang.order.support.StubCrewPortConfig;
import com.bukang.order.support.StubRaceCoursePort;
import com.bukang.order.support.StubRaceCoursePortConfig;
import com.jayway.jsonpath.JsonPath;

/**
 * 크루를 나간 사람을 파티에서 빼기 (BUK-77). 파티는 API로 만들고 합류시킨 뒤 유스케이스를 직접 부른다
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubCrewPortConfig.class})
class CrewMemberLeftUseCaseTest {
	private static final String BASE_URL = "/api/v1/order/parties";
	private static final long LEADER = 1;
	private static final long MEMBER = 2;
	private static final long OTHER = 3;
	private static final long CREW_ID = 7;
	private static final long OTHER_CREW_ID = 8;
	private static final long COURSE_ID = 10;
	private static final long RACE_ID = 100;
	// 파티원 줄의 크루 가입 시각과 같은 가입에 대한 떠남
	private static final LocalDateTime LEFT_JOINED_AT = StubCrewPort.JOINED_AT;
	private static final int CONCURRENT_ROUNDS = 5;
	private static final long LOCK_WAIT_MILLIS = 500;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private CrewMemberLeftUseCase useCase;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private PartyRepository partyRepository;

	@Autowired
	private PartyMemberRepository partyMemberRepository;

	@Autowired
	private ParticipationRepository participationRepository;

	@Autowired
	private OrderOutboxKafkaEventRepository orderOutboxKafkaEventRepository;

	@Autowired
	private StubRaceCoursePort raceCoursePort;

	@Autowired
	private StubCrewPort crewPort;

	@BeforeEach
	void setUp() {
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null));
	}

	@AfterEach
	void tearDown() {
		participationRepository.deleteAll();
		partyMemberRepository.deleteAll();
		partyRepository.deleteAll();
		orderOutboxKafkaEventRepository.deleteAll();
		raceCoursePort.clear();
		crewPort.clear();
	}

	@Test
	@DisplayName("크루를 나간 파티원은 모집 중 파티에서 빠지고 신청서가 끝나며, 같은 메시지가 다시 와도 한 번만 처리된다")
	void removesMember() throws Exception {
		long partyId = createParty(LEADER);
		join(MEMBER, partyId);

		assertThat(useCase.removeFromParties(CREW_ID, MEMBER, LEFT_JOINED_AT, LocalDateTime.now())).isOne();

		assertThat(member(partyId, MEMBER).getLeftAt()).isNotNull();
		assertThat(statusOf(MEMBER)).isEqualTo(ParticipationStatus.CANCELED);
		assertThat(statusOf(LEADER)).isEqualTo(ParticipationStatus.PENDING);
		assertThat(party(partyId).getLeaderMemberId()).isEqualTo(LEADER);
		// 자리 해제 메시지가 생겼다
		long outbox = orderOutboxKafkaEventRepository.count();
		assertThat(outbox).isPositive();

		// 다시 와도 이미 빠져 있어 아무것도 바뀌지 않는다 (자리 해제 메시지도 다시 남기지 않음)
		assertThat(useCase.removeFromParties(CREW_ID, MEMBER, LEFT_JOINED_AT, LocalDateTime.now())).isZero();
		assertThat(orderOutboxKafkaEventRepository.count()).isEqualTo(outbox);
	}

	@Test
	@DisplayName("파티장이 크루를 나가면 가장 먼저 합류한 파티원에게 넘기고, 혼자였으면 파티가 종료된다")
	void leaderLeaves() throws Exception {
		long partyId = createParty(LEADER);
		join(MEMBER, partyId);
		join(OTHER, partyId);
		long soloPartyId = createPartyOnOtherCrew();

		useCase.removeFromParties(CREW_ID, LEADER, LEFT_JOINED_AT, LocalDateTime.now());
		assertThat(party(partyId).getLeaderMemberId()).isEqualTo(MEMBER);
		assertThat(party(partyId).getStatus()).isEqualTo(PartyStatus.RECRUITING);
		// 다른 크루의 파티는 건드리지 않는다
		assertThat(member(soloPartyId, LEADER).isActive()).isTrue();

		useCase.removeFromParties(OTHER_CREW_ID, LEADER, LEFT_JOINED_AT, LocalDateTime.now());
		Party closed = party(soloPartyId);
		assertThat(closed.getStatus()).isEqualTo(PartyStatus.CLOSED);
		assertThat(closed.getClosedAt()).isNotNull();
	}

	@Test
	@DisplayName("떠남 메시지의 가입보다 늦게 다시 가입해 합류한 사람은 빼지 않는다 (늦게 온 메시지)")
	void keepsRejoinedMember() throws Exception {
		long partyId = createParty(LEADER);
		crewPort.rejoinedCrewAt(MEMBER, LEFT_JOINED_AT.plusDays(3));
		join(MEMBER, partyId);

		assertThat(useCase.removeFromParties(CREW_ID, MEMBER, LEFT_JOINED_AT, LocalDateTime.now())).isZero();

		assertThat(member(partyId, MEMBER).isActive()).isTrue();
		assertThat(statusOf(MEMBER)).isEqualTo(ParticipationStatus.PENDING);
	}

	@Test
	@DisplayName("결제 진행 중(경고 로그만)·결제 끝 파티는 바꾸지 않는다")
	void keepsPayingParty() throws Exception {
		for (PartyStatus status : new PartyStatus[] {PartyStatus.PAYING, PartyStatus.PAID}) {
			long partyId = createParty(LEADER);
			join(MEMBER, partyId);
			jdbcTemplate.update("update order_party set status = ? where id = ?", status.name(), partyId);

			assertThat(useCase.removeFromParties(CREW_ID, MEMBER, LEFT_JOINED_AT, LocalDateTime.now())).isZero();

			assertThat(member(partyId, MEMBER).isActive()).isTrue();
			assertThat(statusOf(MEMBER)).isEqualTo(ParticipationStatus.PENDING);
			tearDown();
			setUp();
		}
	}

	@Test
	@DisplayName("파티장이 크루 자리를 요청해 결과를 모르는 중이면 바꾸지 않고 예외로 끝내 Kafka 재시도로 미룬다. 표시가 풀리면 뺀다")
	void waitsWhileCrewHoldInProgress() throws Exception {
		long partyId = createParty(LEADER);
		join(MEMBER, partyId);
		jdbcTemplate.update("update order_party set crew_hold_requested_at = ? where id = ?", LocalDateTime.now(),
			partyId);

		assertThatThrownBy(() -> useCase.removeFromParties(CREW_ID, MEMBER, LEFT_JOINED_AT, LocalDateTime.now()))
			.isInstanceOf(PartyConflictException.class);
		assertThat(member(partyId, MEMBER).isActive()).isTrue();
		assertThat(statusOf(MEMBER)).isEqualTo(ParticipationStatus.PENDING);

		jdbcTemplate.update("update order_party set crew_hold_requested_at = null where id = ?", partyId);
		assertThat(useCase.removeFromParties(CREW_ID, MEMBER, LEFT_JOINED_AT, LocalDateTime.now())).isOne();
		assertThat(member(partyId, MEMBER).isActive()).isFalse();
	}

	@Test
	@DisplayName("떠남 메시지와 그 사람의 파티 나가기가 동시에 와도 한 번만 빠지고 메시지 처리는 실패하지 않는다")
	void concurrentWithLeave() throws Exception {
		for (int round = 0; round < CONCURRENT_ROUNDS; round++) {
			long partyId = createParty(LEADER);
			join(MEMBER, partyId);
			ExecutorService pool = Executors.newFixedThreadPool(2);
			CountDownLatch start = new CountDownLatch(1);
			Future<Integer> leave = pool.submit(() -> {
				start.await();
				return mockMvc.perform(post(BASE_URL + "/" + partyId + "/leave").header("X-Member-Id", MEMBER))
					.andReturn().getResponse().getStatus();
			});
			Future<Integer> event = pool.submit(() -> {
				start.await();
				return useCase.removeFromParties(CREW_ID, MEMBER, LEFT_JOINED_AT, LocalDateTime.now());
			});
			start.countDown();
			int leaveStatus = leave.get(10, TimeUnit.SECONDS);
			int removed = event.get(10, TimeUnit.SECONDS);
			pool.shutdown();

			// 나가기가 먼저면 200·메시지는 건너뜀, 메시지가 먼저면 나가기는 403(이미 파티원 아님)
			assertThat(leaveStatus == 200 ? 0 : 1).isEqualTo(removed);
			assertThat(leaveStatus).isIn(200, 403);
			assertThat(member(partyId, MEMBER).isActive()).isFalse();
			assertThat(participationRepository.findAll()).filteredOn(p -> p.getMemberId() == MEMBER)
				.singleElement().satisfies(p -> assertThat(p.getStatus()).isEqualTo(ParticipationStatus.CANCELED));
			tearDown();
			setUp();
		}
	}

	@Test
	@DisplayName("메시지가 파티 잠금을 기다리는 사이 그 사람이 다시 가입해 합류하면, 잠근 뒤 새 크루 가입 시각을 보고 남긴다")
	void rejoinWhileWaitingForLock() throws Exception {
		long partyId = createParty(LEADER);
		join(MEMBER, partyId);
		LocalDateTime rejoinedCrewAt = LEFT_JOINED_AT.plusDays(3);
		ExecutorService pool = Executors.newSingleThreadExecutor();
		Future<Integer> event = transactionTemplate.execute(status -> {
			// 합류처럼 파티를 잠근 채 파티원 줄의 크루 가입 시각을 새 값으로 바꾼다 (나갔다가 다시 가입해 합류한 것과 같은 결과)
			partyRepository.findByIdForUpdate(partyId).orElseThrow();
			member(partyId, MEMBER).rejoin(rejoinedCrewAt, LocalDateTime.now());
			Future<Integer> submitted = pool.submit(() ->
				useCase.removeFromParties(CREW_ID, MEMBER, LEFT_JOINED_AT, LocalDateTime.now()));
			// 메시지가 커밋 전 상태(옛 가입 시각)로 파티를 찾고 잠금에서 기다리게 둔다
			sleep(LOCK_WAIT_MILLIS);
			return submitted;
		});
		assertThat(event.get(10, TimeUnit.SECONDS)).isZero();
		pool.shutdown();

		assertThat(member(partyId, MEMBER).isActive()).isTrue();
		assertThat(statusOf(MEMBER)).isEqualTo(ParticipationStatus.PENDING);
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}

	private long createParty(long memberId) throws Exception {
		return createParty(memberId, CREW_ID);
	}

	private long createPartyOnOtherCrew() throws Exception {
		// 같은 대회에는 한 파티만 들어갈 수 있어 다른 대회 종목으로 만든다
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID + 1, RACE_ID + 1, 14, null));
		return createParty(LEADER, OTHER_CREW_ID, COURSE_ID + 1);
	}

	private long createParty(long memberId, long crewId) throws Exception {
		return createParty(memberId, crewId, COURSE_ID);
	}

	private long createParty(long memberId, long crewId, long courseId) throws Exception {
		String body = mockMvc.perform(post(BASE_URL)
				.header("X-Member-Id", memberId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "crewId": %d, "raceCourseId": %d, "participant": %s, "thirdPartyConsent": true }
					""".formatted(crewId, courseId, participant())))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return ((Number)JsonPath.read(body, "$.data.party.id")).longValue();
	}

	private void join(long memberId, long partyId) throws Exception {
		join(memberId, party(partyId).getJoinToken()).andExpect(status().isCreated());
	}

	private ResultActions join(long memberId, String token) throws Exception {
		return mockMvc.perform(post(BASE_URL + "/join")
			.header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{ "joinToken": "%s", "participant": %s, "thirdPartyConsent": true }
				""".formatted(token, participant())));
	}

	private Party party(long partyId) {
		return partyRepository.findById(partyId).orElseThrow();
	}

	private PartyMember member(long partyId, long memberId) {
		return partyMemberRepository.findByPartyIdAndMemberId(partyId, memberId).orElseThrow();
	}

	// 살아 있는 신청서가 있으면 그것, 없으면 마지막 신청서의 상태
	private ParticipationStatus statusOf(long memberId) {
		List<ParticipationStatus> statuses = participationRepository.findAll().stream()
			.filter(p -> p.getMemberId() == memberId)
			.map(Participation::getStatus)
			.toList();
		return statuses.stream().filter(ParticipationStatus::isActive).findFirst().orElse(statuses.getLast());
	}

	private static String participant() {
		return """
			{ "name": "김러너", "birth": "1994-06-25", "gender": "MALE", "phone": "010-1234-5678",
				"emergencyContactName": "김가족", "emergencyContactPhone": "010-9876-5432", "tshirtSize": "L",
				"shippingAddress": { "recipientName": "김러너", "recipientPhone": "010-1234-5678", "zipcode": "04700",
					"address": "서울 성동구 왕십리로 00", "addressDetail": "101동 1001호" } }
			""";
	}
}
