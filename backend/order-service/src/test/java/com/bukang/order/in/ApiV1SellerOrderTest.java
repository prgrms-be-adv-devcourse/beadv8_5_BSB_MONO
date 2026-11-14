package com.bukang.order.in;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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

import com.bukang.order.app.OrderCreateResult;
import com.bukang.order.app.OrderCreateUseCase;
import com.bukang.order.app.OrderExpiryUseCase;
import com.bukang.order.app.OrderPayUseCase;
import com.bukang.order.domain.Gender;
import com.bukang.order.domain.Order;
import com.bukang.order.domain.ParticipantInfo;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.Party;
import com.bukang.order.domain.ShippingAddress;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.PartyRepository;
import com.bukang.order.out.QueueAdmissionRepository;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.support.QueueEntry;
import com.bukang.order.support.StubRaceCoursePort;
import com.bukang.order.support.StubRaceCoursePortConfig;
import com.bukang.order.support.StubRefundEligibilityPortConfig;
import com.bukang.order.support.StubSeatHoldPort;
import com.bukang.order.support.StubSeatHoldPortConfig;
import com.bukang.order.support.StubSellerLookupPort;
import com.bukang.order.support.StubSellerLookupPortConfig;
import com.bukang.order.support.StubWalletPort;
import com.bukang.order.support.StubWalletPortConfig;
import com.jayway.jsonpath.JsonPath;

/**
 * 판매자 주문 목록·파티별 내역 API (BUK-227). 승인된 판매자만 쓰고, 자기 대회 것만 보인다
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubSeatHoldPortConfig.class, StubWalletPortConfig.class,
	StubRefundEligibilityPortConfig.class, StubSellerLookupPortConfig.class})
class ApiV1SellerOrderTest {
	private static final String ORDERS_URL = "/api/v1/order/seller/orders";
	private static final String PARTIES_URL = "/api/v1/order/seller/parties";
	private static final long COURSE_ID = 80;
	private static final long RACE_ID = 800;
	private static final long OTHER_COURSE_ID = 81;
	private static final long OTHER_RACE_ID = 810;
	private static final long BUYER_ID = 1;
	private static final long SELLER_MEMBER_ID = 90;
	private static final long OTHER_SELLER_MEMBER_ID = 91;
	private static final long NOT_SELLER_MEMBER_ID = 92;
	private static final ParticipantInfo PARTICIPANT = new ParticipantInfo("박파티", LocalDate.of(1994, 6, 25),
		Gender.MALE, "010-1234-5678", "박가족", "010-9876-5432", "L",
		new ShippingAddress("박파티", "010-1234-5678", "04700", "서울 성동구 왕십리로 00", null));

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private ParticipationRepository participationRepository;

	@Autowired
	private PartyRepository partyRepository;

	@Autowired
	private QueueAdmissionRepository queueAdmissionRepository;

	@Autowired
	private OrderCreateUseCase orderCreateUseCase;

	@Autowired
	private OrderPayUseCase orderPayUseCase;

	@Autowired
	private OrderExpiryUseCase orderExpiryUseCase;

	@Autowired
	private StubRaceCoursePort raceCoursePort;

	@Autowired
	private StubSeatHoldPort seatHoldPort;

	@Autowired
	private StubWalletPort walletPort;

	@Autowired
	private StubSellerLookupPort sellerLookupPort;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@AfterEach
	void tearDown() {
		orderRepository.deleteAll();
		participationRepository.deleteAll();
		partyRepository.deleteAll();
		queueAdmissionRepository.deleteAll();
		raceCoursePort.clear();
		seatHoldPort.clear();
		walletPort.clear();
		sellerLookupPort.clear();
	}

	@Test
	@DisplayName("판매자는 자기 대회 주문만 최근 것부터 보고, 연락처·배송지는 응답에 없다. 대회·상태로 거르고 쪽을 나눈다")
	void listsOnlyMyOrders() throws Exception {
		setUpCourses();
		long first = paidOrder(BUYER_ID, COURSE_ID);
		long second = paidOrder(BUYER_ID + 1, COURSE_ID);
		paidOrder(BUYER_ID + 2, OTHER_COURSE_ID);

		orders(SELLER_MEMBER_ID, "size", "1")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("주문 목록을 조회했습니다."))
			.andExpect(jsonPath("$.data.totalElements").value(2))
			.andExpect(jsonPath("$.data.totalPages").value(2))
			.andExpect(jsonPath("$.data.orders[0].orderItemId").value(itemIdOf(second)))
			.andExpect(jsonPath("$.data.orders[0].orderNo").isNotEmpty())
			.andExpect(jsonPath("$.data.orders[0].orderedAt").isNotEmpty())
			.andExpect(jsonPath("$.data.orders[0].paidAt").isNotEmpty())
			.andExpect(jsonPath("$.data.orders[0].raceId").value(RACE_ID))
			.andExpect(jsonPath("$.data.orders[0].courseName").value("테스트 대회 종목 " + COURSE_ID))
			.andExpect(jsonPath("$.data.orders[0].status").value("PAID"))
			.andExpect(jsonPath("$.data.orders[0].participantName").value("김러너"))
			.andExpect(jsonPath("$.data.orders[0].kitOptions").isArray())
			.andExpect(jsonPath("$.data.orders[0].partyId").doesNotExist())
			.andExpect(jsonPath("$.data.orders[0].participationId").doesNotExist())
			.andExpect(jsonPath("$.data.orders[0].phone").doesNotExist())
			.andExpect(jsonPath("$.data.orders[0].shippingAddress").doesNotExist());
		orders(SELLER_MEMBER_ID, "size", "1", "page", "1")
			.andExpect(jsonPath("$.data.orders[0].orderItemId").value(itemIdOf(first)));
		orders(SELLER_MEMBER_ID, "raceId", String.valueOf(OTHER_RACE_ID))
			.andExpect(jsonPath("$.data.totalElements").value(0));
		orders(SELLER_MEMBER_ID, "status", "CANCELED")
			.andExpect(jsonPath("$.data.totalElements").value(0));
		orders(OTHER_SELLER_MEMBER_ID)
			.andExpect(jsonPath("$.data.totalElements").value(1))
			.andExpect(jsonPath("$.data.orders[0].raceId").value(OTHER_RACE_ID));
	}

	@Test
	@DisplayName("주문일 기간으로 거르고, 파티 번호로 찾으면 기간을 보지 않는다")
	void periodAndPartySearch() throws Exception {
		setUpCourses();
		long orderId = paidOrder(BUYER_ID, COURSE_ID);
		paidOrder(BUYER_ID + 1, COURSE_ID);
		Party party = partyRepository.save(Party.create(2, BUYER_ID, RACE_ID, COURSE_ID, LocalDateTime.now()));
		// 파티로 결제한 주문처럼 신청서에 파티 ID를 붙인다 (파티 결제 흐름 자체는 ApiV1PartyPaymentTest가 본다)
		jdbcTemplate.update("update order_participation set party_id = ? where id = ?", party.getId(),
			participationIdOf(orderId));
		LocalDate yesterday = LocalDate.now().minusDays(1);

		orders(SELLER_MEMBER_ID, "from", yesterday.minusDays(5).toString(), "to", yesterday.toString())
			.andExpect(jsonPath("$.data.totalElements").value(0));
		orders(SELLER_MEMBER_ID, "from", yesterday.toString(), "to", LocalDate.now().toString())
			.andExpect(jsonPath("$.data.totalElements").value(2));
		orders(SELLER_MEMBER_ID, "partyId", String.valueOf(party.getId()),
			"from", yesterday.minusDays(5).toString(), "to", yesterday.toString())
			.andExpect(jsonPath("$.data.totalElements").value(1))
			.andExpect(jsonPath("$.data.orders[0].orderItemId").value(itemIdOf(orderId)))
			.andExpect(jsonPath("$.data.orders[0].partyId").value(party.getId()));
	}

	@Test
	@DisplayName("결제 대기 주문은 결제 대기로 나오고, 기한이 지나 만료되면 빠진다. 키트 없는 참가권도 나온다")
	void pendingExpiredAndKitless() throws Exception {
		setUpCourses();
		raceCoursePort.put(StubRaceCoursePort.courseWithoutKit(COURSE_ID + 2, RACE_ID));
		long pendingOrderId = createdOrder(BUYER_ID, COURSE_ID);
		paidOrder(BUYER_ID + 1, COURSE_ID + 2);

		orders(SELLER_MEMBER_ID, "status", "ORDERED")
			.andExpect(jsonPath("$.data.totalElements").value(1))
			.andExpect(jsonPath("$.data.orders[0].paidAt").doesNotExist());
		orders(SELLER_MEMBER_ID, "raceCourseId", String.valueOf(COURSE_ID + 2))
			.andExpect(jsonPath("$.data.totalElements").value(1))
			.andExpect(jsonPath("$.data.orders[0].status").value("PAID"))
			.andExpect(jsonPath("$.data.orders[0].kitOptions.length()").value(0));

		LocalDateTime due = orderRepository.findById(pendingOrderId).orElseThrow().getPaymentDueAt();
		orderExpiryUseCase.expireOverdue(due.plusSeconds(1));

		orders(SELLER_MEMBER_ID)
			.andExpect(jsonPath("$.data.totalElements").value(1))
			.andExpect(jsonPath("$.data.orders[0].raceCourseId").value(COURSE_ID + 2));
	}

	@Test
	@DisplayName("기간이 거꾸로거나 12개월을 넘으면 400 ORDER_INVALID_SEARCH_PERIOD다")
	void invalidPeriod() throws Exception {
		setUpCourses();
		orders(SELLER_MEMBER_ID, "from", "2026-10-02", "to", "2026-10-01")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ORDER_INVALID_SEARCH_PERIOD"))
			.andExpect(jsonPath("$.message").value("조회 시작일이 종료일보다 늦습니다."));
		orders(SELLER_MEMBER_ID, "from", "2025-10-01", "to", "2026-10-02")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("조회 기간은 한 번에 12개월까지입니다."));
		orders(SELLER_MEMBER_ID, "from", "2025-10-01", "to", "2026-10-01")
			.andExpect(status().isOk());
		parties(SELLER_MEMBER_ID, "raceId", String.valueOf(RACE_ID), "from", "2025-10-01", "to", "2026-10-02")
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("파티별 내역: 신청 인원 = 결제 인원 + 결제하지 않은 자리(결제 중·기한 지나 풀림)이고, 신청 전 파티는 빠진다. "
		+ "파티 번호로 찾으면 그 파티만, 기간은 보지 않는다")
	void partySummary() throws Exception {
		setUpCourses();
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime due = now.plusHours(5);
		Party paying = appliedParty(due);
		partyMember(paying, 11, "CONFIRMED", due);
		partyMember(paying, 12, "CONFIRMED", due);
		partyMember(paying, 13, "APPLIED", due);
		partyMember(paying, 14, "RELEASED", due);
		Party other = appliedParty(due);
		partyMember(other, 21, "CONFIRMED", due);
		// 아직 신청하지 않은 파티는 판매자에게 보이지 않는다
		partyRepository.save(Party.create(2, 30, RACE_ID, COURSE_ID, now));

		parties(SELLER_MEMBER_ID, "raceId", String.valueOf(RACE_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("파티별 내역을 조회했습니다."))
			.andExpect(jsonPath("$.data.totalElements").value(2))
			.andExpect(jsonPath("$.data.parties[0].partyId").value(other.getId()))
			.andExpect(jsonPath("$.data.parties[1].partyId").value(paying.getId()))
			.andExpect(jsonPath("$.data.parties[1].crewId").value(2))
			.andExpect(jsonPath("$.data.parties[1].status").value("PAYING"))
			.andExpect(jsonPath("$.data.parties[1].appliedCount").value(4))
			.andExpect(jsonPath("$.data.parties[1].paidCount").value(2))
			.andExpect(jsonPath("$.data.parties[1].unpaidCount").value(2))
			.andExpect(jsonPath("$.data.parties[1].paymentDueAt").isNotEmpty());
		LocalDate past = LocalDate.now().minusDays(10);
		parties(SELLER_MEMBER_ID, "raceId", String.valueOf(RACE_ID),
			"from", past.toString(), "to", past.plusDays(1).toString())
			.andExpect(jsonPath("$.data.totalElements").value(0));
		parties(SELLER_MEMBER_ID, "raceId", String.valueOf(RACE_ID), "partyId", String.valueOf(paying.getId()),
			"from", past.toString(), "to", past.plusDays(1).toString())
			.andExpect(jsonPath("$.data.totalElements").value(1))
			.andExpect(jsonPath("$.data.parties[0].appliedCount").value(4));
	}

	@Test
	@DisplayName("판매자가 아니면 두 API 모두 403, 다른 판매자 대회의 파티는 403, 대회 ID가 없으면 400이다")
	void forbidden() throws Exception {
		setUpCourses();
		appliedParty(LocalDateTime.now().plusHours(5));

		orders(NOT_SELLER_MEMBER_ID)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORDER_SELLER_FORBIDDEN"));
		parties(NOT_SELLER_MEMBER_ID, "raceId", String.valueOf(RACE_ID))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("승인된 판매자만 쓸 수 있습니다."));
		parties(OTHER_SELLER_MEMBER_ID, "raceId", String.valueOf(RACE_ID))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("본인 대회의 파티만 볼 수 있습니다."));
		// 결과가 비는 쪽·없는 파티 ID여도 남의 대회면 403이다 (파티 수·파티 ID가 새지 않게)
		parties(OTHER_SELLER_MEMBER_ID, "raceId", String.valueOf(RACE_ID), "page", "999")
			.andExpect(status().isForbidden());
		parties(OTHER_SELLER_MEMBER_ID, "raceId", String.valueOf(RACE_ID), "partyId", "999999")
			.andExpect(status().isForbidden());
		// 남의 파티 ID로 주문을 찾아도 자기 주문만 걸러 빈 목록이다
		orders(OTHER_SELLER_MEMBER_ID, "partyId", "1")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(0));
		mockMvc.perform(get(PARTIES_URL).header("X-Member-Id", SELLER_MEMBER_ID))
			.andExpect(status().isBadRequest());
		mockMvc.perform(get(ORDERS_URL))
			.andExpect(status().isUnauthorized());
	}

	private void setUpCourses() {
		sellerLookupPort.approve(SELLER_MEMBER_ID, StubRaceCoursePort.SELLER_ID);
		sellerLookupPort.approve(OTHER_SELLER_MEMBER_ID, StubRaceCoursePort.SELLER_ID + 1);
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null));
		RaceCourseInfo course = StubRaceCoursePort.openCourse(OTHER_COURSE_ID, OTHER_RACE_ID, 14, null);
		raceCoursePort.put(new RaceCourseInfo(course.raceCourseId(), course.raceId(), course.raceDate(),
			course.applicationOpenAt(), course.applicationCloseAt(), course.minAge(), course.maxAge(),
			StubRaceCoursePort.SELLER_ID + 1, course.courseName(), course.price(), course.kitIncluded(),
			course.refundDeadline(), course.scheduleChangedAt(), course.canceled(), course.tshirtSizeOption()));
	}

	private Party appliedParty(LocalDateTime due) {
		Party party = Party.create(2, 10, RACE_ID, COURSE_ID, LocalDateTime.now());
		party.startPayment(due, LocalDateTime.now());
		return partyRepository.save(party);
	}

	private void partyMember(Party party, long memberId, String status, LocalDateTime due) {
		Participation participation = Participation.partyMember(memberId, RACE_ID, COURSE_ID, party.getId(),
			PARTICIPANT, LocalDateTime.now());
		participation.markApplied(due);
		if (status.equals("CONFIRMED")) {
			participation.markConfirmed(LocalDateTime.now());
		} else if (status.equals("RELEASED")) {
			participation.markReleased(LocalDateTime.now());
		}
		participationRepository.save(participation);
	}

	private ResultActions orders(long memberId, String... params) throws Exception {
		var request = get(ORDERS_URL).header("X-Member-Id", memberId);
		for (int i = 0; i < params.length; i += 2) {
			request.param(params[i], params[i + 1]);
		}
		return mockMvc.perform(request);
	}

	private ResultActions parties(long memberId, String... params) throws Exception {
		var request = get(PARTIES_URL).header("X-Member-Id", memberId);
		for (int i = 0; i < params.length; i += 2) {
			request.param(params[i], params[i + 1]);
		}
		return mockMvc.perform(request);
	}

	private long createdOrder(long buyerId, long courseId) throws Exception {
		long participationId = apply(buyerId, courseId);
		OrderCreateResult result = transactionTemplate.execute(status -> orderCreateUseCase.create(buyerId,
			participationId, UUID.randomUUID().toString(), LocalDateTime.now()));
		return result.order().getId();
	}

	private long paidOrder(long buyerId, long courseId) throws Exception {
		long orderId = createdOrder(buyerId, courseId);
		transactionTemplate.execute(status -> orderPayUseCase.pay(buyerId, orderId, List.of(), LocalDateTime.now()));
		return orderId;
	}

	private long apply(long buyerId, long courseId) throws Exception {
		QueueEntry.enter(mockMvc, buyerId, courseId);
		String body = mockMvc.perform(post("/api/v1/order/participations")
				.header("X-Member-Id", buyerId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "raceCourseId": %d, "thirdPartyConsent": true,
						"participant": { "name": "김러너", "birth": "1994-06-25", "gender": "MALE",
							"phone": "010-1234-5678", "emergencyContactName": "김가족",
							"emergencyContactPhone": "010-9876-5432", "tshirtSize": "L",
						"shippingAddress": { "recipientName": "김러너", "recipientPhone": "010-1234-5678",
							"zipcode": "04700", "address": "서울 성동구 왕십리로 00", "addressDetail": "101동 1001호" } } }
					""".formatted(courseId)))
			.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(body, "$.data.id").longValue();
	}

	private long itemIdOf(long orderId) {
		return transactionTemplate.execute(status -> {
			Order order = orderRepository.findById(orderId).orElseThrow();
			return order.getItems().getFirst().getId();
		});
	}

	private long participationIdOf(long orderId) {
		return transactionTemplate.execute(status -> {
			Order order = orderRepository.findById(orderId).orElseThrow();
			return order.getItems().getFirst().getParticipationId();
		});
	}
}
