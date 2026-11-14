package com.bukang.order.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
import com.bukang.order.app.OrderPayUseCase;
import com.bukang.order.domain.SellerExportLog;
import com.bukang.order.domain.SellerExportType;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.QueueAdmissionRepository;
import com.bukang.order.out.SellerExportLogRepository;
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
 * 판매자 참가자 명단·키트 배송 목록 내려받기 (BUK-228). 자기 대회 것만 담고, 내려받을 때마다 사유와 함께 기록한다
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubSeatHoldPortConfig.class, StubWalletPortConfig.class,
	StubRefundEligibilityPortConfig.class, StubSellerLookupPortConfig.class})
class ApiV1SellerExportTest {
	private static final String ROSTER_URL = "/api/v1/order/seller/orders/roster-export";
	private static final String KIT_URL = "/api/v1/order/seller/kits/export";
	private static final long COURSE_ID = 85;
	private static final long RACE_ID = 850;
	private static final long OTHER_COURSE_ID = 86;
	private static final long OTHER_RACE_ID = 860;
	private static final long BUYER_ID = 1;
	private static final long SELLER_MEMBER_ID = 95;
	private static final long OTHER_SELLER_MEMBER_ID = 96;
	private static final long NOT_SELLER_MEMBER_ID = 97;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private ParticipationRepository participationRepository;

	@Autowired
	private QueueAdmissionRepository queueAdmissionRepository;

	@Autowired
	private SellerExportLogRepository sellerExportLogRepository;

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
	private StubSellerLookupPort sellerLookupPort;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@AfterEach
	void tearDown() {
		orderRepository.deleteAll();
		participationRepository.deleteAll();
		queueAdmissionRepository.deleteAll();
		sellerExportLogRepository.deleteAll();
		raceCoursePort.clear();
		seatHoldPort.clear();
		walletPort.clear();
		sellerLookupPort.clear();
	}

	@Test
	@DisplayName("명단은 결제한 참가자의 이름·연락처·종목·키트 옵션을 BOM 붙은 CSV로 주고, 누가 왜 몇 줄 받았는지 남긴다")
	void roster() throws Exception {
		setUp();
		paidOrder(BUYER_ID, COURSE_ID, "김러너");
		paidOrder(BUYER_ID + 1, COURSE_ID, "=CMD()");
		paidOrder(BUYER_ID + 2, OTHER_COURSE_ID, "남의참가자");
		createdOrder(BUYER_ID + 3, COURSE_ID, "결제안함");
		// 파티로 결제한 참가자처럼 신청서에 파티 번호를 붙인다
		jdbcTemplate.update("update order_participation set party_id = 77 where member_id = ?", BUYER_ID);

		String csv = body(roster(SELLER_MEMBER_ID, RACE_ID, "  현장 접수 확인  ")
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(header().string("Content-Disposition",
				"attachment; filename=\"roster-%d-%s.csv\"".formatted(RACE_ID, LocalDate.now()))));

		assertThat(csv).startsWith("﻿주문번호,종목,참가자 이름,연락처,키트 옵션,파티 번호\r\n");
		List<String> lines = csv.lines().toList();
		assertThat(lines).hasSize(3);
		assertThat(lines.get(1)).endsWith("김러너,010-1234-5678,티셔츠: L,77");
		// 참가자가 적은 값이 엑셀 수식으로 실행되지 않는다
		assertThat(lines.get(2)).contains(",'=CMD(),");
		assertThat(csv).doesNotContain("남의참가자").doesNotContain("결제안함");

		List<SellerExportLog> logs =
			sellerExportLogRepository.findBySellerIdOrderByIdDesc(StubRaceCoursePort.SELLER_ID);
		assertThat(logs).hasSize(1);
		assertThat(logs.getFirst().getMemberId()).isEqualTo(SELLER_MEMBER_ID);
		assertThat(logs.getFirst().getRaceId()).isEqualTo(RACE_ID);
		assertThat(logs.getFirst().getExportType()).isEqualTo(SellerExportType.ROSTER);
		assertThat(logs.getFirst().getReason()).isEqualTo("현장 접수 확인");
		assertThat(logs.getFirst().getRowCount()).isEqualTo(2);
		assertThat(logs.getFirst().getCreateDate()).isNotNull();

		// 종목으로 거르면 그 종목만 담는다
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID + 2, RACE_ID, 14, null));
		paidOrder(BUYER_ID + 4, COURSE_ID + 2, "다른종목");
		String byCourse = body(mockMvc.perform(post(ROSTER_URL).header("X-Member-Id", SELLER_MEMBER_ID)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"raceId\": %d, \"raceCourseId\": %d, \"reason\": \"확인\"}".formatted(RACE_ID, COURSE_ID + 2))));
		assertThat(byCourse.lines().toList()).hasSize(2);
		assertThat(byCourse).contains("다른종목").doesNotContain("김러너");
	}

	@Test
	@DisplayName("키트 배송 목록은 배송지·옵션·배송 상태를 담고, 다른 판매자는 자기 것만 받는다")
	void kitShipments() throws Exception {
		setUp();
		paidOrder(BUYER_ID, COURSE_ID, "김러너");
		paidOrder(BUYER_ID + 2, OTHER_COURSE_ID, "남의참가자");

		String csv = body(kits(SELLER_MEMBER_ID, "{\"raceId\": %d, \"status\": \"PREPARING\", \"reason\": \"택배 접수\"}"
			.formatted(RACE_ID)).andExpect(status().isOk()));
		List<String> lines = csv.lines().toList();
		assertThat(lines).hasSize(2);
		assertThat(lines.get(1)).contains("결제 완료,김러너,김러너,010-1234-5678,04700,서울 성동구 왕십리로 00,101동 1001호,"
			+ "티셔츠: L,배송 준비,,");
		assertThat(csv).doesNotContain("남의참가자");

		String other = body(kits(OTHER_SELLER_MEMBER_ID, "{\"raceId\": %d, \"reason\": \"확인\"}".formatted(RACE_ID))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition",
				"attachment; filename=\"kit-shipments-%d-%s.csv\"".formatted(RACE_ID, LocalDate.now()))));
		assertThat(other.lines().toList()).hasSize(1);
		assertThat(sellerExportLogRepository.findBySellerIdOrderByIdDesc(StubRaceCoursePort.SELLER_ID + 1))
			.extracting(SellerExportLog::getExportType, SellerExportLog::getRowCount)
			.containsExactly(tuple(SellerExportType.KIT_SHIPMENTS, 0));
	}

	@Test
	@DisplayName("사유·대회가 없거나 사유가 200자를 넘으면 400, 판매자가 아니면 403이고 기록은 남지 않는다")
	void invalidRequests() throws Exception {
		setUp();
		roster(SELLER_MEMBER_ID, RACE_ID, " ")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("내려받는 사유를 입력해주세요."));
		roster(SELLER_MEMBER_ID, RACE_ID, "가".repeat(201))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("사유는 200자 이하로 입력해주세요."));
		mockMvc.perform(post(ROSTER_URL).header("X-Member-Id", SELLER_MEMBER_ID)
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"확인\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("대회를 골라주세요."));
		kits(SELLER_MEMBER_ID, "{\"status\": \"LOST\", \"reason\": \"확인\"}")
			.andExpect(status().isBadRequest());
		roster(NOT_SELLER_MEMBER_ID, RACE_ID, "확인")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORDER_SELLER_FORBIDDEN"));
		kits(NOT_SELLER_MEMBER_ID, "{\"reason\": \"확인\"}")
			.andExpect(status().isForbidden());
		assertThat(sellerExportLogRepository.count()).isZero();
	}

	private void setUp() {
		sellerLookupPort.approve(SELLER_MEMBER_ID, StubRaceCoursePort.SELLER_ID);
		sellerLookupPort.approve(OTHER_SELLER_MEMBER_ID, StubRaceCoursePort.SELLER_ID + 1);
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null));
		RaceCourseInfo course = StubRaceCoursePort.openCourse(OTHER_COURSE_ID, OTHER_RACE_ID, 14, null);
		raceCoursePort.put(new RaceCourseInfo(course.raceCourseId(), course.raceId(), course.raceDate(),
			course.applicationOpenAt(), course.applicationCloseAt(), course.minAge(), course.maxAge(),
			StubRaceCoursePort.SELLER_ID + 1, course.courseName(), course.price(), course.kitIncluded(),
			course.refundDeadline(), course.scheduleChangedAt(), course.canceled(), course.tshirtSizeOption()));
	}

	private ResultActions roster(long memberId, long raceId, String reason) throws Exception {
		return mockMvc.perform(post(ROSTER_URL).header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"raceId\": %d, \"reason\": \"%s\"}".formatted(raceId, reason)));
	}

	private ResultActions kits(long memberId, String body) throws Exception {
		return mockMvc.perform(post(KIT_URL).header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static String body(ResultActions result) throws Exception {
		return new String(result.andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
	}

	private long createdOrder(long buyerId, long courseId, String name) throws Exception {
		long participationId = apply(buyerId, courseId, name);
		OrderCreateResult result = transactionTemplate.execute(status -> orderCreateUseCase.create(buyerId,
			participationId, UUID.randomUUID().toString(), LocalDateTime.now()));
		return result.order().getId();
	}

	private void paidOrder(long buyerId, long courseId, String name) throws Exception {
		long orderId = createdOrder(buyerId, courseId, name);
		transactionTemplate.execute(status -> orderPayUseCase.pay(buyerId, orderId, List.of(), LocalDateTime.now()));
	}

	private long apply(long buyerId, long courseId, String name) throws Exception {
		QueueEntry.enter(mockMvc, buyerId, courseId);
		String body = mockMvc.perform(post("/api/v1/order/participations")
				.header("X-Member-Id", buyerId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "raceCourseId": %d, "thirdPartyConsent": true,
						"participant": { "name": "%s", "birth": "1994-06-25", "gender": "MALE",
							"phone": "010-1234-5678", "emergencyContactName": "김가족",
							"emergencyContactPhone": "010-9876-5432", "tshirtSize": "L",
						"shippingAddress": { "recipientName": "%s", "recipientPhone": "010-1234-5678",
							"zipcode": "04700", "address": "서울 성동구 왕십리로 00", "addressDetail": "101동 1001호" } } }
					""".formatted(courseId, name, name)))
			.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(body, "$.data.id").longValue();
	}
}
