package com.bukang.order.in;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.order.domain.Order;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.QueueAdmissionRepository;
import com.bukang.order.support.QueueEntry;
import com.bukang.order.support.StubRaceCoursePort;
import com.bukang.order.support.StubRaceCoursePortConfig;
import com.bukang.order.support.StubSeatHoldPort;
import com.bukang.order.support.StubSeatHoldPortConfig;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubSeatHoldPortConfig.class})
class ApiV1OrderQueryTest {
	private static final String BASE_URL = "/api/v1/order/orders";
	private static final long MEMBER_ID = 1;
	private static final long OTHER_MEMBER_ID = 2;
	// 종목 10·11·12는 대회 100·101·102 (한 사람 한 대회 1건이라 주문마다 다른 대회로 신청한다)
	private static final long FIRST_COURSE_ID = 10;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private ParticipationRepository participationRepository;

	@Autowired
	private QueueAdmissionRepository queueAdmissionRepository;

	@Autowired
	private StubRaceCoursePort raceCoursePort;

	@Autowired
	private StubSeatHoldPort seatHoldPort;

	@BeforeEach
	void setUp() {
		for (int i = 0; i < 3; i++) {
			raceCoursePort.put(StubRaceCoursePort.openCourse(FIRST_COURSE_ID + i, 100 + i, 14, null));
		}
	}

	@AfterEach
	void tearDown() {
		orderRepository.deleteAll();
		participationRepository.deleteAll();
		queueAdmissionRepository.deleteAll();
		raceCoursePort.clear();
		seatHoldPort.clear();
	}

	@Test
	@DisplayName("10/10·10/11·10/12에 주문한 회원의 목록은 10/12 → 10/10 순서이고, 다른 회원 주문은 없다")
	void findMineNewestFirst() throws Exception {
		long first = orderAt(MEMBER_ID, FIRST_COURSE_ID, LocalDateTime.of(2026, 10, 10, 9, 0));
		long second = orderAt(MEMBER_ID, FIRST_COURSE_ID + 1, LocalDateTime.of(2026, 10, 11, 9, 0));
		long third = orderAt(MEMBER_ID, FIRST_COURSE_ID + 2, LocalDateTime.of(2026, 10, 12, 9, 0));
		orderAt(OTHER_MEMBER_ID, FIRST_COURSE_ID, LocalDateTime.of(2026, 10, 12, 9, 0));

		findMine(MEMBER_ID, "")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("주문 내역을 조회했습니다."))
			.andExpect(jsonPath("$.data.orders.length()").value(3))
			.andExpect(jsonPath("$.data.orders[0].id").value(third))
			.andExpect(jsonPath("$.data.orders[1].id").value(second))
			.andExpect(jsonPath("$.data.orders[2].id").value(first))
			.andExpect(jsonPath("$.data.orders[0].status").value("CREATED"))
			.andExpect(jsonPath("$.data.orders[0].itemName").value("테스트 대회 종목 " + (FIRST_COURSE_ID + 2)))
			.andExpect(jsonPath("$.data.orders[0].itemCount").value(1))
			.andExpect(jsonPath("$.data.orders[0].paymentDueAt").isNotEmpty())
			.andExpect(jsonPath("$.data.totalElements").value(3))
			.andExpect(jsonPath("$.data.page").value(0))
			.andExpect(jsonPath("$.data.size").value(20));
	}

	@Test
	@DisplayName("조회 기간은 주문 일자 기준이고 종료일 23:59 주문까지 포함한다. 시작일·종료일은 각각 생략할 수 있다")
	void findMineByPeriod() throws Exception {
		orderAt(MEMBER_ID, FIRST_COURSE_ID, LocalDateTime.of(2026, 10, 10, 23, 59, 59));
		long middle = orderAt(MEMBER_ID, FIRST_COURSE_ID + 1, LocalDateTime.of(2026, 10, 11, 23, 59, 59));
		orderAt(MEMBER_ID, FIRST_COURSE_ID + 2, LocalDateTime.of(2026, 10, 12, 0, 0));

		findMine(MEMBER_ID, "?from=2026-10-11&to=2026-10-11")
			.andExpect(jsonPath("$.data.orders.length()").value(1))
			.andExpect(jsonPath("$.data.orders[0].id").value(middle));
		findMine(MEMBER_ID, "?from=2026-10-11")
			.andExpect(jsonPath("$.data.totalElements").value(2));
		findMine(MEMBER_ID, "?to=2026-10-11")
			.andExpect(jsonPath("$.data.totalElements").value(2));
	}

	@Test
	@DisplayName("페이지 크기만큼 나눠 돌려주고, 주문이 없으면 빈 목록이다")
	void findMinePaging() throws Exception {
		for (int i = 0; i < 3; i++) {
			orderAt(MEMBER_ID, FIRST_COURSE_ID + i, LocalDateTime.of(2026, 10, 10 + i, 9, 0));
		}

		findMine(MEMBER_ID, "?size=2")
			.andExpect(jsonPath("$.data.orders.length()").value(2))
			.andExpect(jsonPath("$.data.totalPages").value(2));
		findMine(MEMBER_ID, "?size=2&page=1")
			.andExpect(jsonPath("$.data.orders.length()").value(1));
		findMine(OTHER_MEMBER_ID, "")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.orders.length()").value(0))
			.andExpect(jsonPath("$.data.totalElements").value(0));
	}

	@Test
	@DisplayName("시작일이 종료일보다 늦거나, 페이지 크기가 100을 넘거나, 날짜 형식이 틀리면 400이다")
	void findMineInvalidRequest() throws Exception {
		findMine(MEMBER_ID, "?from=2026-10-12&to=2026-10-11")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("조회 시작일이 종료일보다 늦습니다."))
			.andExpect(jsonPath("$.code").value("ORDER_INVALID_SEARCH_PERIOD"));
		findMine(MEMBER_ID, "?size=101")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("한 번에 100개까지 조회할 수 있습니다."))
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		findMine(MEMBER_ID, "?from=2026/10/11")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."))
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("주문 상세에서 상품·상태·결제 기한을 보고, 다른 회원 주문은 403, 없는 주문은 404다")
	void findById() throws Exception {
		long orderId = orderAt(MEMBER_ID, FIRST_COURSE_ID, LocalDateTime.of(2026, 10, 12, 9, 0));

		mockMvc.perform(get(BASE_URL + "/" + orderId).header("X-Member-Id", MEMBER_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("주문을 조회했습니다."))
			.andExpect(jsonPath("$.data.id").value(orderId))
			.andExpect(jsonPath("$.data.status").value("CREATED"))
			.andExpect(jsonPath("$.data.paymentDueAt").isNotEmpty())
			.andExpect(jsonPath("$.data.paidAt").isEmpty())
			.andExpect(jsonPath("$.data.canceledAmount").value(0))
			.andExpect(jsonPath("$.data.items[0].itemName").value("테스트 대회 종목 " + FIRST_COURSE_ID))
			.andExpect(jsonPath("$.data.items[0].unitPrice")
				.value(StubRaceCoursePort.PRICE));

		mockMvc.perform(get(BASE_URL + "/" + orderId).header("X-Member-Id", OTHER_MEMBER_ID))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("본인의 주문만 볼 수 있습니다."))
			.andExpect(jsonPath("$.code").value("ORDER_FORBIDDEN"));
		mockMvc.perform(get(BASE_URL + "/999999").header("X-Member-Id", MEMBER_ID))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("존재하지 않는 주문입니다."))
			.andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
	}

	private ResultActions findMine(long memberId, String query) throws Exception {
		return mockMvc.perform(get(BASE_URL + query).header("X-Member-Id", memberId));
	}

	// 신청서 작성 → [결제하기]로 주문을 만들고, 주문 일시를 정한 시각으로 바꾼다
	private long orderAt(long memberId, long raceCourseId, LocalDateTime orderedAt) throws Exception {
		QueueEntry.enter(mockMvc, memberId, raceCourseId);
		long participationId = read(mockMvc.perform(post("/api/v1/order/participations")
			.header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{ "raceCourseId": %d, "thirdPartyConsent": true,
					"participant": { "name": "김러너", "birth": "1994-06-25", "gender": "MALE",
						"phone": "010-1234-5678", "emergencyContactName": "김가족",
						"emergencyContactPhone": "010-9876-5432", "tshirtSize": "L",
						"shippingAddress": { "recipientName": "김러너", "recipientPhone": "010-1234-5678",
							"zipcode": "04700", "address": "서울 성동구 왕십리로 00", "addressDetail": "101동 1001호" } } }
				""".formatted(raceCourseId))));
		long orderId = read(mockMvc.perform(post(BASE_URL)
			.header("X-Member-Id", memberId)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"participationId\": %d}".formatted(participationId))));
		Order order = orderRepository.findById(orderId).orElseThrow();
		ReflectionTestUtils.setField(order, "createDate", orderedAt);
		orderRepository.save(order);
		return orderId;
	}

	private long read(ResultActions result) throws Exception {
		String body = result.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(body, "$.data.id").longValue();
	}
}
