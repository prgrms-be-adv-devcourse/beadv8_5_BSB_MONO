package com.bukang.order.in;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.app.OrderCreateResult;
import com.bukang.order.app.OrderCreateUseCase;
import com.bukang.order.app.OrderPayUseCase;
import com.bukang.order.domain.Order;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.QueueAdmissionRepository;
import com.bukang.order.support.QueueEntry;
import com.bukang.order.support.StubMemberPorts;
import com.bukang.order.support.StubMemberPortsConfig;
import com.bukang.order.support.StubRaceCoursePort;
import com.bukang.order.support.StubRaceCoursePortConfig;
import com.bukang.order.support.StubRefundEligibilityPortConfig;
import com.bukang.order.support.StubSeatHoldPort;
import com.bukang.order.support.StubSeatHoldPortConfig;
import com.bukang.order.support.StubWalletPort;
import com.bukang.order.support.StubWalletPortConfig;
import com.jayway.jsonpath.JsonPath;

/**
 * 관리자 회원·주문 조회 API (BUK-26, 피그마 V2 A04). 관리자만, 닉네임 또는 주문번호 하나로 찾는다
 * 결제 대기·기한 만료·취소한 주문도 보여 준다 (10/30 사용자 결정: 문의 대응용)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubSeatHoldPortConfig.class, StubWalletPortConfig.class,
	StubRefundEligibilityPortConfig.class, StubMemberPortsConfig.class})
class ApiV1AdminOrderTest {
	private static final String BASE_URL = "/api/v1/order/admin/orders";
	// 한 대회에는 한 번만 신청할 수 있어, 한 회원의 주문 두 건은 다른 대회로 만든다
	private static final long FIRST_RACE_ID = 800L;
	private static final long SECOND_RACE_ID = 810L;
	private static final long FIRST_COURSE_ID = 80L;
	private static final long SECOND_COURSE_ID = 81L;
	private static final long ADMIN_ID = 3L;
	private static final long BUYER_ID = 4L;
	private static final long OTHER_BUYER_ID = 5L;
	private static final String BUYER_NICKNAME = "달리는곰";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private ParticipationRepository participationRepository;

	@Autowired
	private QueueAdmissionRepository queueAdmissionRepository;

	@Autowired
	private OrderCreateUseCase orderCreateUseCase;

	@Autowired
	private OrderPayUseCase orderPayUseCase;

	@Autowired
	private StubRaceCoursePort raceCoursePort;

	@Autowired
	private StubSeatHoldPort seatHoldPort;

	@Autowired
	private StubWalletPort walletPort;

	@Autowired
	private StubMemberPorts memberPorts;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@BeforeEach
	void setUp() {
		raceCoursePort.put(StubRaceCoursePort.openCourse(FIRST_COURSE_ID, FIRST_RACE_ID, 14, null));
		raceCoursePort.put(StubRaceCoursePort.openCourse(SECOND_COURSE_ID, SECOND_RACE_ID, 14, null));
		memberPorts.admin(ADMIN_ID);
		memberPorts.member(BUYER_NICKNAME, BUYER_ID);
	}

	@AfterEach
	void tearDown() {
		orderRepository.deleteAll();
		participationRepository.deleteAll();
		queueAdmissionRepository.deleteAll();
		raceCoursePort.clear();
		seatHoldPort.clear();
		walletPort.clear();
		memberPorts.clear();
	}

	@Test
	@DisplayName("닉네임으로 찾으면 그 회원의 주문만 최신순으로 나오고, 결제 대기 주문도 결제일 없이 함께 나온다")
	void byNickname() throws Exception {
		long paidOrderId = createOrder(BUYER_ID, FIRST_COURSE_ID, true);
		long unpaidOrderId = createOrder(BUYER_ID, SECOND_COURSE_ID, false);
		createOrder(OTHER_BUYER_ID, FIRST_COURSE_ID, true);

		search("nickname", " " + BUYER_NICKNAME + " ")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("회원 주문을 조회했습니다."))
			.andExpect(jsonPath("$.data.totalElements").value(2))
			.andExpect(jsonPath("$.data.orders[0].id").value(unpaidOrderId))
			.andExpect(jsonPath("$.data.orders[0].status").value("CREATED"))
			.andExpect(jsonPath("$.data.orders[0].statusLabel").value("결제 대기"))
			.andExpect(jsonPath("$.data.orders[0].paidAt").doesNotExist())
			.andExpect(jsonPath("$.data.orders[1].id").value(paidOrderId))
			.andExpect(jsonPath("$.data.orders[1].status").value("PAID"))
			.andExpect(jsonPath("$.data.orders[1].statusLabel").value("결제 완료"))
			.andExpect(jsonPath("$.data.orders[1].paidAt").isNotEmpty())
			.andExpect(jsonPath("$.data.orders[1].buyerId").value(BUYER_ID))
			.andExpect(jsonPath("$.data.orders[1].itemCount").value(1))
			.andExpect(jsonPath("$.data.orders[1].itemName").isNotEmpty())
			.andExpect(jsonPath("$.data.orders[1].totalAmount").isNumber());
	}

	@Test
	@DisplayName("결제하지 않고 기한이 지난 주문도 기한 만료로 함께 나온다 (문의 대응, 10/30 결정)")
	void includesExpired() throws Exception {
		long expiredOrderId = createOrder(BUYER_ID, FIRST_COURSE_ID, false);
		transactionTemplate.executeWithoutResult(status -> {
			Order order = orderRepository.findById(expiredOrderId).orElseThrow();
			order.expire(order.getPaymentDueAt());
		});

		search("nickname", BUYER_NICKNAME)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(1))
			.andExpect(jsonPath("$.data.orders[0].id").value(expiredOrderId))
			.andExpect(jsonPath("$.data.orders[0].status").value("EXPIRED"))
			.andExpect(jsonPath("$.data.orders[0].statusLabel").value("기한 만료"))
			.andExpect(jsonPath("$.data.orders[0].paidAt").doesNotExist());
	}

	@Test
	@DisplayName("주문번호로 찾으면 그 주문 한 줄만 나오고, 소문자로 넣어도 찾는다. 없는 주문번호는 빈 목록이다")
	void byOrderNo() throws Exception {
		long orderId = createOrder(BUYER_ID, FIRST_COURSE_ID, true);
		createOrder(OTHER_BUYER_ID, FIRST_COURSE_ID, true);
		String orderNo = orderRepository.findById(orderId).map(Order::getOrderNo).orElseThrow();

		search("orderNo", orderNo.toLowerCase())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(1))
			.andExpect(jsonPath("$.data.orders[0].id").value(orderId))
			.andExpect(jsonPath("$.data.orders[0].orderNo").value(orderNo))
			.andExpect(jsonPath("$.data.orders[0].buyerId").value(BUYER_ID));
		search("orderNo", "OD-19990101-0000")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(0));
	}

	@Test
	@DisplayName("관리자가 아니면 403이고 주문을 돌려주지 않는다")
	void notAdmin() throws Exception {
		createOrder(BUYER_ID, FIRST_COURSE_ID, true);

		mockMvc.perform(get(BASE_URL).header("X-Member-Id", BUYER_ID).param("nickname", BUYER_NICKNAME))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORDER_ADMIN_FORBIDDEN"))
			.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	@DisplayName("회원 서비스에 연결하지 못하면 관리자인지 모르므로 503이다. 회원 서비스는 DB 트랜잭션 밖에서 부른다")
	void memberServiceUnavailable() throws Exception {
		search("orderNo", "OD-20261030-0001")
			.andExpect(status().isOk());
		assertThat(memberPorts.askedInTransaction()).containsOnly(false);

		memberPorts.setUnavailable(true);
		search("orderNo", "OD-20261030-0001")
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("ORDER_EXTERNAL_SERVICE_UNAVAILABLE"));
	}

	@Test
	@DisplayName("없는 닉네임이면 404, 닉네임·주문번호가 둘 다 없거나 둘 다 있으면 400이다")
	void invalidSearch() throws Exception {
		search("nickname", "없는회원")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ORDER_MEMBER_NOT_FOUND"));
		mockMvc.perform(get(BASE_URL).header("X-Member-Id", ADMIN_ID).param("nickname", " "))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ORDER_INVALID_ADMIN_SEARCH"));
		mockMvc.perform(get(BASE_URL).header("X-Member-Id", ADMIN_ID)
				.param("nickname", BUYER_NICKNAME).param("orderNo", "OD-20261030-0001"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ORDER_INVALID_ADMIN_SEARCH"));
		search("orderNo", "OD-20261030-0001", "101")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	private ResultActions search(String key, String value) throws Exception {
		return search(key, value, "20");
	}

	private ResultActions search(String key, String value, String size) throws Exception {
		return mockMvc.perform(get(BASE_URL).header("X-Member-Id", ADMIN_ID).param(key, value).param("size", size));
	}

	private long createOrder(long buyerId, long courseId, boolean pay) throws Exception {
		long participationId = apply(buyerId, courseId);
		OrderCreateResult result = transactionTemplate.execute(status -> orderCreateUseCase.create(buyerId,
			participationId, UUID.randomUUID().toString(), LocalDateTime.now()));
		long orderId = result.order().getId();
		if (pay) {
			transactionTemplate.execute(status -> orderPayUseCase.pay(buyerId, orderId, LocalDateTime.now()));
		}
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
		return ((Number)JsonPath.read(body, "$.data.id")).longValue();
	}
}
