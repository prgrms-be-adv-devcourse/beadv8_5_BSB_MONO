package com.bukang.order.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

import com.bukang.order.domain.Order;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.QueueAdmissionRepository;
import com.bukang.order.support.StubRaceCoursePort;
import com.bukang.order.support.StubRaceCoursePortConfig;
import com.bukang.order.support.StubSeatHoldPort;
import com.bukang.order.support.StubSeatHoldPortConfig;
import com.jayway.jsonpath.JsonPath;

/**
 * 결제 페이지 타이머용 대기열 입장 상태 API와 개발용 더미 (PRO-191)
 * 대기열 스텁 없이 가짜 대기열(FakeQueueAdmissionAdapter)을 그대로 써, 타이머의 끝 시각과 결제 기한이 같은지 본다
 * 가짜 대기열은 앱 안에 입장을 기억하므로, 테스트마다 다른 회원 ID를 쓴다
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubSeatHoldPortConfig.class})
class ApiV1QueueAdmissionTest {
	private static final long RACE_ID = 300;
	private static final long COURSE_ID = 30;

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

	@AfterEach
	void tearDown() {
		orderRepository.deleteAll();
		participationRepository.deleteAll();
		queueAdmissionRepository.deleteAll();
		raceCoursePort.clear();
		seatHoldPort.clear();
	}

	@Test
	@DisplayName("입장 중이면 끝 시각과 남은 초를 주고, 다시 물어도 끝 시각이 같으며, 그 시각이 [결제하기]의 결제 기한이 된다")
	void admissionEndsAtIsPaymentDue() throws Exception {
		long memberId = 101;
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null));
		String first = admission(memberId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("대기열에 입장 중입니다."))
			.andExpect(jsonPath("$.data.admitted").value(true))
			.andReturn().getResponse().getContentAsString();
		String endsAt = JsonPath.read(first, "$.data.admissionEndsAt");
		int remaining = JsonPath.read(first, "$.data.remainingSeconds");
		assertThat(remaining).isBetween(890, 900);

		admission(memberId).andExpect(jsonPath("$.data.admissionEndsAt").value(endsAt));

		long participationId = apply(memberId);
		mockMvc.perform(post("/api/v1/order/orders")
				.header("X-Member-Id", memberId)
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"participationId\": %d}".formatted(participationId)))
			.andExpect(status().isCreated());
		Order order = orderRepository.findAll().getFirst();
		// DB는 마이크로초까지만 저장한다. 리눅스(CI)는 지금 시각을 나노초까지 줘 그 아래 자리만 다를 수 있다
		assertThat(order.getPaymentDueAt()).isCloseTo(LocalDateTime.parse(endsAt), within(1, ChronoUnit.MICROS));
	}

	@Test
	@DisplayName("더미로 다시 입장하면 끝 시각을 지금부터 다시 센다")
	void dummyReenter() throws Exception {
		long memberId = 102;
		String before = JsonPath.read(admission(memberId).andReturn().getResponse().getContentAsString(),
			"$.data.admissionEndsAt");

		Thread.sleep(20);
		mockMvc.perform(post("/dev/queue-dummy/enter").header("X-Member-Id", memberId)
				.param("raceCourseId", String.valueOf(COURSE_ID)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.admitted").value(true));

		String after = JsonPath.read(admission(memberId).andReturn().getResponse().getContentAsString(),
			"$.data.admissionEndsAt");
		assertThat(LocalDateTime.parse(after)).isAfter(LocalDateTime.parse(before));
		// 다시 입장하면 저장한 끝 시각도 새 값으로 바뀐다 ([결제하기]가 이 값을 결제 기한으로 쓴다)
		assertThat(queueAdmissionRepository.findByMemberIdAndRaceCourseId(memberId, COURSE_ID).orElseThrow()
			.getAdmissionEndsAt()).isCloseTo(LocalDateTime.parse(after), within(1, ChronoUnit.MICROS));
	}

	@Test
	@DisplayName("같은 회원이 처음 들어오며 동시에 여러 번 물어도 모두 200이고, 입장 끝 시각은 한 줄로 저장된다")
	void concurrentFirstEntry() throws Exception {
		int callers = 8;
		// 처음 넣는 줄이 겹치는 경우를 여러 번 만들려고 회원을 바꿔 가며 되풀이한다
		for (long memberId = 200; memberId < 210; memberId++) {
			long targetMemberId = memberId;
			CountDownLatch start = new CountDownLatch(1);
			List<Future<Integer>> statuses = new ArrayList<>();
			try (ExecutorService executor = Executors.newFixedThreadPool(callers)) {
				for (int index = 0; index < callers; index++) {
					statuses.add(executor.submit(() -> {
						start.await();
						return admission(targetMemberId).andReturn().getResponse().getStatus();
					}));
				}
				start.countDown();
				for (Future<Integer> callStatus : statuses) {
					assertThat(callStatus.get()).isEqualTo(200);
				}
			}
			assertThat(queueAdmissionRepository.findByMemberIdAndRaceCourseId(targetMemberId, COURSE_ID)).isPresent();
		}
		assertThat(queueAdmissionRepository.count()).isEqualTo(10);
	}

	@Test
	@DisplayName("더미 페이지는 HTML로 열린다")
	void dummyPage() throws Exception {
		mockMvc.perform(get("/dev/queue-dummy"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
			.andExpect(content().string(containsString("대기열 더미")));
	}

	@Test
	@DisplayName("회원 ID 헤더가 없으면 401, 대회·종목 ID가 0 이하면 400이다")
	void invalidRequest() throws Exception {
		mockMvc.perform(get("/api/v1/order/queue/admission")
				.param("raceId", String.valueOf(RACE_ID)).param("raceCourseId", String.valueOf(COURSE_ID)))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/order/queue/admission").header("X-Member-Id", 1)
				.param("raceId", "0").param("raceCourseId", String.valueOf(COURSE_ID)))
			.andExpect(status().isBadRequest());
	}

	private ResultActions admission(long memberId) throws Exception {
		return mockMvc.perform(get("/api/v1/order/queue/admission")
			.header("X-Member-Id", memberId)
			.param("raceId", String.valueOf(RACE_ID))
			.param("raceCourseId", String.valueOf(COURSE_ID)));
	}

	private long apply(long memberId) throws Exception {
		String body = mockMvc.perform(post("/api/v1/order/participations")
				.header("X-Member-Id", memberId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "raceCourseId": %d, "thirdPartyConsent": true,
						"participant": { "name": "김러너", "birth": "1994-06-25", "gender": "MALE",
							"phone": "010-1234-5678", "emergencyContactName": "김가족",
							"emergencyContactPhone": "010-9876-5432", "tshirtSize": "L",
						"shippingAddress": { "recipientName": "김러너", "recipientPhone": "010-1234-5678",
							"zipcode": "04700", "address": "서울 성동구 왕십리로 00", "addressDetail": "101동 1001호" } } }
					""".formatted(COURSE_ID)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(body, "$.data.id").longValue();
	}
}
