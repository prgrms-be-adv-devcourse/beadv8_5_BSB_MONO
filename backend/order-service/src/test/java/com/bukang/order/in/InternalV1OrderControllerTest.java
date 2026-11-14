package com.bukang.order.in;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import com.bukang.order.domain.ParticipationKitOption;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationKitOptionRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.QueueAdmissionRepository;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.support.QueueEntry;
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
	private static final long COURSE_1115 = 10;
	private static final long COURSE_1116 = 11;
	// 오늘 0시 대회 종목과 어제 23:59 대회 종목 (끝나지 않은 참가권 수 조회)
	private static final long COURSE_TODAY = 12;
	private static final long COURSE_YESTERDAY = 13;
	private static final long COURSE_UNPAID = 14;
	private static final long COURSE_OVERDUE = 15;
	// 판매 집계: 11/15 대회(100)의 다른 코스와, 같은 대회에 다른 판매자가 판 코스
	private static final long COURSE_1115_HALF = 16;
	private static final long COURSE_1115_OTHER_SELLER = 17;
	private static final long OTHER_SELLER_ID = 8;
	private static final String SUMMARY_URL = "/internal/v1/order/sold-summary";
	private static final long TICKET_PRICE = StubRaceCoursePort.PRICE;
	// 주문 요약: 대회명·종목명을 주는 키트 종목과, 이름을 주지 않는 키트 없는 종목
	private static final long COURSE_NAMED = 18;
	private static final long COURSE_NO_KIT = 19;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private ParticipationRepository participationRepository;

	@Autowired
	private QueueAdmissionRepository queueAdmissionRepository;

	@Autowired
	private ParticipationKitOptionRepository participationKitOptionRepository;

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
		participationKitOptionRepository.deleteAll();
		queueAdmissionRepository.deleteAll();
		raceCoursePort.clear();
		seatHoldPort.clear();
		walletPort.clear();
	}

	@Test
	@DisplayName("11/15 대회 참가권 3장 중 1장을 취소하면 그 대회 조회에 2장만 정산 필드로 나온다")
	void soldEntriesExcludeCanceled() throws Exception {
		long first = paidOrderItem(1, COURSE_1115);
		long canceled = paidOrderItem(2, COURSE_1115);
		long third = paidOrderItem(3, COURSE_1115);
		// 취소 API는 다음 작업이라, 주문 상품을 직접 취소 상태로 만든다
		markCanceled(canceled);

		findSoldEntries(100)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("판매 내역을 조회했습니다."))
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].orderItemId").value(first))
			.andExpect(jsonPath("$.data[0].sellerId").value(StubRaceCoursePort.SELLER_ID))
			.andExpect(jsonPath("$.data[0].raceId").value(100))
			.andExpect(jsonPath("$.data[0].raceCourseId").value(COURSE_1115))
			.andExpect(jsonPath("$.data[0].raceDate").value("2026-11-15"))
			.andExpect(jsonPath("$.data[0].price").value(TICKET_PRICE))
			.andExpect(jsonPath("$.data[0].status").value("PAID"))
			.andExpect(jsonPath("$.data[1].orderItemId").value(third));
	}

	@Test
	@DisplayName("결제 전(CREATED)·만료(EXPIRED) 주문 상품과 다른 대회의 참가권은 나오지 않는다")
	void soldEntriesOnlyPaidOfThatRace() throws Exception {
		long paid = paidOrderItem(1, COURSE_1115);
		createdOrder(2, COURSE_1115);
		createdOrder(3, COURSE_1115);
		orderExpiryUseCase.expireOverdue(LocalDateTime.now().plusHours(1));
		createdOrder(4, COURSE_1115);
		long otherRace = paidOrderItem(1, COURSE_1116);

		findSoldEntries(100)
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].orderItemId").value(paid));
		findSoldEntries(101)
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].orderItemId").value(otherRace));
		// 판매가 없거나 없는 대회는 빈 목록이다
		findSoldEntries(999)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	@DisplayName("주문 상품에 복사된 대회일이 달라져도 대회 ID로 찾고, 그 대회일을 그대로 준다")
	void soldEntriesByRaceIdRegardlessOfRaceDate() throws Exception {
		long moved = paidOrderItem(1, COURSE_1115);
		changeRaceDate(moved, LocalDateTime.of(2026, 11, 22, 8, 0));

		findSoldEntries(100)
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].orderItemId").value(moved))
			.andExpect(jsonPath("$.data[0].raceDate").value("2026-11-22"));
	}

	@Test
	@DisplayName("대회 ID가 없거나 형식이 틀리면 400이다")
	void invalidRaceId() throws Exception {
		mockMvc.perform(get(URL))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("필수 요청 값(raceId)이 없습니다."))
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mockMvc.perform(get(URL).param("raceId", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."))
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("끝나지 않은 참가권은 내 결제 완료 참가권 중 대회가 오늘 이후(오늘 0시 포함)인 것만 센다")
	void countActiveTickets() throws Exception {
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul")); // 컨트롤러가 한국 날짜로 「오늘」을 잡는다
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

	@Test
	@DisplayName("주문 요약: 결제한 키트 종목은 대회명·종목명, 고른 키트 옵션, 배송 준비 상태를 준다")
	void orderLookupWithKit() throws Exception {
		RaceCourseInfo base = courseOn(COURSE_NAMED, 102, LocalDateTime.of(2026, 11, 20, 8, 0));
		raceCoursePort.put(new RaceCourseInfo(base.raceCourseId(), base.raceId(), base.raceDate(),
			base.applicationOpenAt(), base.applicationCloseAt(), base.minAge(), base.maxAge(), base.sellerId(),
			base.courseName(), base.price(), base.kitIncluded(), base.refundDeadline(), base.scheduleChangedAt(),
			false, base.tshirtSizeOption(), null, "서울 마라톤", "10K"));
		long orderItemId = paidOrderItem(5, COURSE_NAMED);
		long participationId = transactionTemplate.execute(status ->
			findOrderItem(orderItemId).getParticipationId());
		participationKitOptionRepository.save(ParticipationKitOption.of(participationId, 1, "T-shirt", "L"));
		participationKitOptionRepository.save(ParticipationKitOption.of(participationId, 2, "양말", "260"));
		long orderId = orderIdOf(orderItemId);

		findOrderLookup(orderId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("주문 요약을 조회했습니다."))
			.andExpect(jsonPath("$.data.memberId").value(5))
			.andExpect(jsonPath("$.data.orderNo").isNotEmpty())
			.andExpect(jsonPath("$.data.paymentAmount").value(TICKET_PRICE))
			.andExpect(jsonPath("$.data.status").value("PAID"))
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].raceId").value(102))
			.andExpect(jsonPath("$.data.items[0].raceName").value("서울 마라톤"))
			.andExpect(jsonPath("$.data.items[0].courseName").value("10K"))
			.andExpect(jsonPath("$.data.items[0].kitSummary").value("T-shirt L, 양말 260"))
			.andExpect(jsonPath("$.data.items[0].deliveryStatus").value("PREPARING"));
	}

	@Test
	@DisplayName("주문 요약: 키트 없는 종목은 키트 칸이 null이고, 상품이 이름을 주지 않으면 상품명으로 대신한다")
	void orderLookupWithoutKit() throws Exception {
		raceCoursePort.put(StubRaceCoursePort.courseWithoutKit(COURSE_NO_KIT, 103));
		long orderId = createdOrder(6, COURSE_NO_KIT);

		findOrderLookup(orderId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("CREATED"))
			.andExpect(jsonPath("$.data.items[0].raceId").value(103))
			.andExpect(jsonPath("$.data.items[0].raceName").value("테스트 대회 종목 " + COURSE_NO_KIT))
			.andExpect(jsonPath("$.data.items[0].courseName").doesNotExist())
			.andExpect(jsonPath("$.data.items[0].kitSummary").doesNotExist())
			.andExpect(jsonPath("$.data.items[0].deliveryStatus").doesNotExist());
	}

	@Test
	@DisplayName("주문 요약: 결제 전 키트 종목은 구매자 주문 상세처럼 배송 준비이고, 상품 서비스가 멈추면 상품명으로 대신한다")
	void orderLookupUnpaidKitWhileProductDown() throws Exception {
		long orderId = createdOrder(7, COURSE_1115);
		raceCoursePort.setUnavailable(true);

		findOrderLookup(orderId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("CREATED"))
			.andExpect(jsonPath("$.data.items[0].raceId").value(100))
			.andExpect(jsonPath("$.data.items[0].raceName").value("테스트 대회 종목 " + COURSE_1115))
			.andExpect(jsonPath("$.data.items[0].courseName").doesNotExist())
			.andExpect(jsonPath("$.data.items[0].kitSummary").value("키트"))
			.andExpect(jsonPath("$.data.items[0].deliveryStatus").value("PREPARING"));
	}

	@Test
	@DisplayName("주문 요약: 없는 주문이면 404와 ORDER_NOT_FOUND")
	void orderLookupNotFound() throws Exception {
		findOrderLookup(999_999)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
	}

	private ResultActions findOrderLookup(long orderId) throws Exception {
		return mockMvc.perform(get("/internal/v1/order/orders/" + orderId + "/summary"));
	}

	private long orderIdOf(long orderItemId) {
		return transactionTemplate.execute(status -> findOrderItem(orderItemId).getOrder().getId());
	}

	private ResultActions summarizeSold(long raceId) throws Exception {
		return mockMvc.perform(get(SUMMARY_URL).param("raceId", String.valueOf(raceId)));
	}

	// 11/15 대회(100)에 코스를 둘 더한다: 같은 판매자의 다른 코스, 다른 판매자(8)의 코스
	private void putSummaryCourses() {
		LocalDateTime raceDate = LocalDateTime.of(2026, 11, 15, 8, 0);
		raceCoursePort.put(courseOn(COURSE_1115_HALF, 100, raceDate));
		raceCoursePort.put(courseOf(COURSE_1115_OTHER_SELLER, 100, raceDate, OTHER_SELLER_ID));
	}

	private ResultActions countActiveTickets(long memberId) throws Exception {
		return mockMvc.perform(get("/internal/v1/order/members/{memberId}/active-tickets/count", memberId));
	}

	private ResultActions findSoldEntries(long raceId) throws Exception {
		return mockMvc.perform(get(URL).param("raceId", String.valueOf(raceId)));
	}

	private static RaceCourseInfo courseOn(long raceCourseId, long raceId, LocalDateTime raceDate) {
		return courseOf(raceCourseId, raceId, raceDate, StubRaceCoursePort.SELLER_ID);
	}

	private static RaceCourseInfo courseOf(long raceCourseId, long raceId, LocalDateTime raceDate, long sellerId) {
		LocalDateTime now = LocalDateTime.now();
		return new RaceCourseInfo(raceCourseId, raceId, raceDate, now.minusDays(1), now.plusDays(30), 14, null,
			sellerId, "테스트 대회 종목 " + raceCourseId, StubRaceCoursePort.PRICE,
			true, raceDate.toLocalDate().minusDays(10), null);
	}

	private void changeRaceDate(long orderItemId, LocalDateTime raceDate) {
		transactionTemplate.executeWithoutResult(status -> ReflectionTestUtils.setField(
			findOrderItem(orderItemId), "raceDate", raceDate));
	}

	private OrderItem findOrderItem(long orderItemId) {
		return orderRepository.findAll().stream()
			.flatMap(order -> order.getItems().stream())
			.filter(candidate -> candidate.getId() == orderItemId)
			.findFirst()
			.orElseThrow();
	}

	private void makeOverdue(long orderId) {
		transactionTemplate.executeWithoutResult(status -> ReflectionTestUtils.setField(
			orderRepository.findById(orderId).orElseThrow(), "paymentDueAt", LocalDateTime.now().minusMinutes(1)));
	}

	private void markCanceled(long orderItemId) {
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
	private long paidOrderItem(long memberId, long raceCourseId) throws Exception {
		long orderId = createdOrder(memberId, raceCourseId);
		mockMvc.perform(post("/api/v1/order/orders/" + orderId + "/payment").header("X-Member-Id", memberId))
			.andExpect(status().isOk());
		return transactionTemplate.execute(status ->
			orderRepository.findById(orderId).orElseThrow().getItems().getFirst().getId());
	}

	// 신청서 → [결제하기]까지만 한 결제 대기 주문 ID
	private long createdOrder(long memberId, long raceCourseId) throws Exception {
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
		return read(mockMvc.perform(post("/api/v1/order/orders")
			.header("X-Member-Id", memberId)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"participationId\": %d}".formatted(participationId))));
	}

	private long read(ResultActions result) throws Exception {
		String body = result.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(body, "$.data.id").longValue();
	}
}
