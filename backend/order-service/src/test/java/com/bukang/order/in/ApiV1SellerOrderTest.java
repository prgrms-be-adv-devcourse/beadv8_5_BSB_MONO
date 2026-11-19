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
 * 판매자 주문 목록·파티별 내역 API (BUK-227), 기간 판매 합계 (BUK-247). 승인된 판매자만 쓰고, 자기 대회 것만 보인다
 */
// 주문 상세의 「배송 조회」 링크를 만들려고 Delivery Tracker Client ID를 준다 (CRU-40)
@SpringBootTest(properties = "order.clients.delivery-tracker.client-id=test-client")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubSeatHoldPortConfig.class, StubWalletPortConfig.class,
	StubRefundEligibilityPortConfig.class, StubSellerLookupPortConfig.class})
class ApiV1SellerOrderTest {
	private static final String ORDERS_URL = "/api/v1/order/seller/orders";
	private static final String PARTIES_URL = "/api/v1/order/seller/parties";
	private static final String SALES_URL = "/api/v1/order/seller/orders/summary";
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
	@DisplayName("keyword 한 칸으로 참가자 이름 또는 주문번호를 부분 일치 검색한다. 없는 말은 빈 목록이고 다른 조건과 함께 걸린다")
	void keywordSearch() throws Exception {
		setUpCourses();
		long first = paidOrder(BUYER_ID, COURSE_ID);
		paidOrder(BUYER_ID + 1, COURSE_ID);
		String firstOrderNo = jdbcTemplate.queryForObject("select order_no from order_order where id = ?",
			String.class, first);

		// 참가자 이름은 두 주문 모두 "김러너"라 이름으로 찾으면 둘 다, 부분 일치도 된다
		orders(SELLER_MEMBER_ID, "keyword", "김러너")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(2));
		orders(SELLER_MEMBER_ID, "keyword", "러너")
			.andExpect(jsonPath("$.data.totalElements").value(2));
		// 주문번호로 찾으면 그 주문 하나만
		orders(SELLER_MEMBER_ID, "keyword", firstOrderNo)
			.andExpect(jsonPath("$.data.totalElements").value(1))
			.andExpect(jsonPath("$.data.orders[0].orderItemId").value(itemIdOf(first)));
		// 없는 말은 빈 목록
		orders(SELLER_MEMBER_ID, "keyword", "박파티")
			.andExpect(jsonPath("$.data.totalElements").value(0));
		// 빈 keyword는 거르지 않는다
		orders(SELLER_MEMBER_ID, "keyword", "   ")
			.andExpect(jsonPath("$.data.totalElements").value(2));
		// 다른 조건과 함께 걸린다: 주문번호가 맞아도 다른 종목으로 거르면 0
		orders(SELLER_MEMBER_ID, "keyword", firstOrderNo, "raceCourseId", String.valueOf(OTHER_COURSE_ID))
			.andExpect(jsonPath("$.data.totalElements").value(0));
		// 자기 대회만: 남의 판매자는 이 주문번호로 못 찾는다
		orders(OTHER_SELLER_MEMBER_ID, "keyword", firstOrderNo)
			.andExpect(jsonPath("$.data.totalElements").value(0));
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

	@Test
	@DisplayName("주문 상세는 자기 대회 주문 상품의 금액·배송지·배송 요청사항·송장·배송 조회를 돌려준다 (CRU-39)")
	void detailOfShippedKit() throws Exception {
		setUpCourses();
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null, LocalDate.now().minusDays(1)));
		long orderId = paidOrder(BUYER_ID, COURSE_ID);
		long itemId = itemIdOf(orderId);
		jdbcTemplate.update("update order_shipment set delivery_memo = ? where order_id = ?", "문 앞에 놓아 주세요",
			orderId);
		shipKit(itemId).andExpect(status().isOk());

		detail(SELLER_MEMBER_ID, itemId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("주문 상세를 조회했습니다."))
			.andExpect(jsonPath("$.data.orderItemId").value(itemId))
			.andExpect(jsonPath("$.data.orderId").value(orderId))
			.andExpect(jsonPath("$.data.orderNo").isString())
			.andExpect(jsonPath("$.data.orderedAt").isString())
			.andExpect(jsonPath("$.data.paidAt").isString())
			.andExpect(jsonPath("$.data.raceId").value(RACE_ID))
			.andExpect(jsonPath("$.data.raceCourseId").value(COURSE_ID))
			.andExpect(jsonPath("$.data.courseName").value("테스트 대회 종목 " + COURSE_ID))
			.andExpect(jsonPath("$.data.status").value("PAID"))
			.andExpect(jsonPath("$.data.participantName").value("김러너"))
			.andExpect(jsonPath("$.data.tshirtSize").value("L"))
			.andExpect(jsonPath("$.data.kitOptions").isArray())
			.andExpect(jsonPath("$.data.unitPrice").value(StubRaceCoursePort.PRICE))
			.andExpect(jsonPath("$.data.quantity").value(1))
			.andExpect(jsonPath("$.data.amount").value(StubRaceCoursePort.PRICE))
			.andExpect(jsonPath("$.data.canceledAt").doesNotExist())
			.andExpect(jsonPath("$.data.canceledAmount").value(0))
			.andExpect(jsonPath("$.data.cancelReason").doesNotExist())
			// 연락처는 키트 배송 목록과 같이 풀어서 준다
			.andExpect(jsonPath("$.data.shippingAddress.recipientPhone").value("010-1234-5678"))
			.andExpect(jsonPath("$.data.shippingAddress.addressDetail").value("101동 1001호"))
			.andExpect(jsonPath("$.data.deliveryMemo").value("문 앞에 놓아 주세요"))
			.andExpect(jsonPath("$.data.kitDeliveryStatus").value("SHIPPING"))
			.andExpect(jsonPath("$.data.kitCarrier").value("CJ대한통운"))
			.andExpect(jsonPath("$.data.kitTrackingNumber").value("684912345678"))
			.andExpect(jsonPath("$.data.kitShippedAt").isString())
			.andExpect(jsonPath("$.data.kitTracking.status").value("NOT_TRACKED"))
			.andExpect(jsonPath("$.data.kitTracking.events").isArray())
			.andExpect(jsonPath("$.data.kitTrackingUrl").value("https://link.tracker.delivery/track"
				+ "?client_id=test-client&carrier_id=kr.cjlogistics&tracking_number=684912345678"));
	}

	@Test
	@DisplayName("주문 상세: 보내기 전에 취소된 키트 주문도 열리고 취소 시각·환불 금액·취소 이유가 보이며 배송지는 가린다")
	void detailOfCanceledBeforeShipping() throws Exception {
		setUpCourses();
		long orderId = paidOrder(BUYER_ID, COURSE_ID);
		long itemId = itemIdOf(orderId);
		mockMvc.perform(post("/api/v1/order/orders/" + orderId + "/items/" + itemId + "/cancel")
				.header("X-Member-Id", BUYER_ID))
			.andExpect(status().isOk());

		detail(SELLER_MEMBER_ID, itemId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("CANCELED"))
			.andExpect(jsonPath("$.data.amount").value(StubRaceCoursePort.PRICE))
			.andExpect(jsonPath("$.data.canceledAt").isString())
			.andExpect(jsonPath("$.data.canceledAmount").value(StubRaceCoursePort.PRICE))
			.andExpect(jsonPath("$.data.cancelReason").value("BUYER_CANCELED"))
			.andExpect(jsonPath("$.data.kitDeliveryStatus").value("PREPARING"))
			.andExpect(jsonPath("$.data.kitTrackingNumber").doesNotExist())
			.andExpect(jsonPath("$.data.kitTrackingUrl").doesNotExist())
			.andExpect(jsonPath("$.data.shippingAddress").doesNotExist())
			.andExpect(jsonPath("$.data.deliveryMemo").doesNotExist());
	}

	@Test
	@DisplayName("주문 상세: 키트 없는 참가권은 배송 칸이 모두 비고, 결제 전 주문은 결제 시각이 없다")
	void detailWithoutKit() throws Exception {
		setUpCourses();
		raceCoursePort.put(StubRaceCoursePort.courseWithoutKit(COURSE_ID + 2, RACE_ID));
		long kitless = itemIdOf(paidOrder(BUYER_ID, COURSE_ID + 2));
		long pending = itemIdOf(createdOrder(BUYER_ID + 1, COURSE_ID));

		detail(SELLER_MEMBER_ID, kitless)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("PAID"))
			.andExpect(jsonPath("$.data.kitOptions.length()").value(0))
			.andExpect(jsonPath("$.data.shippingAddress").doesNotExist())
			.andExpect(jsonPath("$.data.deliveryMemo").doesNotExist())
			.andExpect(jsonPath("$.data.kitDeliveryStatus").doesNotExist())
			.andExpect(jsonPath("$.data.kitTracking").doesNotExist())
			.andExpect(jsonPath("$.data.kitTrackingUrl").doesNotExist());
		detail(SELLER_MEMBER_ID, pending)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("ORDERED"))
			.andExpect(jsonPath("$.data.paidAt").doesNotExist())
			.andExpect(jsonPath("$.data.kitDeliveryStatus").doesNotExist());
	}

	@Test
	@DisplayName("주문 상세: 다른 판매자 대회·판매자 아님은 403, 없는 ID·만료된 주문은 404, 헤더 없으면 401이다")
	void detailRejected() throws Exception {
		setUpCourses();
		long itemId = itemIdOf(paidOrder(BUYER_ID, COURSE_ID));
		long expiredOrderId = createdOrder(BUYER_ID + 1, COURSE_ID);
		long expiredItemId = itemIdOf(expiredOrderId);
		LocalDateTime due = orderRepository.findById(expiredOrderId).orElseThrow().getPaymentDueAt();
		orderExpiryUseCase.expireOverdue(due.plusSeconds(1));

		detail(OTHER_SELLER_MEMBER_ID, itemId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORDER_SELLER_FORBIDDEN"))
			.andExpect(jsonPath("$.message").value("본인 대회의 주문만 볼 수 있습니다."));
		detail(NOT_SELLER_MEMBER_ID, itemId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("승인된 판매자만 쓸 수 있습니다."));
		detail(SELLER_MEMBER_ID, 999_999)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ORDER_ITEM_NOT_FOUND"));
		detail(SELLER_MEMBER_ID, expiredItemId)
			.andExpect(status().isNotFound());
		mockMvc.perform(get(ORDERS_URL + "/" + itemId))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions detail(long memberId, long orderItemId) throws Exception {
		return mockMvc.perform(get(ORDERS_URL + "/" + orderItemId).header("X-Member-Id", memberId));
	}

	private ResultActions shipKit(long orderItemId) throws Exception {
		return mockMvc.perform(post("/api/v1/order/seller/kits/" + orderItemId + "/shipment")
			.header("X-Member-Id", SELLER_MEMBER_ID)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"carrierId\": \"kr.cjlogistics\", \"trackingNumber\": \"684912345678\"}"));
	}

	@Test
	@DisplayName("판매는 결제일이 기간 안인 결제 완료·취소 중만, 취소는 취소를 마친 시각이 기간 안인 것만 센다. 결제 전·다른 판매자·기간 밖은 뺀다")
	void salesSummary() throws Exception {
		setUpCourses();
		long monthStart = paidOrder(BUYER_ID, COURSE_ID);
		long lastMoment = paidOrder(BUYER_ID + 1, COURSE_ID);
		long canceling = paidOrder(BUYER_ID + 2, COURSE_ID);
		long canceled = paidOrder(BUYER_ID + 3, COURSE_ID);
		long lastMonth = paidOrder(BUYER_ID + 4, COURSE_ID);
		createdOrder(BUYER_ID + 5, COURSE_ID);
		long otherSeller = paidOrder(BUYER_ID + 6, OTHER_COURSE_ID);
		long paidOctCanceledNov = paidOrder(BUYER_ID + 7, COURSE_ID);
		long canceledLastMonth = paidOrder(BUYER_ID + 8, COURSE_ID);
		paidAt(monthStart, "2026-11-01T00:00:00");
		paidAt(lastMoment, "2026-11-16T23:59:59");
		paidAt(canceling, "2026-11-10T12:00:00");
		paidAt(canceled, "2026-11-10T12:00:00");
		paidAt(lastMonth, "2026-10-31T23:59:59");
		paidAt(otherSeller, "2026-11-10T12:00:00");
		paidAt(paidOctCanceledNov, "2026-10-20T12:00:00");
		paidAt(canceledLastMonth, "2026-10-05T12:00:00");
		itemStatus(canceling, "CANCELING");
		// 취소는 결제일이 아니라 취소를 마친 시각으로 센다. 월초 0시는 들고, 지난달 말일 끝은 지난달이다
		canceledAt(canceled, "2026-11-12T09:00:00");
		canceledAt(paidOctCanceledNov, "2026-11-01T00:00:00");
		canceledAt(canceledLastMonth, "2026-10-31T23:59:59");
		long price = jdbcTemplate.queryForObject("select amount from order_order_item where order_id = ?", Long.class,
			monthStart);

		sales(SELLER_MEMBER_ID, "2026-11-01", "2026-11-16")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("판매 합계를 조회했습니다."))
			.andExpect(jsonPath("$.data.soldCount").value(3))
			.andExpect(jsonPath("$.data.soldAmount").value(price * 3))
			// 취소 중은 아직 취소로 세지 않는다
			.andExpect(jsonPath("$.data.canceledCount").value(2))
			.andExpect(jsonPath("$.data.refundedAmount").value(price * 2));
		sales(SELLER_MEMBER_ID, "2026-10-01", "2026-10-31")
			.andExpect(jsonPath("$.data.soldCount").value(1))
			.andExpect(jsonPath("$.data.soldAmount").value(price))
			.andExpect(jsonPath("$.data.canceledCount").value(1))
			.andExpect(jsonPath("$.data.refundedAmount").value(price));
		sales(SELLER_MEMBER_ID, "2026-09-01", "2026-09-30")
			.andExpect(jsonPath("$.data.soldCount").value(0))
			.andExpect(jsonPath("$.data.soldAmount").value(0))
			.andExpect(jsonPath("$.data.canceledCount").value(0))
			.andExpect(jsonPath("$.data.refundedAmount").value(0));
		sales(OTHER_SELLER_MEMBER_ID, "2026-11-01", "2026-11-16")
			.andExpect(jsonPath("$.data.soldCount").value(1))
			.andExpect(jsonPath("$.data.canceledCount").value(0));
		sales(SELLER_MEMBER_ID, "2026-11-16", "2026-11-01")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ORDER_INVALID_SEARCH_PERIOD"));
		sales(SELLER_MEMBER_ID, "2025-11-01", "2026-11-02")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("조회 기간은 한 번에 12개월까지입니다."));
		sales(NOT_SELLER_MEMBER_ID, "2026-11-01", "2026-11-16")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORDER_SELLER_FORBIDDEN"));
		mockMvc.perform(get(SALES_URL).header("X-Member-Id", SELLER_MEMBER_ID).param("to", "2026-11-16"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("필수 요청 값(from)이 없습니다."));
	}

	private void paidAt(long orderId, String paidAt) {
		jdbcTemplate.update("update order_order set paid_at = ? where id = ?", LocalDateTime.parse(paidAt), orderId);
	}

	private void canceledAt(long orderId, String canceledAt) {
		jdbcTemplate.update("update order_order_item set status = 'CANCELED', canceled_amount = amount, "
			+ "canceled_at = ? where order_id = ?", LocalDateTime.parse(canceledAt), orderId);
	}

	private void itemStatus(long orderId, String itemStatus) {
		jdbcTemplate.update("update order_order_item set status = ? where order_id = ?", itemStatus, orderId);
	}

	private ResultActions sales(long memberId, String from, String to) throws Exception {
		return mockMvc.perform(get(SALES_URL).header("X-Member-Id", memberId).param("from", from).param("to", to));
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
