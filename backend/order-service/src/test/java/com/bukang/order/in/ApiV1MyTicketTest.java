package com.bukang.order.in;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.order.domain.Gender;
import com.bukang.order.domain.ParticipantInfo;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.Party;
import com.bukang.order.domain.ShippingAddress;
import com.bukang.order.out.KitOptionSaleRepository;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationKitOptionRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.PartyRepository;
import com.bukang.order.out.QueueAdmissionRepository;
import com.bukang.order.out.ShipmentRepository;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.support.QueueEntry;
import com.bukang.order.support.StubKitOptionPort;
import com.bukang.order.support.StubKitOptionPortConfig;
import com.bukang.order.support.StubMemberPorts;
import com.bukang.order.support.StubMemberPortsConfig;
import com.bukang.order.support.StubRaceCoursePort;
import com.bukang.order.support.StubRaceCoursePortConfig;
import com.bukang.order.support.StubSeatHoldPort;
import com.bukang.order.support.StubSeatHoldPortConfig;
import com.bukang.order.support.StubSellerLookupPortConfig;
import com.bukang.order.support.StubWalletPort;
import com.bukang.order.support.StubWalletPortConfig;
import com.jayway.jsonpath.JsonPath;

// 내 참가권 목록·참가권 상세의 키트 옵션·대회 상세의 내 신청 (BUK-181)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubSeatHoldPortConfig.class, StubWalletPortConfig.class,
	StubKitOptionPortConfig.class, StubSellerLookupPortConfig.class, StubMemberPortsConfig.class})
class ApiV1MyTicketTest {
	private static final String TICKETS_URL = "/api/v1/order/participations/me";
	private static final long MEMBER = 1;
	private static final long OTHER_MEMBER = 2;
	private static final long LEADER = 3;
	private static final long COURSE_ID = 10;
	private static final long RACE_ID = 100;
	private static final long PARTY_COURSE_ID = 20;
	private static final long PARTY_RACE_ID = 200;
	private static final long CREW_ID = 7;
	private static final long SHIRT_L = 12;
	private static final ParticipantInfo PARTICIPANT = new ParticipantInfo("김러너", LocalDate.of(1994, 6, 25),
		Gender.MALE, "010-1234-5678", "김가족", "010-9876-5432", "L",
		new ShippingAddress("김러너", "010-1234-5678", "04700", "서울 성동구 왕십리로 00", null));

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
	private ShipmentRepository shipmentRepository;

	@Autowired
	private KitOptionSaleRepository kitOptionSaleRepository;

	@Autowired
	private ParticipationKitOptionRepository participationKitOptionRepository;

	@Autowired
	private StubRaceCoursePort raceCoursePort;

	@Autowired
	private StubSeatHoldPort seatHoldPort;

	@Autowired
	private StubWalletPort walletPort;

	@Autowired
	private StubKitOptionPort kitOptionPort;

	@Autowired
	private StubMemberPorts memberPorts;

	@BeforeEach
	void setUp() {
		raceCoursePort.put(withRaceName(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null), "서울 마라톤"));
		raceCoursePort.put(withRaceName(StubRaceCoursePort.openCourse(PARTY_COURSE_ID, PARTY_RACE_ID, 14, null),
			"부산 하프"));
		kitOptionPort.add(RACE_ID, SHIRT_L, "T-shirt", "L", 30);
		memberPorts.member("러너A", LEADER);
	}

	@AfterEach
	void tearDown() {
		shipmentRepository.deleteAll();
		orderRepository.deleteAll();
		participationRepository.deleteAll();
		partyRepository.deleteAll();
		queueAdmissionRepository.deleteAll();
		participationKitOptionRepository.deleteAll();
		kitOptionSaleRepository.deleteAll();
		raceCoursePort.clear();
		seatHoldPort.clear();
		walletPort.clear();
		kitOptionPort.clear();
		memberPorts.clear();
	}

	@Test
	@DisplayName("결제한 참가권은 대회·종목·환불 기한·주문·키트 옵션·배송 상태를 한 줄에 담고, 남의 신청은 나오지 않는다")
	void paidTicket() throws Exception {
		long orderId = orderAndPay(MEMBER);
		orderAndPay(OTHER_MEMBER);

		tickets(MEMBER, false)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(1))
			.andExpect(jsonPath("$.data.tickets[0].raceName").value("서울 마라톤"))
			.andExpect(jsonPath("$.data.tickets[0].courseName").value("테스트 대회 종목 " + COURSE_ID))
			.andExpect(jsonPath("$.data.tickets[0].refundDeadline").isNotEmpty())
			.andExpect(jsonPath("$.data.tickets[0].participantName").value("김러너"))
			.andExpect(jsonPath("$.data.tickets[0].status").value("CONFIRMED"))
			.andExpect(jsonPath("$.data.tickets[0].orderId").value(orderId))
			.andExpect(jsonPath("$.data.tickets[0].orderItemStatus").value("PAID"))
			.andExpect(jsonPath("$.data.tickets[0].amount").value(StubRaceCoursePort.PRICE))
			.andExpect(jsonPath("$.data.tickets[0].kitOptions[0].name").value("T-shirt"))
			.andExpect(jsonPath("$.data.tickets[0].kitOptions[0].value").value("L"))
			.andExpect(jsonPath("$.data.tickets[0].kitDeliveryStatus").value("PREPARING"))
			.andExpect(jsonPath("$.data.tickets[0].partyId").doesNotExist());
	}

	@Test
	@DisplayName("참가권 상세에 결제 때 고른 키트 옵션이 나온다")
	void detailHasKitOptions() throws Exception {
		orderAndPay(MEMBER);
		long participationId = myParticipationId();

		mockMvc.perform(get("/api/v1/order/participations/" + participationId).header("X-Member-Id", MEMBER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.kitOptions.length()").value(1))
			.andExpect(jsonPath("$.data.kitOptions[0].kitOptionId").value(SHIRT_L))
			.andExpect(jsonPath("$.data.kitOptions[0].value").value("L"));
	}

	@Test
	@DisplayName("상품 서비스에 묻지 못해도 목록은 나오고, 대회 이름은 비우고 종목 이름은 주문 당시 값으로 채운다")
	void productUnavailable() throws Exception {
		orderAndPay(MEMBER);
		raceCoursePort.setUnavailable(true);

		tickets(MEMBER, false)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.tickets[0].raceName").doesNotExist())
			.andExpect(jsonPath("$.data.tickets[0].refundDeadline").doesNotExist())
			.andExpect(jsonPath("$.data.tickets[0].courseName").value("테스트 대회 종목 " + COURSE_ID))
			.andExpect(jsonPath("$.data.tickets[0].raceDate").isNotEmpty());
	}

	@Test
	@DisplayName("파티원 신청은 크루 ID·파티 상태·파티장 닉네임이 나오고, 주문 전이라 주문 칸은 비어 있다")
	void partyTicket() throws Exception {
		Party party = partyRepository.save(Party.create(CREW_ID, LEADER, PARTY_RACE_ID, PARTY_COURSE_ID,
			LocalDateTime.now()));
		participationRepository.save(Participation.partyMember(MEMBER, PARTY_RACE_ID, PARTY_COURSE_ID,
			party.getId(), PARTICIPANT, LocalDateTime.now()));

		tickets(MEMBER, false)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.tickets[0].participationType").value("PARTY"))
			.andExpect(jsonPath("$.data.tickets[0].raceName").value("부산 하프"))
			.andExpect(jsonPath("$.data.tickets[0].partyId").value(party.getId()))
			.andExpect(jsonPath("$.data.tickets[0].crewId").value(CREW_ID))
			.andExpect(jsonPath("$.data.tickets[0].partyStatus").value("RECRUITING"))
			.andExpect(jsonPath("$.data.tickets[0].leaderNickname").value("러너A"))
			.andExpect(jsonPath("$.data.tickets[0].orderId").doesNotExist())
			.andExpect(jsonPath("$.data.tickets[0].kitOptions.length()").value(0));
	}

	@Test
	@DisplayName("active=true면 취소한 신청은 빠지고 살아 있는 신청만 나온다")
	void activeOnly() throws Exception {
		Participation canceled = participationRepository.save(Participation.individual(MEMBER, PARTY_RACE_ID,
			PARTY_COURSE_ID, PARTICIPANT, LocalDateTime.now()));
		mockMvc.perform(post("/api/v1/order/participations/" + canceled.getId() + "/cancel")
				.header("X-Member-Id", MEMBER))
			.andExpect(status().isOk());
		orderAndPay(MEMBER);

		tickets(MEMBER, false).andExpect(jsonPath("$.data.totalElements").value(2));
		tickets(MEMBER, true)
			.andExpect(jsonPath("$.data.totalElements").value(1))
			.andExpect(jsonPath("$.data.tickets[0].status").value("CONFIRMED"));
	}

	@Test
	@DisplayName("페이지 크기가 50을 넘으면 400이다")
	void pageSizeLimit() throws Exception {
		mockMvc.perform(get(TICKETS_URL).header("X-Member-Id", MEMBER).param("size", "51"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("한 번에 50개까지 조회할 수 있습니다."));
	}

	@Test
	@DisplayName("대회 상세 상태: 파티가 없어도 개인 신청이 있으면 myParticipation이 나오고, 신청이 없으면 칸이 빠진다")
	void raceStatusHasMyParticipation() throws Exception {
		orderAndPay(MEMBER);

		mockMvc.perform(get("/api/v1/order/parties/me/race-status").header("X-Member-Id", MEMBER)
				.param("raceId", String.valueOf(RACE_ID)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.role").value("NONE"))
			.andExpect(jsonPath("$.data.myParticipation.participationType").value("INDIVIDUAL"))
			.andExpect(jsonPath("$.data.myParticipation.status").value("CONFIRMED"));

		mockMvc.perform(get("/api/v1/order/parties/me/race-status").header("X-Member-Id", MEMBER)
				.param("raceId", String.valueOf(PARTY_RACE_ID)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.myParticipation").doesNotExist());
	}

	private ResultActions tickets(long memberId, boolean active) throws Exception {
		return mockMvc.perform(get(TICKETS_URL).header("X-Member-Id", memberId)
			.param("active", String.valueOf(active)));
	}

	private long myParticipationId() {
		return participationRepository.findAll().stream()
			.filter(participation -> participation.getMemberId() == MEMBER)
			.filter(participation -> participation.getRaceId() == RACE_ID)
			.findFirst().orElseThrow().getId();
	}

	// 신청서 작성 → [결제하기] → 티셔츠 L로 예치금 결제
	private long orderAndPay(long memberId) throws Exception {
		QueueEntry.enter(mockMvc, memberId, COURSE_ID);
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
				""".formatted(COURSE_ID))));
		long orderId = read(mockMvc.perform(post("/api/v1/order/orders")
			.header("X-Member-Id", memberId)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"participationId\": %d}".formatted(participationId))));
		mockMvc.perform(post("/api/v1/order/orders/" + orderId + "/payment").header("X-Member-Id", memberId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"kitOptionIds\": [" + SHIRT_L + "]}"))
			.andExpect(status().isOk());
		assertThat(orderId).isPositive();
		return orderId;
	}

	private long read(ResultActions result) throws Exception {
		String body = result.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(body, "$.data.id").longValue();
	}

	private static RaceCourseInfo withRaceName(RaceCourseInfo course, String raceName) {
		return new RaceCourseInfo(course.raceCourseId(), course.raceId(), course.raceDate(),
			course.applicationOpenAt(), course.applicationCloseAt(), course.minAge(), course.maxAge(),
			course.sellerId(), course.courseName(), course.price(), course.kitIncluded(), course.refundDeadline(),
			course.scheduleChangedAt(), course.canceled(), course.tshirtSizeOption(), course.crewSale(), raceName,
			course.course());
	}
}
