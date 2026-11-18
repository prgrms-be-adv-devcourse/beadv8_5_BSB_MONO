package com.bukang.order.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.shared.notification.NotificationKafkaSender;
import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.order.domain.Order;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.in.OrderKafkaConsumer;
import com.bukang.order.out.OrderInboxKafkaEventRepository;
import com.bukang.order.out.OrderOutboxKafkaEventRepository;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.RaceScheduleNoticeRepository;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.support.QueueEntry;
import com.bukang.order.support.StubRaceCoursePort;
import com.bukang.order.support.StubRaceCoursePortConfig;
import com.bukang.order.support.StubSeatHoldPort;
import com.bukang.order.support.StubSeatHoldPortConfig;
import com.bukang.order.support.StubWalletPort;
import com.bukang.order.support.StubWalletPortConfig;
import com.jayway.jsonpath.JsonPath;

/**
 * 대회 변경 메시지에 새 일정 변경이 실리면 결제한 참가권의 구매자에게 한 번씩 알리는지 본다 (BUK-82)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubSeatHoldPortConfig.class, StubWalletPortConfig.class})
class RaceScheduleNoticeTest {
	private static final long COURSE_ID = 40;
	private static final long RACE_ID = 400;
	private static final long OTHER_COURSE_ID = 41;
	private static final long OTHER_RACE_ID = 401;
	private static final long BALANCE = 100_000_000;
	private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OrderKafkaConsumer orderKafkaConsumer;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private ParticipationRepository participationRepository;

	@Autowired
	private OrderInboxKafkaEventRepository orderInboxKafkaEventRepository;

	@Autowired
	private OrderOutboxKafkaEventRepository orderOutboxKafkaEventRepository;

	@Autowired
	private RaceScheduleNoticeRepository raceScheduleNoticeRepository;

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
	private TransactionTemplate transactionTemplate;

	@MockitoSpyBean
	private NotificationKafkaSender notificationSender;

	@BeforeEach
	void setUp() {
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null));
		raceCoursePort.put(StubRaceCoursePort.openCourse(OTHER_COURSE_ID, OTHER_RACE_ID, 14, null));
		walletPort.setBalance(BALANCE);
		// Kafka로 보내지 않고 불렸는지만 본다 (알림은 커밋된 뒤에만 보낸다)
		doNothing().when(notificationSender).send(any());
	}

	@AfterEach
	void tearDown() {
		orderRepository.deleteAll();
		participationRepository.deleteAll();
		orderInboxKafkaEventRepository.deleteAll();
		orderOutboxKafkaEventRepository.deleteAll();
		raceScheduleNoticeRepository.deleteAll();
		raceCoursePort.clear();
		seatHoldPort.clear();
		walletPort.clear();
	}

	@Test
	@DisplayName("새 일정 변경이면 그 대회의 결제한 참가권마다 구매자에게 참가권 안내 링크로 한 번 알린다")
	void notifiesEachPaidTicket() throws Exception {
		long first = paidOrder(1, COURSE_ID);
		long second = paidOrder(2, COURSE_ID);
		createOrder(3, COURSE_ID);
		paidOrder(4, OTHER_COURSE_ID);
		LocalDateTime changedAt = LocalDateTime.now().withNano(0);
		LocalDateTime raceDate = LocalDateTime.of(2027, 3, 1, 8, 30);

		orderKafkaConsumer.handleRaceChanged(payload(RACE_ID, raceDate, changedAt), "m-1");

		ArgumentCaptor<NotificationRequestedEvent> sent = ArgumentCaptor.forClass(NotificationRequestedEvent.class);
		verify(notificationSender, times(2)).send(sent.capture());
		assertThat(sent.getAllValues())
			.extracting(NotificationRequestedEvent::memberId, NotificationRequestedEvent::kind,
				NotificationRequestedEvent::href)
			.containsExactlyInAnyOrder(
				tuple(1L, NotificationKind.RACE_SCHEDULE_CHANGED, "/tickets/%d-%d/notice".formatted(first,
					itemIdOf(first))),
				tuple(2L, NotificationKind.RACE_SCHEDULE_CHANGED, "/tickets/%d-%d/notice".formatted(second,
					itemIdOf(second))));
		// 바뀐 날을 1일째로 7일째 날까지 전액 환불된다 (RaceCourseInfo.scheduleChangeRefundLastDay)
		LocalDateTime lastDay = changedAt.plusDays(6);
		assertThat(sent.getAllValues().getFirst().body()).isEqualTo(
			"테스트 대회 종목 %d 3월 1일 08:30.\n%d월 %d일까지 전액 환불할 수 있어요.".formatted(COURSE_ID,
				lastDay.getMonthValue(), lastDay.getDayOfMonth()));
	}

	@Test
	@DisplayName("같은 일정 변경은 메시지 ID가 같든(재전달) 다르든(다른 칸 수정) 다시 알리지 않고, 더 새 변경은 다시 알린다")
	void notifiesOncePerChange() throws Exception {
		paidOrder(1, COURSE_ID);
		LocalDateTime changedAt = LocalDateTime.now().minusDays(1).withNano(0);
		LocalDateTime raceDate = LocalDateTime.of(2027, 3, 1, 8, 30);

		orderKafkaConsumer.handleRaceChanged(payload(RACE_ID, raceDate, changedAt), "m-1");
		orderKafkaConsumer.handleRaceChanged(payload(RACE_ID, raceDate, changedAt), "m-1");
		orderKafkaConsumer.handleRaceChanged(payload(RACE_ID, raceDate, changedAt), "m-2");
		// 늦게 온 옛 변경도 다시 알리지 않는다
		orderKafkaConsumer.handleRaceChanged(payload(RACE_ID, raceDate, changedAt.minusHours(1)), "m-3");
		verify(notificationSender, times(1)).send(any());

		orderKafkaConsumer.handleRaceChanged(payload(RACE_ID, raceDate.plusDays(1), changedAt.plusHours(1)), "m-4");
		verify(notificationSender, times(2)).send(any());
	}

	@Test
	@DisplayName("일정 변경 일시가 없거나 전액 환불 7일이 이미 지난 변경이면 알리지 않는다")
	void skipsWithoutChangeOrAfterRefundWindow() throws Exception {
		paidOrder(1, COURSE_ID);
		LocalDateTime raceDate = LocalDateTime.of(2027, 3, 1, 8, 30);

		orderKafkaConsumer.handleRaceChanged(payload(RACE_ID, raceDate, null), "m-1");
		orderKafkaConsumer.handleRaceChanged(payload(RACE_ID, raceDate, LocalDateTime.now().minusDays(7)), "m-2");

		verify(notificationSender, never()).send(any());
	}

	@Test
	@DisplayName("상품이 대회명을 주면 문구에 대회명을 쓴다")
	void usesRaceNameFromProduct() throws Exception {
		paidOrder(1, COURSE_ID);
		RaceCourseInfo course = StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null);
		raceCoursePort.put(new RaceCourseInfo(course.raceCourseId(), course.raceId(), course.raceDate(),
			course.applicationOpenAt(), course.applicationCloseAt(), course.minAge(), course.maxAge(),
			course.sellerId(), course.courseName(), course.price(), course.kitIncluded(), course.refundDeadline(),
			course.scheduleChangedAt(), false, course.tshirtSizeOption(), null, "서울 마라톤", "10K"));

		orderKafkaConsumer.handleRaceChanged(
			payload(RACE_ID, LocalDateTime.of(2027, 3, 1, 8, 30), LocalDateTime.now().withNano(0)), "m-1");

		ArgumentCaptor<NotificationRequestedEvent> sent = ArgumentCaptor.forClass(NotificationRequestedEvent.class);
		verify(notificationSender).send(sent.capture());
		assertThat(sent.getValue().body()).startsWith("서울 마라톤 3월 1일 08:30.");
	}

	@Test
	@DisplayName("상품 조회가 실패하면 알린 기록도 남기지 않아, 다시 처리할 때 한 번만 알린다")
	void retriesAfterProductFailure() throws Exception {
		paidOrder(1, COURSE_ID);
		LocalDateTime changedAt = LocalDateTime.now().withNano(0);
		String payload = payload(RACE_ID, LocalDateTime.of(2027, 3, 1, 8, 30), changedAt);
		raceCoursePort.setUnavailable(true);

		assertThatThrownBy(() -> orderKafkaConsumer.handleRaceChanged(payload, "m-1"))
			.isInstanceOf(ExternalServiceUnavailableException.class);
		assertThat(raceScheduleNoticeRepository.count()).isZero();
		verify(notificationSender, never()).send(any());

		raceCoursePort.setUnavailable(false);
		orderKafkaConsumer.handleRaceChanged(payload, "m-1");
		orderKafkaConsumer.handleRaceChanged(payload, "m-1");
		verify(notificationSender, times(1)).send(any());
	}

	// 상품 RaceChangedEvent 모양 그대로 (주문이 읽지 않는 칸도 넣는다)
	private String payload(long raceId, LocalDateTime raceDate, LocalDateTime scheduleChangedAt) {
		return """
			{"raceId":%d,"saleStatus":"ON_SALE","individualOpenAt":"2026-10-01T10:00:00",
			"applicationCloseAt":"2027-02-01T18:00:00","crewSaleStartAt":null,"crewSaleEndAt":null,
			"updatedAt":"%s","raceDate":"%s","scheduleChangedAt":%s}
			""".formatted(raceId, LocalDateTime.now().format(ISO), raceDate.format(ISO),
			scheduleChangedAt == null ? "null" : "\"" + scheduleChangedAt.format(ISO) + "\"");
	}

	private long itemIdOf(long orderId) {
		return transactionTemplate.execute(status -> {
			Order order = orderRepository.findById(orderId).orElseThrow();
			return order.getItems().getFirst().getId();
		});
	}

	private long paidOrder(long memberId, long courseId) throws Exception {
		long orderId = createOrder(memberId, courseId);
		transactionTemplate.executeWithoutResult(status ->
			orderPayUseCase.pay(memberId, orderId, List.of(), LocalDateTime.now()));
		return orderId;
	}

	private long createOrder(long memberId, long courseId) throws Exception {
		long participationId = apply(memberId, courseId);
		OrderCreateResult result = transactionTemplate.execute(status -> orderCreateUseCase.create(memberId,
			participationId, UUID.randomUUID().toString(), LocalDateTime.now()));
		return result.order().getId();
	}

	private long apply(long memberId, long courseId) throws Exception {
		QueueEntry.enter(mockMvc, memberId, courseId);
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
					""".formatted(courseId)))
			.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(body, "$.data.id").longValue();
	}
}
