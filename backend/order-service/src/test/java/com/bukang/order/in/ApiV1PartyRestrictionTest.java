package com.bukang.order.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

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

import com.bukang.order.out.OrderOutboxKafkaEventRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.PartyMemberRepository;
import com.bukang.order.out.PartyRepository;
import com.bukang.order.support.StubCrewPort;
import com.bukang.order.support.StubCrewPortConfig;
import com.bukang.order.support.StubRaceCoursePort;
import com.bukang.order.support.StubRaceCoursePortConfig;
import com.jayway.jsonpath.JsonPath;

// 결제율 미달 크루의 파티 만들기 제한 (BUK-190). 파티 만들기만 막고 합류는 막지 않는다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubCrewPortConfig.class})
class ApiV1PartyRestrictionTest {
	private static final String BASE_URL = "/api/v1/order/parties";
	private static final long LEADER = 1;
	private static final long MEMBER = 2;
	private static final long CREW_ID = 7;
	private static final long COURSE_10K = 10;
	private static final long RACE_ID = 100;
	private static final LocalDateTime END_AT = LocalDateTime.of(2026, 12, 12, 4, 30);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PartyRepository partyRepository;

	@Autowired
	private PartyMemberRepository partyMemberRepository;

	@Autowired
	private ParticipationRepository participationRepository;

	@Autowired
	private OrderOutboxKafkaEventRepository orderOutboxKafkaEventRepository;

	@Autowired
	private StubRaceCoursePort raceCoursePort;

	@Autowired
	private StubCrewPort crewPort;

	@BeforeEach
	void setUp() {
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_10K, RACE_ID, 14, null));
	}

	@AfterEach
	void tearDown() {
		participationRepository.deleteAll();
		partyMemberRepository.deleteAll();
		partyRepository.deleteAll();
		orderOutboxKafkaEventRepository.deleteAll();
		raceCoursePort.clear();
		crewPort.clear();
	}

	@Test
	@DisplayName("제한 중인 크루는 파티를 만들 수 없고 끝나는 시각을 받는다. 파티·신청서는 남지 않는다")
	void createRestricted() throws Exception {
		crewPort.restrictParty(CREW_ID, END_AT);

		create(LEADER)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORDER_CREW_PARTY_RESTRICTED"))
			.andExpect(jsonPath("$.data.endAt").value("2026-12-12T04:30:00"));

		assertThat(partyRepository.count()).isZero();
		assertThat(participationRepository.count()).isZero();
	}

	@Test
	@DisplayName("제한 전에 만든 파티에는 제한 중에도 합류할 수 있다")
	void joinNotRestricted() throws Exception {
		String token = JsonPath.read(create(LEADER).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString(), "$.data.party.joinToken");
		crewPort.restrictParty(CREW_ID, END_AT);

		mockMvc.perform(post(BASE_URL + "/join")
				.header("X-Member-Id", MEMBER)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "joinToken": "%s", "participant": %s, "thirdPartyConsent": true }
					""".formatted(token, participant())))
			.andExpect(status().isCreated());
	}

	private ResultActions create(long memberId) throws Exception {
		return mockMvc.perform(post(BASE_URL)
			.header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{ "crewId": %d, "raceCourseId": %d, "participant": %s, "thirdPartyConsent": true }
				""".formatted(CREW_ID, COURSE_10K, participant())));
	}

	private static String participant() {
		return """
			{ "name": "김러너", "birth": "1994-06-25", "gender": "MALE", "phone": "010-1234-5678",
				"emergencyContactName": "김가족", "emergencyContactPhone": "010-9876-5432", "tshirtSize": "L",
				"shippingAddress": { "recipientName": "김러너", "recipientPhone": "010-1234-5678", "zipcode": "04700",
					"address": "서울 성동구 왕십리로 00", "addressDetail": "101동 1001호" } }
			""";
	}
}
