package com.bukang.order.in;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.app.OrderExpiryUseCase;
import com.bukang.order.domain.OrderItem;
import com.bukang.order.domain.OrderItemStatus;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.support.StubRaceCoursePort;
import com.bukang.order.support.StubRaceCoursePortConfig;
import com.bukang.order.support.StubSeatHoldPort;
import com.bukang.order.support.StubSeatHoldPortConfig;
import com.bukang.order.support.StubWalletPort;
import com.bukang.order.support.StubWalletPortConfig;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubSeatHoldPortConfig.class, StubWalletPortConfig.class})
class InternalV1OrderControllerTest {
	private static final String URL = "/internal/v1/order/sold-entries";
	// 11/15 대회 종목과 11/16 대회 종목
	private static final int COURSE_1115 = 10;
	private static final int COURSE_1116 = 11;
	// 오늘 0시 대회 종목과 어제 23:59 대회 종목 (끝나지 않은 참가권 수 조회)
	private static final int COURSE_TODAY = 12;
	private static final int COURSE_YESTERDAY = 13;
	private static final int COURSE_UNPAID = 14;
	private static final int COURSE_OVERDUE = 15;
	// 판매 집계: 11/15 대회(100)의 다른 코스와, 같은 대회에 다른 판매자가 판 코스
	private static final int COURSE_1115_HALF = 16;
	private static final int COURSE_1115_OTHER_SELLER = 17;
	private static final int OTHER_SELLER_ID = 8;
	private static final String SUMMARY_URL = "/internal/v1/order/sold-summary";
	private static final int TICKET_PRICE = StubRaceCoursePort.PRICE;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private ParticipationRepository participationRepository;

	@Autowired
	private StubRaceCoursePort raceCoursePort;

	@Autowired
	private StubSeatHoldPort seatHoldPort;

	@Autowired
	private StubWalletPort walletPort;

	@Autowired
	private OrderExpiryUseCase orderExpiryUseCase;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@BeforeEach
	void setUp() {
		raceCoursePort.put(courseOn(COURSE_1115, 100, LocalDateTime.of(2026, 11, 15, 8, 0)));
		raceCoursePort.put(courseOn(COURSE_1116, 101, LocalDateTime.of(2026, 11, 16, 23, 59)));
	}

	@AfterEach
	void tearDown() {
		orderRepository.deleteAll();
		participationRepository.deleteAll();
		raceCoursePort.clear();
		seatHoldPort.clear();
		walletPort.clear();
	}

	@Test
	@DisplayName("11/15 대회 참가권 3장 중 1장을 취소하면 11/15 ~ 11/15 조회에 2장만 정산 필드로 나온다")
	void soldEntriesExcludeCanceled() throws Exception {
		int first = paidOrderItem(1, COURSE_1115);
		int canceled = paidOrderItem(2, COURSE_1115);
		int third = paidOrderItem(3, COURSE_1115);
		// 취소 API는 다음 작업이라, 주문 상품을 직접 취소 상태로 만든다
		markCanceled(canceled);

		findSoldEntries("2026-11-15", "2026-11-15")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("판매 내역을 조회했습니다."))
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].orderItemId").value(first))
			.andExpect(jsonPath("$.data[0].sellerId").value(StubRaceCoursePort.SELLER_ID))
			.andExpect(jsonPath("$.data[0].raceId").value(100))
			.andExpect(jsonPath("$.data[0].raceCourseId").value(COURSE_1115))
			.andExpect(jsonPath("$.data[0].raceDate").value("2026-11-15"))
			.andExpect(jsonPath("$.data[0].price").value(TICKET_PRICE))
			.andExpect(jsonPath("$.data[1].orderItemId").value(third));
	}

	@Test
	@DisplayName("결제 전(CREATED)·만료(EXPIRED) 주문 상품과 다른 날 대회는 나오지 않는다")
	void soldEntriesOnlyPaidOnThatDay() throws Exception {
		int paid = paidOrderItem(1, COURSE_1115);
		createdOrder(2, COURSE_1115);
		createdOrder(3, COURSE_1115);
		orderExpiryUseCase.expireOverdue(LocalDateTime.now().plusHours(1));
		createdOrder(4, COURSE_1115);
		paidOrderItem(1, COURSE_1116);

		findSoldEntries("2026-11-15", "2026-11-15")
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].orderItemId").value(paid));
		// 11/16 23:59 대회는 그날에 들어간다
		findSoldEntries("2026-11-16", "2026-11-16")
			.andExpect(jsonPath("$.data.length()").value(1));
		findSoldEntries("2026-11-17", "2026-11-17")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	@DisplayName("기간으로 물으면 양 끝 날짜의 대회를 모두 포함해 주문 상품 ID 순으로 돌려주고, 참가권마다 대회일을 준다")
	void soldEntriesInPeriod() throws Exception {
		int on1115 = paidOrderItem(1, COURSE_1115);
		int on1116 = paidOrderItem(2, COURSE_1116);

		findSoldEntries("2026-11-15", "2026-11-16")
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].orderItemId").value(on1115))
			.andExpect(jsonPath("$.data[0].raceDate").value("2026-11-15"))
			.andExpect(jsonPath("$.data[1].orderItemId").value(on1116))
			.andExpect(jsonPath("$.data[1].raceDate").value("2026-11-16"));
		findSoldEntries("2026-11-09", "2026-11-15")
			.andExpect(jsonPath("$.data.length()").value(1));
	}

	@Test
	@DisplayName("기간이 없거나 형식이 틀리거나, 시작일이 종료일보다 늦거나, 31일을 넘으면 400이다")
	void invalidPeriod() throws Exception {
		mockMvc.perform(get(URL).param("to", "2026-11-15"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("필수 요청 값(from)이 없습니다."))
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		findSoldEntries("2026/11/15", "2026-11-15")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."))
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		findSoldEntries("2026-11-16", "2026-11-15")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("조회 시작일이 종료일보다 늦습니다."))
			.andExpect(jsonPath("$.code").value("ORDER_INVALID_SOLD_ENTRY_PERIOD"));
		// 10/16 ~ 11/15는 31일, 10/15 ~ 11/15는 32일
		findSoldEntries("2026-10-16", "2026-11-15")
			.andExpect(status().isOk());
		findSoldEntries("2026-10-15", "2026-11-15")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("판매 내역은 한 번에 31일까지 조회할 수 있습니다."))
			.andExpect(jsonPath("$.code").value("ORDER_INVALID_SOLD_ENTRY_PERIOD"));
	}

	@Test
	@DisplayName("끝나지 않은 참가권은 내 결제 완료 참가권 중 대회가 오늘 이후(오늘 0시 포함)인 것만 센다")
	void countActiveTickets() throws Exception {
		LocalDate today = LocalDate.now();
		// 지난 대회는 신청·결제가 막히므로, 앞날 종목으로 결제한 뒤 주문 상품의 대회 일시 사본만 바꾼다
		raceCoursePort.put(courseOn(COURSE_TODAY, 102, LocalDateTime.of(2026, 11, 20, 8, 0)));
		raceCoursePort.put(courseOn(COURSE_YESTERDAY, 103, LocalDateTime.of(2026, 11, 21, 8, 0)));
		raceCoursePort.put(courseOn(COURSE_UNPAID, 104, LocalDateTime.of(2026, 11, 22, 8, 0)));
		raceCoursePort.put(courseOn(COURSE_OVERDUE, 105, LocalDateTime.of(2026, 11, 23, 8, 0)));
		paidOrderItem(1, COURSE_1115);
		changeRaceDate(paidOrderItem(1, COURSE_TODAY), today.atStartOfDay());
		// 결제 기한 안의 결제 대기 주문도 센다 (탈퇴 뒤 남은 Access Token으로 결제하는 것을 막기 위해)
		createdOrder(1, COURSE_UNPAID);
		// 지난 대회, 취소(환불)한 참가권, 결제 기한이 지난 결제 대기 주문, 다른 회원의 참가권은 세지 않는다
		changeRaceDate(paidOrderItem(1, COURSE_YESTERDAY), today.minusDays(1).atTime(23, 59));
		markCanceled(paidOrderItem(1, COURSE_1116));
		makeOverdue(createdOrder(1, COURSE_OVERDUE));
		paidOrderItem(2, COURSE_1115);

		countActiveTickets(1)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("끝나지 않은 참가권 수를 조회했습니다."))
			.andExpect(jsonPath("$.data.count").value(3));
		countActiveTickets(3)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.count").value(0));
	}

	// ---------- 1차 정산용 판매 집계 (PRO-231) ----------

	@Test
	@DisplayName("대회 100의 결제 참가권을 판매자 × 코스로 묶어 결제 금액 합과 장수를 판매자 · 코스 순으로 돌려준다")
	void soldSummaryGroupsBySellerAndCourse() throws Exception {
		putSummaryCourses();
		paidOrderItem(1, COURSE_1115);
		paidOrderItem(2, COURSE_1115);
		paidOrderItem(3, COURSE_1115_HALF);
		paidOrderItem(4, COURSE_1115_OTHER_SELLER);

		summarizeSold(100)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("판매 집계를 조회했습니다."))
			.andExpect(jsonPath("$.data.length()").value(3))
			.andExpect(jsonPath("$.data[0].sellerId").value(StubRaceCoursePort.SELLER_ID))
			.andExpect(jsonPath("$.data[0].raceId").value(100))
			.andExpect(jsonPath("$.data[0].raceCourseId").value(COURSE_1115))
			.andExpect(jsonPath("$.data[0].priceSum").value(TICKET_PRICE * 2))
			.andExpect(jsonPath("$.data[0].ticketCount").value(2))
			.andExpect(jsonPath("$.data[1].sellerId").value(StubRaceCoursePort.SELLER_ID))
			.andExpect(jsonPath("$.data[1].raceCourseId").value(COURSE_1115_HALF))
			.andExpect(jsonPath("$.data[1].priceSum").value(TICKET_PRICE))
			.andExpect(jsonPath("$.data[1].ticketCount").value(1))
			.andExpect(jsonPath("$.data[2].sellerId").value(OTHER_SELLER_ID))
			.andExpect(jsonPath("$.data[2].raceCourseId").value(COURSE_1115_OTHER_SELLER))
			.andExpect(jsonPath("$.data[2].priceSum").value(TICKET_PRICE))
			.andExpect(jsonPath("$.data[2].ticketCount").value(1));
	}

	@Test
	@DisplayName("취소(CANCELED) · 결제 대기 · 만료 주문 상품과 다른 대회(101)는 집계에서 빠진다")
	void soldSummaryOnlyPaidOfThatRace() throws Exception {
		paidOrderItem(1, COURSE_1115);
		markCanceled(paidOrderItem(2, COURSE_1115));
		createdOrder(3, COURSE_1115);
		orderExpiryUseCase.expireOverdue(LocalDateTime.now().plusHours(1));
		createdOrder(4, COURSE_1115);
		paidOrderItem(5, COURSE_1116);

		summarizeSold(100)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].raceCourseId").value(COURSE_1115))
			.andExpect(jsonPath("$.data[0].priceSum").value(TICKET_PRICE))
			.andExpect(jsonPath("$.data[0].ticketCount").value(1));
	}

	@Test
	@DisplayName("판매가 없는 대회는 200과 빈 목록이다")
	void soldSummaryEmpty() throws Exception {
		summarizeSold(999)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	@DisplayName("raceId가 없으면 400 '필수 요청 값(raceId)이 없습니다.'이고, 숫자가 아니면 400 형식 오류다")
	void soldSummaryInvalidRaceId() throws Exception {
		mockMvc.perform(get(SUMMARY_URL))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value("필수 요청 값(raceId)이 없습니다."));
		mockMvc.perform(get(SUMMARY_URL).param("raceId", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."));
	}

	private ResultActions summarizeSold(int raceId) throws Exception {
		return mockMvc.perform(get(SUMMARY_URL).param("raceId", String.valueOf(raceId)));
	}

	// 11/15 대회(100)에 코스를 둘 더한다: 같은 판매자의 다른 코스, 다른 판매자(8)의 코스
	private void putSummaryCourses() {
		LocalDateTime raceDate = LocalDateTime.of(2026, 11, 15, 8, 0);
		raceCoursePort.put(courseOn(COURSE_1115_HALF, 100, raceDate));
		raceCoursePort.put(courseOf(COURSE_1115_OTHER_SELLER, 100, raceDate, OTHER_SELLER_ID));
	}

	private ResultActions countActiveTickets(int memberId) throws Exception {
		return mockMvc.perform(get("/internal/v1/order/members/{memberId}/active-tickets/count", memberId));
	}

	private ResultActions findSoldEntries(String from, String to) throws Exception {
		return mockMvc.perform(get(URL).param("from", from).param("to", to));
	}

	private static RaceCourseInfo courseOn(int raceCourseId, int raceId, LocalDateTime raceDate) {
		return courseOf(raceCourseId, raceId, raceDate, StubRaceCoursePort.SELLER_ID);
	}

	private static RaceCourseInfo courseOf(int raceCourseId, int raceId, LocalDateTime raceDate, int sellerId) {
		LocalDateTime now = LocalDateTime.now();
		return new RaceCourseInfo(raceCourseId, raceId, raceDate, now.minusDays(1), now.plusDays(30), 14, null,
			sellerId, "테스트 대회 종목 " + raceCourseId, StubRaceCoursePort.PRICE,
			true, raceDate.toLocalDate().minusDays(10), null);
	}

	private void changeRaceDate(int orderItemId, LocalDateTime raceDate) {
		transactionTemplate.executeWithoutResult(status -> ReflectionTestUtils.setField(
			findOrderItem(orderItemId), "raceDate", raceDate));
	}

	private OrderItem findOrderItem(int orderItemId) {
		return orderRepository.findAll().stream()
			.flatMap(order -> order.getItems().stream())
			.filter(candidate -> candidate.getId() == orderItemId)
			.findFirst()
			.orElseThrow();
	}

	private void makeOverdue(int orderId) {
		transactionTemplate.executeWithoutResult(status -> ReflectionTestUtils.setField(
			orderRepository.findById(orderId).orElseThrow(), "paymentDueAt", LocalDateTime.now().minusMinutes(1)));
	}

	private void markCanceled(int orderItemId) {
		transactionTemplate.executeWithoutResult(status -> {
			OrderItem item = orderRepository.findAll().stream()
				.flatMap(order -> order.getItems().stream())
				.filter(candidate -> candidate.getId() == orderItemId)
				.findFirst()
				.orElseThrow();
			ReflectionTestUtils.setField(item, "status", OrderItemStatus.CANCELED);
		});
	}

	// 신청서 → [결제하기] → 결제까지 마친 주문의 주문 상품 ID
	private int paidOrderItem(int memberId, int raceCourseId) throws Exception {
		int orderId = createdOrder(memberId, raceCourseId);
		mockMvc.perform(post("/api/v1/order/orders/" + orderId + "/payment").header("X-Member-Id", memberId))
			.andExpect(status().isOk());
		return transactionTemplate.execute(status ->
			orderRepository.findById(orderId).orElseThrow().getItems().getFirst().getId());
	}

	// 신청서 → [결제하기]까지만 한 결제 대기 주문 ID
	private int createdOrder(int memberId, int raceCourseId) throws Exception {
		int participationId = read(mockMvc.perform(post("/api/v1/order/participations")
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
		return read(mockMvc.perform(post("/api/v1/order/orders")
			.header("X-Member-Id", memberId)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"participationId\": %d}".formatted(participationId))));
	}

	private int read(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id");
	}
}
