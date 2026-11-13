package com.bukang.order.in;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.out.CartRepository;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.PartyMemberRepository;
import com.bukang.order.out.PartyRepository;
import com.bukang.order.out.QueueAdmissionRepository;
import com.bukang.order.out.seat.CrewSeatHoldPort;
import com.bukang.order.out.seat.FakeCrewSeatHoldAdapter;
import com.bukang.order.support.QueueEntry;
import com.bukang.order.support.StubCrewPort;
import com.bukang.order.support.StubCrewPortConfig;
import com.bukang.order.support.StubQueueAdmissionPort;
import com.bukang.order.support.StubQueueAdmissionPortConfig;
import com.bukang.order.support.StubRaceCoursePort;
import com.bukang.order.support.StubRaceCoursePortConfig;
import com.bukang.order.support.StubRefundEligibilityPortConfig;
import com.bukang.order.support.StubSeatHoldPort;
import com.bukang.order.support.StubSeatHoldPortConfig;
import com.bukang.order.support.StubWalletPort;
import com.bukang.order.support.StubWalletPortConfig;
import com.jayway.jsonpath.JsonPath;

/**
 * 바깥 트랜잭션 없이(NOT_SUPPORTED) 부르는 요청이 DB 연결을 하나만 쓰는지 본다 (BUK-213)
 * 연결 풀을 1개로 줄여, 연결을 두 개 잡는 흐름은 두 번째 연결을 기다리다 연결 대기 시간(250ms) 뒤 500으로 끝난다
 * 운영에서는 연결 수(10)만큼 동시에 결제하면 모두 첫 연결을 쥔 채 두 번째를 기다리다 30초 뒤 실패했다 (PR #478 부하 테스트)
 * 다른 테스트와 H2 DB를 나눠 쓰지 않도록 DB 이름을 따로 둔다 (create-drop이 다른 컨텍스트의 표를 지우지 않게)
 */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:bukang-order-one-connection;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
	"spring.datasource.hikari.maximum-pool-size=1",
	"spring.datasource.hikari.connection-timeout=250"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubSeatHoldPortConfig.class, StubWalletPortConfig.class,
	StubRefundEligibilityPortConfig.class, StubCrewPortConfig.class, StubQueueAdmissionPortConfig.class})
class OrderSingleConnectionTest {
	private static final long MEMBER_ID = 1;
	private static final long COURSE_ID = 10;
	private static final long CREW_ID = 7;
	private static final long PARTY_COURSE_ID = 301;
	private static final long PARTY_RACE_ID = 300;
	private static final List<Long> PARTY_MEMBERS = List.of(2L, 3L, 4L);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private ParticipationRepository participationRepository;

	@Autowired
	private QueueAdmissionRepository queueAdmissionRepository;

	@Autowired
	private CartRepository cartRepository;

	@Autowired
	private PartyRepository partyRepository;

	@Autowired
	private PartyMemberRepository partyMemberRepository;

	@Autowired
	private StubRaceCoursePort raceCoursePort;

	@Autowired
	private StubSeatHoldPort seatHoldPort;

	@Autowired
	private StubWalletPort walletPort;

	@Autowired
	private StubQueueAdmissionPort queueAdmissionPort;

	@Autowired
	private StubCrewPort crewPort;

	@Autowired
	private FakeCrewSeatHoldAdapter fakeCrewSeatHoldAdapter;

	@MockitoSpyBean
	private CrewSeatHoldPort crewSeatHoldPort;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@BeforeEach
	void setUp() {
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID, 100, 14, null));
	}

	@AfterEach
	void tearDown() {
		orderRepository.deleteAll();
		participationRepository.deleteAll();
		partyMemberRepository.deleteAll();
		partyRepository.deleteAll();
		queueAdmissionRepository.deleteAll();
		cartRepository.deleteAll();
		raceCoursePort.clear();
		seatHoldPort.clear();
		walletPort.clear();
		queueAdmissionPort.clear();
		crewPort.clear();
		fakeCrewSeatHoldAdapter.clear();
	}

	@Test
	@DisplayName("연결이 하나뿐이어도 결제가 된다")
	void payWithOneConnection() throws Exception {
		long orderId = read(order(apply()));

		pay(orderId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("PAID"));
	}

	@Test
	@DisplayName("연결이 하나뿐이어도 결제한 참가권을 취소할 수 있다")
	void cancelWithOneConnection() throws Exception {
		long orderId = read(order(apply()));
		pay(orderId).andExpect(status().isOk());
		long orderItemId = transactionTemplate.execute(status ->
			orderRepository.findById(orderId).orElseThrow().getItems().getFirst().getId());

		mockMvc.perform(post("/api/v1/order/orders/" + orderId + "/items/" + orderItemId + "/cancel")
				.header("X-Member-Id", MEMBER_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("CANCELED"));
	}

	@Test
	@DisplayName("연결이 하나뿐이어도 장바구니 결제가 된다")
	void cartCheckoutWithOneConnection() throws Exception {
		long participationId = apply();

		mockMvc.perform(post("/api/v1/order/cart/checkout")
				.header("X-Member-Id", MEMBER_ID)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"participationIds\": [%d]}".formatted(participationId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.results[0].outcome").value("CREATED"));
	}

	@Test
	@DisplayName("연결이 하나뿐이어도 503 뒤 크루 예매가 끝난 파티의 재신청(처음 결과 이어받기)이 된다")
	void partyRetryWithOneConnection() throws Exception {
		LocalDateTime saleStartAt = LocalDateTime.now().minusDays(1);
		putPartyCourse(saleStartAt, LocalDateTime.now().plusDays(20));
		long partyId = partyWith(PARTY_MEMBERS);
		doAnswer(invocation -> {
			invocation.callRealMethod();
			throw new ExternalServiceUnavailableException("상품 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.");
		}).doCallRealMethod().when(crewSeatHoldPort).holdAll(anyLong(), anyLong(), anyLong(), any(), any());
		partyApply(partyId).andExpect(status().isServiceUnavailable());
		// 크루 예매가 끝나 확인에 걸리고, 표시가 남아 있어 상품의 처음 결과를 이어받는 길로 간다
		putPartyCourse(saleStartAt, LocalDateTime.now().minusMinutes(1));
		queueAdmissionPort.notAdmittedToParty(PARTY_COURSE_ID);

		partyApply(partyId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("PAYING"));
	}

	private ResultActions pay(long orderId) throws Exception {
		return mockMvc.perform(post("/api/v1/order/orders/" + orderId + "/payment").header("X-Member-Id", MEMBER_ID));
	}

	private long apply() throws Exception {
		QueueEntry.enter(mockMvc, MEMBER_ID, COURSE_ID);
		return read(mockMvc.perform(post("/api/v1/order/participations")
			.header("X-Member-Id", MEMBER_ID)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{ "raceCourseId": %d, "thirdPartyConsent": true, "participant": %s }
				""".formatted(COURSE_ID, participant()))));
	}

	private ResultActions order(long participationId) throws Exception {
		return mockMvc.perform(post("/api/v1/order/orders")
			.header("X-Member-Id", MEMBER_ID)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"participationId\": %d}".formatted(participationId)));
	}

	private void putPartyCourse(LocalDateTime startAt, LocalDateTime endAt) {
		raceCoursePort.put(StubRaceCoursePort.withCrewSale(
			StubRaceCoursePort.openCourse(PARTY_COURSE_ID, PARTY_RACE_ID, 14, null), startAt, endAt, 4, 10));
	}

	private long partyWith(List<Long> members) throws Exception {
		String created = mockMvc.perform(post("/api/v1/order/parties")
				.header("X-Member-Id", MEMBER_ID)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "crewId": %d, "raceCourseId": %d, "participant": %s, "thirdPartyConsent": true }
					""".formatted(CREW_ID, PARTY_COURSE_ID, participant())))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		String token = JsonPath.read(created, "$.data.party.joinToken");
		for (long member : members) {
			mockMvc.perform(post("/api/v1/order/parties/join")
					.header("X-Member-Id", member)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
						{ "joinToken": "%s", "participant": %s, "thirdPartyConsent": true }
						""".formatted(token, participant())))
				.andExpect(status().isCreated());
		}
		return ((Number)JsonPath.read(created, "$.data.party.id")).longValue();
	}

	private ResultActions partyApply(long partyId) throws Exception {
		return mockMvc.perform(post("/api/v1/order/parties/" + partyId + "/apply").header("X-Member-Id", MEMBER_ID));
	}

	private static String participant() {
		return """
			{ "name": "김러너", "birth": "1994-06-25", "gender": "MALE", "phone": "010-1234-5678",
				"emergencyContactName": "김가족", "emergencyContactPhone": "010-9876-5432", "tshirtSize": "L",
				"shippingAddress": { "recipientName": "김러너", "recipientPhone": "010-1234-5678", "zipcode": "04700",
					"address": "서울 성동구 왕십리로 00", "addressDetail": "101동 1001호" } }
			""";
	}

	private long read(ResultActions result) throws Exception {
		String body = result.andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(body, "$.data.id").longValue();
	}
}
