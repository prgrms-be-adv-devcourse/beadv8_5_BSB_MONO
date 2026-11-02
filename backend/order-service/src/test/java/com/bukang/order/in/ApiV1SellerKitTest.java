package com.bukang.order.in;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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

import com.bukang.order.app.OrderCancelUseCase;
import com.bukang.order.app.OrderCreateResult;
import com.bukang.order.app.OrderCreateUseCase;
import com.bukang.order.app.OrderPayUseCase;
import com.bukang.order.domain.Order;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationRepository;
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
 * 판매자 키트 배송 API (PRO-197). 로그인한 회원이 승인된 판매자일 때만 쓰고, 판매자 ID로 자기 대회 참가권만 다룬다
 * 키트 배송 규칙(환불 기한, 상태 순서) 자체는 KitDeliveryTest가 본다
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubSeatHoldPortConfig.class, StubWalletPortConfig.class,
	StubRefundEligibilityPortConfig.class, StubSellerLookupPortConfig.class})
class ApiV1SellerKitTest {
	private static final String BASE_URL = "/api/v1/order/seller/kits";
	private static final long COURSE_ID = 60;
	private static final long RACE_ID = 600;
	private static final long OTHER_COURSE_ID = 61;
	private static final long OTHER_RACE_ID = 610;
	private static final long BUYER_ID = 1;
	// 판매자로 승인된 회원. 판매자 ID는 종목의 판매자(StubRaceCoursePort.SELLER_ID)다
	private static final long SELLER_MEMBER_ID = 70;
	private static final long OTHER_SELLER_MEMBER_ID = 71;
	private static final long NOT_SELLER_MEMBER_ID = 72;

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
	private OrderCancelUseCase orderCancelUseCase;

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

	@AfterEach
	void tearDown() {
		orderRepository.deleteAll();
		participationRepository.deleteAll();
		queueAdmissionRepository.deleteAll();
		raceCoursePort.clear();
		seatHoldPort.clear();
		walletPort.clear();
		sellerLookupPort.clear();
	}

	@Test
	@DisplayName("승인된 판매자는 키트 목록·건수를 보고, 발송·배송 완료하면 구매자 주문 조회에 그대로 보인다")
	void sellerShipsAndDelivers() throws Exception {
		approveSellers();
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null, LocalDate.now().minusDays(1)));
		long orderId = paidOrder();
		long itemId = itemIdOf(orderId);

		mockMvc.perform(get(BASE_URL).header("X-Member-Id", SELLER_MEMBER_ID).param("raceId", String.valueOf(RACE_ID)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("키트 주문 목록을 조회했습니다."))
			.andExpect(jsonPath("$.data.totalElements").value(1))
			.andExpect(jsonPath("$.data.kits[0].orderItemId").value(itemId))
			.andExpect(jsonPath("$.data.kits[0].participantName").value("김러너"))
			.andExpect(jsonPath("$.data.kits[0].kitDeliveryStatus").value("PREPARING"));
		mockMvc.perform(get(BASE_URL + "/summary").header("X-Member-Id", SELLER_MEMBER_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.preparing").value(1))
			.andExpect(jsonPath("$.data.beforeRefundDeadline").value(0));

		ship(SELLER_MEMBER_ID, itemId, "CJ대한통운", "684912345678")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("키트를 발송 처리했습니다."))
			.andExpect(jsonPath("$.data.kitDeliveryStatus").value("SHIPPING"))
			.andExpect(jsonPath("$.data.kitTrackingNumber").value("684912345678"));
		mockMvc.perform(post(BASE_URL + "/" + itemId + "/delivery").header("X-Member-Id", SELLER_MEMBER_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("키트를 배송 완료로 바꿨습니다."))
			.andExpect(jsonPath("$.data.kitDeliveryStatus").value("DELIVERED"));

		mockMvc.perform(get("/api/v1/order/orders/" + orderId).header("X-Member-Id", BUYER_ID))
			.andExpect(jsonPath("$.data.items[0].kitDeliveryStatus").value("DELIVERED"))
			.andExpect(jsonPath("$.data.items[0].kitCarrier").value("CJ대한통운"));
		mockMvc.perform(get(BASE_URL).header("X-Member-Id", SELLER_MEMBER_ID).param("status", "DELIVERED"))
			.andExpect(jsonPath("$.data.totalElements").value(1));
	}

	@Test
	@DisplayName("취소 중(환불 결과를 기다림)인 참가권의 키트는 판매자 목록·건수에서 빠지고, 결제 완료로 되돌아오면 다시 보인다")
	void cancelingKitHidden() throws Exception {
		approveSellers();
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null, LocalDate.now().plusDays(5)));
		long orderId = paidOrder();
		long itemId = itemIdOf(orderId);
		transactionTemplate.executeWithoutResult(status -> {
			Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow();
			order.startCancelItem(order.findItem(itemId), LocalDateTime.now());
		});

		mockMvc.perform(get(BASE_URL).header("X-Member-Id", SELLER_MEMBER_ID))
			.andExpect(jsonPath("$.data.totalElements").value(0));
		mockMvc.perform(get(BASE_URL + "/summary").header("X-Member-Id", SELLER_MEMBER_ID))
			.andExpect(jsonPath("$.data.preparing").value(0))
			.andExpect(jsonPath("$.data.beforeRefundDeadline").value(0));

		orderCancelUseCase.revertCancel(orderId, itemId);

		mockMvc.perform(get(BASE_URL).header("X-Member-Id", SELLER_MEMBER_ID))
			.andExpect(jsonPath("$.data.totalElements").value(1));
		mockMvc.perform(get(BASE_URL + "/summary").header("X-Member-Id", SELLER_MEMBER_ID))
			.andExpect(jsonPath("$.data.preparing").value(1))
			.andExpect(jsonPath("$.data.beforeRefundDeadline").value(1));
	}

	@Test
	@DisplayName("판매자가 아닌 회원은 네 API 모두 403 ORDER_SELLER_FORBIDDEN이고, 다른 판매자의 참가권은 403 ORDER_FORBIDDEN이다")
	void notSellerOrOtherSeller() throws Exception {
		approveSellers();
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null, LocalDate.now().minusDays(1)));
		long itemId = itemIdOf(paidOrder());

		for (ResultActions result : new ResultActions[] {
			mockMvc.perform(get(BASE_URL).header("X-Member-Id", NOT_SELLER_MEMBER_ID)),
			mockMvc.perform(get(BASE_URL + "/summary").header("X-Member-Id", NOT_SELLER_MEMBER_ID)),
			ship(NOT_SELLER_MEMBER_ID, itemId, "CJ대한통운", "684912345678"),
			mockMvc.perform(post(BASE_URL + "/" + itemId + "/delivery").header("X-Member-Id", NOT_SELLER_MEMBER_ID))
		}) {
			result.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ORDER_SELLER_FORBIDDEN"))
				.andExpect(jsonPath("$.message").value("승인된 판매자만 쓸 수 있습니다."));
		}
		ship(OTHER_SELLER_MEMBER_ID, itemId, "CJ대한통운", "684912345678")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORDER_FORBIDDEN"));
		ship(SELLER_MEMBER_ID, itemId, "CJ대한통운", "684912345678").andExpect(status().isOk());
		mockMvc.perform(post(BASE_URL + "/" + itemId + "/delivery").header("X-Member-Id", OTHER_SELLER_MEMBER_ID))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORDER_FORBIDDEN"));
		mockMvc.perform(get(BASE_URL).header("X-Member-Id", OTHER_SELLER_MEMBER_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(0));
	}

	@Test
	@DisplayName("택배사·송장 번호가 비었거나 너무 길면 400, 환불 기한 전 발송·발송 전 배송 완료는 409, 없는 상품은 404다")
	void invalidRequests() throws Exception {
		approveSellers();
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null, LocalDate.now().plusDays(5)));
		long itemId = itemIdOf(paidOrder());

		ship(SELLER_MEMBER_ID, itemId, "CJ대한통운", " ")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value("송장 번호를 입력해주세요."));
		ship(SELLER_MEMBER_ID, itemId, "CJ대한통운", "1".repeat(51))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("송장 번호는 50자 이하로 입력해주세요."));
		ship(SELLER_MEMBER_ID, itemId, "CJ대한통운", "684912345678")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_KIT_NOT_SHIPPABLE"));
		mockMvc.perform(post(BASE_URL + "/" + itemId + "/delivery").header("X-Member-Id", SELLER_MEMBER_ID))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("아직 발송하지 않은 키트입니다."));
		ship(SELLER_MEMBER_ID, 999_999, "CJ대한통운", "684912345678")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ORDER_ITEM_NOT_FOUND"));
		mockMvc.perform(get(BASE_URL + "/summary").header("X-Member-Id", SELLER_MEMBER_ID))
			.andExpect(jsonPath("$.data.beforeRefundDeadline").value(1));
	}

	@Test
	@DisplayName("목록은 내 판매자 ID의 참가권만 페이지로 나눠 세고, 다른 판매자 대회의 참가권은 건수에도 넣지 않는다")
	void pagesOnlyMyKits() throws Exception {
		approveSellers();
		LocalDate refundDeadline = LocalDate.now().minusDays(1);
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null, refundDeadline));
		raceCoursePort.put(otherSellerCourse(refundDeadline));
		long firstOrderId = paidOrder(BUYER_ID, COURSE_ID);
		long secondOrderId = paidOrder(BUYER_ID + 1, COURSE_ID);
		paidOrder(BUYER_ID + 2, OTHER_COURSE_ID);

		// 한 페이지에 다 들어가지 않아야 건수 쿼리가 따로 돈다
		mockMvc.perform(get(BASE_URL).header("X-Member-Id", SELLER_MEMBER_ID).param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.kits.length()").value(1))
			.andExpect(jsonPath("$.data.kits[0].orderItemId").value(itemIdOf(secondOrderId)))
			.andExpect(jsonPath("$.data.totalElements").value(2))
			.andExpect(jsonPath("$.data.totalPages").value(2));
		mockMvc.perform(get(BASE_URL).header("X-Member-Id", SELLER_MEMBER_ID).param("size", "1").param("page", "1"))
			.andExpect(jsonPath("$.data.kits[0].orderItemId").value(itemIdOf(firstOrderId)));
		mockMvc.perform(get(BASE_URL).header("X-Member-Id", OTHER_SELLER_MEMBER_ID))
			.andExpect(jsonPath("$.data.totalElements").value(1))
			.andExpect(jsonPath("$.data.kits[0].raceId").value(OTHER_RACE_ID));
	}

	@Test
	@DisplayName("페이지 크기가 100을 넘거나 없는 배송 상태거나 회원 ID 헤더가 없으면 판매자 확인 전에 400·401이다")
	void invalidPageOrHeader() throws Exception {
		mockMvc.perform(get(BASE_URL).header("X-Member-Id", SELLER_MEMBER_ID).param("size", "101"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("한 번에 100개까지 조회할 수 있습니다."));
		mockMvc.perform(get(BASE_URL).header("X-Member-Id", SELLER_MEMBER_ID).param("status", "LOST"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."));
		mockMvc.perform(get(BASE_URL))
			.andExpect(status().isUnauthorized());
	}

	// 다른 판매자(StubRaceCoursePort.SELLER_ID + 1)가 여는 종목
	private RaceCourseInfo otherSellerCourse(LocalDate refundDeadline) {
		RaceCourseInfo course = StubRaceCoursePort.openCourse(OTHER_COURSE_ID, OTHER_RACE_ID, 14, null, refundDeadline);
		return new RaceCourseInfo(course.raceCourseId(), course.raceId(), course.raceDate(), course.applicationOpenAt(),
			course.applicationCloseAt(), course.minAge(), course.maxAge(), StubRaceCoursePort.SELLER_ID + 1,
			course.courseName(), course.price(), course.kitIncluded(), course.refundDeadline(),
			course.scheduleChangedAt(), course.canceled(), course.tshirtSizeOption());
	}

	private void approveSellers() {
		sellerLookupPort.approve(SELLER_MEMBER_ID, StubRaceCoursePort.SELLER_ID);
		sellerLookupPort.approve(OTHER_SELLER_MEMBER_ID, StubRaceCoursePort.SELLER_ID + 1);
	}

	private ResultActions ship(long memberId, long orderItemId, String carrier, String trackingNumber)
		throws Exception {
		return mockMvc.perform(post(BASE_URL + "/" + orderItemId + "/shipment")
			.header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"carrier\": \"%s\", \"trackingNumber\": \"%s\"}".formatted(carrier, trackingNumber)));
	}

	private long paidOrder() throws Exception {
		return paidOrder(BUYER_ID, COURSE_ID);
	}

	private long paidOrder(long buyerId, long courseId) throws Exception {
		long participationId = apply(buyerId, courseId);
		OrderCreateResult result = transactionTemplate.execute(status -> orderCreateUseCase.create(buyerId,
			participationId, UUID.randomUUID().toString(), LocalDateTime.now()));
		long orderId = result.order().getId();
		transactionTemplate.execute(status -> orderPayUseCase.pay(buyerId, orderId, LocalDateTime.now()));
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
}
