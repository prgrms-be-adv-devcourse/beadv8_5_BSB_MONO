package com.bukang.order.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.app.OrderExpiryUseCase;
import com.bukang.order.domain.Order;
import com.bukang.order.domain.OrderItem;
import com.bukang.order.domain.OrderStatus;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.domain.QueueAdmission;
import com.bukang.order.out.OrderItemRepository;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.QueueAdmissionRepository;
import com.bukang.order.support.QueueEntry;
import com.bukang.order.support.StubQueueAdmissionPort;
import com.bukang.order.support.StubQueueAdmissionPortConfig;
import com.bukang.order.support.StubRaceCoursePort;
import com.bukang.order.support.StubRaceCoursePortConfig;
import com.bukang.order.support.StubSeatHoldPort;
import com.bukang.order.support.StubSeatHoldPortConfig;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubSeatHoldPortConfig.class, StubQueueAdmissionPortConfig.class})
class ApiV1OrderTest {
	private static final String BASE_URL = "/api/v1/order/orders";
	private static final long MEMBER_ID = 1;
	private static final long OTHER_MEMBER_ID = 2;
	private static final long COURSE_ID = 10;
	private static final long RACE_ID = 100;
	private static final String ORDER_NO_PATTERN = "^OD-\\d{8}-[0-9A-F]{4}$";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OrderRepository orderRepository;

	// 참가자 정보 수정과 [결제하기]가 겹치는 경합을 만들 때만 응답을 바꾼다. 나머지는 실제 저장소 그대로다
	@MockitoSpyBean
	private OrderItemRepository orderItemRepository;

	@Autowired
	private ParticipationRepository participationRepository;

	@Autowired
	private QueueAdmissionRepository queueAdmissionRepository;

	@Autowired
	private StubRaceCoursePort raceCoursePort;

	@Autowired
	private StubSeatHoldPort seatHoldPort;

	@Autowired
	private StubQueueAdmissionPort queueAdmissionPort;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private OrderExpiryUseCase orderExpiryUseCase;

	@BeforeEach
	void setUp() {
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null));
	}

	@AfterEach
	void tearDown() {
		orderRepository.deleteAll();
		participationRepository.deleteAll();
		queueAdmissionRepository.deleteAll();
		raceCoursePort.clear();
		seatHoldPort.clear();
		queueAdmissionPort.clear();
	}

	@Test
	@DisplayName("[결제하기]하면 자리를 확보해 참가 신청은 APPLIED, 주문은 CREATED가 되고 결제 기한은 신청서 진입 때 받은 입장 끝 시각(15분 뒤)이다")
	void create() throws Exception {
		// DB(H2·MySQL)는 마이크로초까지만 저장한다. Linux의 now()는 나노초까지 나오므로 비교 기준도 맞춰 자른다
		LocalDateTime before = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
		long participationId = applyAndGetId(MEMBER_ID);

		create(MEMBER_ID, participationId, newKey())
			.andExpect(status().isCreated())
			.andExpect(header().exists("Location"))
			.andExpect(jsonPath("$.status").value(201))
			.andExpect(jsonPath("$.message").value("자리를 확보했습니다. 결제 기한 안에 결제해주세요."))
			.andExpect(jsonPath("$.data.orderNo").value(matchesPattern(ORDER_NO_PATTERN)))
			.andExpect(jsonPath("$.data.buyerId").value(MEMBER_ID))
			.andExpect(jsonPath("$.data.status").value("CREATED"))
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].participationId").value(participationId))
			.andExpect(jsonPath("$.data.items[0].quantity").value(1))
			.andExpect(jsonPath("$.data.items[0].status").value("ORDERED"));
		LocalDateTime after = LocalDateTime.now();

		Participation participation = participationRepository.findById(participationId).orElseThrow();
		assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.APPLIED);
		assertThat(participation.getActiveFlag()).isTrue();

		Order order = orderRepository.findAll().getFirst();
		assertThat(order.getPaymentDueAt())
			.isBetween(before.plusMinutes(StubQueueAdmissionPort.ADMISSION_MINUTES),
				after.plusMinutes(StubQueueAdmissionPort.ADMISSION_MINUTES))
			.isEqualTo(participation.getPaymentDueAt());
	}

	@Test
	@DisplayName("대기열에 입장 중이면 입장 끝 시각이 자리 확보 기한이자 결제 기한이 된다")
	void createUsesAdmissionEndsAt() throws Exception {
		LocalDateTime endsAt = LocalDateTime.now().plusMinutes(4).withNano(0);
		queueAdmissionPort.admitUntil(COURSE_ID, endsAt);
		long participationId = applyAndGetId(MEMBER_ID);

		create(MEMBER_ID, participationId, newKey()).andExpect(status().isCreated());

		assertThat(orderRepository.findAll().getFirst().getPaymentDueAt()).isEqualTo(endsAt);
		assertThat(participationRepository.findById(participationId).orElseThrow().getPaymentDueAt())
			.isEqualTo(endsAt);
	}

	@Test
	@DisplayName("대기열이 정책(예매창 15분)만큼 끝 시각을 주면 자르지 않고 그 끝 시각을 결제 기한으로 쓴다")
	void createKeepsFifteenMinuteAdmission() throws Exception {
		LocalDateTime endsAt = LocalDateTime.now().plusMinutes(15).withNano(0);
		queueAdmissionPort.admitUntil(COURSE_ID, endsAt);
		long participationId = applyAndGetId(MEMBER_ID);

		create(MEMBER_ID, participationId, newKey()).andExpect(status().isCreated());

		assertThat(orderRepository.findAll().getFirst().getPaymentDueAt()).isEqualTo(endsAt);
	}

	@Test
	@DisplayName("대기열이 정책(예매창 15분)보다 먼 끝 시각을 주면 결제 기한을 지금부터 15분으로 자른다")
	void createCapsAdmissionEndsAt() throws Exception {
		queueAdmissionPort.admitUntil(COURSE_ID, LocalDateTime.now().plusMinutes(16));
		long participationId = applyAndGetId(MEMBER_ID);

		LocalDateTime before = LocalDateTime.now();
		create(MEMBER_ID, participationId, newKey()).andExpect(status().isCreated());
		LocalDateTime after = LocalDateTime.now();

		assertThat(orderRepository.findAll().getFirst().getPaymentDueAt())
			.isBetween(before.plusMinutes(15), after.plusMinutes(15));
	}

	@Test
	@DisplayName("신청서 진입 때 입장하지 못했거나 저장한 입장 시간이 끝났으면 403이고, 자리를 확보하지 않는다")
	void createNotAdmitted() throws Exception {
		queueAdmissionPort.notAdmitted(COURSE_ID);
		long participationId = applyAndGetId(MEMBER_ID);

		create(MEMBER_ID, participationId, newKey())
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORDER_QUEUE_NOT_ADMITTED"))
			.andExpect(jsonPath("$.message").value("대기열에서 입장한 뒤 결제할 수 있습니다."));
		assertThat(queueAdmissionRepository.count()).isZero();
		queueAdmissionRepository.save(QueueAdmission.of(MEMBER_ID, COURSE_ID, LocalDateTime.now().minusSeconds(1)));
		create(MEMBER_ID, participationId, newKey()).andExpect(status().isForbidden());

		assertThat(seatHoldPort.holdCalls()).isZero();
		assertThat(orderRepository.count()).isZero();
		assertThat(participationRepository.findById(participationId).orElseThrow().getStatus())
			.isEqualTo(ParticipationStatus.PENDING);
	}

	@Test
	@DisplayName("신청서 진입 뒤에는 대기열에 다시 묻지 않아, 대기열이 입장 중이 아니라고 해도 저장한 끝 시각 안이면 결제 기한으로 쓴다")
	void createUsesSavedAdmissionWithoutAskingQueue() throws Exception {
		LocalDateTime endsAt = LocalDateTime.now().plusMinutes(4).withNano(0);
		queueAdmissionPort.admitUntil(COURSE_ID, endsAt);
		long participationId = applyAndGetId(MEMBER_ID);
		// 신청서를 쓰는 동안 대기열이 멈추거나 입장이 풀린 경우
		queueAdmissionPort.notAdmitted(COURSE_ID);

		create(MEMBER_ID, participationId, newKey()).andExpect(status().isCreated());

		assertThat(orderRepository.findAll().getFirst().getPaymentDueAt()).isEqualTo(endsAt);
	}

	@Test
	@DisplayName("입장 상태 API를 부르지 않고(주소 직접 입력) [결제하기]하면 대기열이 입장 중이라고 해도 403이다")
	void createWithoutEntry() throws Exception {
		long participationId = applyAndGetId(MEMBER_ID);
		queueAdmissionRepository.deleteAll();

		create(MEMBER_ID, participationId, newKey())
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORDER_QUEUE_NOT_ADMITTED"));
		assertThat(seatHoldPort.holdCalls()).isZero();
	}

	@Test
	@DisplayName("같은 키로 다시 보내면 입장 시간이 끝났어도 처음 주문을 돌려준다")
	void createRetryAfterAdmissionEnded() throws Exception {
		long participationId = applyAndGetId(MEMBER_ID);
		String key = newKey();
		long orderId = createAndGetId(MEMBER_ID, participationId, key);
		queueAdmissionPort.notAdmitted(COURSE_ID);

		create(MEMBER_ID, participationId, key)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(orderId));
	}

	@Test
	@DisplayName("주문 상품에 정산이 쓰는 판매자 ID·대회 ID·금액(참가비, 키트 포함)이 저장된다")
	void createStoresSettlementFields() throws Exception {
		long participationId = applyAndGetId(MEMBER_ID);
		long ticketPrice = StubRaceCoursePort.PRICE;

		create(MEMBER_ID, participationId, newKey())
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.raceId").value(RACE_ID))
			.andExpect(jsonPath("$.data.totalAmount").value(ticketPrice))
			.andExpect(jsonPath("$.data.items[0].sellerId").value(StubRaceCoursePort.SELLER_ID))
			.andExpect(jsonPath("$.data.items[0].raceCourseId").value(COURSE_ID))
			.andExpect(jsonPath("$.data.items[0].itemName").value("테스트 대회 종목 " + COURSE_ID))
			.andExpect(jsonPath("$.data.items[0].unitPrice").value(ticketPrice))
			.andExpect(jsonPath("$.data.items[0].amount").value(ticketPrice));

		// 주문 상품은 지연 로딩이라 트랜잭션 안에서 읽는다
		OrderItem item = transactionTemplate.execute(status ->
			orderRepository.findAll().getFirst().getItems().getFirst());
		assertThat(item.getRaceId()).isEqualTo(RACE_ID);
		assertThat(item.getSellerId()).isEqualTo(StubRaceCoursePort.SELLER_ID);
		assertThat(item.getAmount()).isEqualTo(ticketPrice);
	}

	@Test
	@DisplayName("결제 전 주문 응답에는 신청서에서 받은 키트 배송지가 보인다 (배송은 결제 때 만든다, BUK-6)")
	void createCopiesShippingAddress() throws Exception {
		long participationId = applyAndGetId(MEMBER_ID);

		create(MEMBER_ID, participationId, newKey())
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.shippingAddress.recipientName").value("김러너"))
			.andExpect(jsonPath("$.data.shippingAddress.recipientPhone").value("010-1234-5678"))
			.andExpect(jsonPath("$.data.shippingAddress.zipcode").value("04700"))
			.andExpect(jsonPath("$.data.shippingAddress.address").value("서울 성동구 왕십리로 00"))
			.andExpect(jsonPath("$.data.shippingAddress.addressDetail").value("101동 1001호"));
	}

	@Test
	@DisplayName("참가자 정보 수정이 주문을 찾은 뒤 [결제하기]가 주문을 만들면 409로 거절하고, 신청서의 배송지는 그대로다")
	void changeParticipantWhileOrderCreated() throws Exception {
		long participationId = applyAndGetId(MEMBER_ID);
		create(MEMBER_ID, participationId, newKey()).andExpect(status().isCreated());
		long orderId = orderRepository.findAll().getFirst().getId();
		// 수정이 주문을 찾을 때는 아직 없었고, 신청서를 잠그기 전에 [결제하기]가 주문을 만든 경합을 만든다
		doReturn(Optional.empty()).doReturn(Optional.of(orderId))
			.when(orderItemRepository).findOrderIdByParticipationId(participationId);

		mockMvc.perform(put("/api/v1/order/participations/" + participationId + "/participant")
				.header("X-Member-Id", MEMBER_ID)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "name": "김러너", "birth": "1994-06-25", "gender": "MALE", "phone": "010-1234-5678",
						"emergencyContactName": "김가족", "emergencyContactPhone": "010-9876-5432", "tshirtSize": "L",
						"shippingAddress": { "recipientName": "김가족", "recipientPhone": "010-9876-5432",
							"zipcode": "48094", "address": "부산 해운대구 해운대로 00" } }
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("방금 주문이 만들어졌습니다. 잠시 뒤 다시 시도해주세요."))
			.andExpect(jsonPath("$.code").value("ORDER_PARTICIPATION_CHANGE_RETRY"));

		assertThat(participationRepository.findById(participationId).orElseThrow()
			.getParticipant().getShippingAddress().getZipcode()).isEqualTo("04700");
	}

	@Test
	@DisplayName("주문을 만든 뒤 참가자 정보에서 배송지를 고치면 결제 전 주문 응답의 배송지도 바뀐다")
	void changeParticipantUpdatesOrderShippingAddress() throws Exception {
		long participationId = applyAndGetId(MEMBER_ID);
		create(MEMBER_ID, participationId, newKey()).andExpect(status().isCreated());

		mockMvc.perform(put("/api/v1/order/participations/" + participationId + "/participant")
				.header("X-Member-Id", MEMBER_ID)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "name": "김러너", "birth": "1994-06-25", "gender": "MALE", "phone": "010-1234-5678",
						"emergencyContactName": "김가족", "emergencyContactPhone": "010-9876-5432", "tshirtSize": "L",
						"shippingAddress": { "recipientName": "김가족", "recipientPhone": "010-9876-5432",
							"zipcode": "48094", "address": "부산 해운대구 해운대로 00" } }
					"""))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/order/orders/" + orderRepository.findAll().getFirst().getId())
				.header("X-Member-Id", MEMBER_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.shippingAddress.recipientName").value("김가족"))
			.andExpect(jsonPath("$.data.shippingAddress.zipcode").value("48094"))
			.andExpect(jsonPath("$.data.shippingAddress.address").value("부산 해운대구 해운대로 00"))
			.andExpect(jsonPath("$.data.shippingAddress.addressDetail").isEmpty());
	}

	@Test
	@DisplayName("같은 Idempotency-Key로 다시 보내면 200으로 처음 주문을 돌려주고, 자리는 한 번만 확보한다")
	void createWithSameKey() throws Exception {
		long participationId = applyAndGetId(MEMBER_ID);
		String key = newKey();
		long orderId = createAndGetId(MEMBER_ID, participationId, key);

		create(MEMBER_ID, participationId, key)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("이미 처리한 주문 요청입니다."))
			.andExpect(jsonPath("$.data.id").value(orderId));

		assertThat(orderRepository.count()).isEqualTo(1);
		assertThat(seatHoldPort.holdCalls()).isEqualTo(1);
	}

	@Test
	@DisplayName("같은 키를 다른 신청서에 다시 쓰면 처음 주문을 돌려주지 않고 409다")
	void reuseKeyForOtherParticipation() throws Exception {
		String key = newKey();
		createAndGetId(MEMBER_ID, applyAndGetId(MEMBER_ID), key);
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID + 1, RACE_ID + 1, 14, null));
		long otherRace = applyAndGetId(MEMBER_ID, COURSE_ID + 1);

		create(MEMBER_ID, otherRace, key)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 다른 주문에 쓴 Idempotency-Key입니다. 새 키로 다시 요청해주세요."))
			.andExpect(jsonPath("$.code").value("ORDER_IDEMPOTENCY_KEY_REUSED"));

		assertThat(participationRepository.findById(otherRace).orElseThrow().getStatus())
			.isEqualTo(ParticipationStatus.PENDING);
	}

	@Test
	@DisplayName("다른 키로 같은 신청서를 다시 보내면 이미 결제 대기라 409다")
	void createTwiceWithDifferentKey() throws Exception {
		long participationId = applyAndGetId(MEMBER_ID);
		create(MEMBER_ID, participationId, newKey()).andExpect(status().isCreated());

		create(MEMBER_ID, participationId, newKey())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("결제를 시작할 수 없는 신청입니다(현재 상태: 결제 대기)."))
			.andExpect(jsonPath("$.code").value("ORDER_PARTICIPATION_NOT_PAYABLE"));

		assertThat(orderRepository.count()).isEqualTo(1);
		assertThat(seatHoldPort.holdCalls()).isEqualTo(1);
	}

	@Test
	@DisplayName("남은 자리가 없으면 409, 참가 신청은 FAILED로 끝나 주문이 없고 같은 대회에 다시 신청할 수 있다")
	void soldOut() throws Exception {
		long participationId = applyAndGetId(MEMBER_ID);
		seatHoldPort.setRemainingSeats(COURSE_ID, 0);

		create(MEMBER_ID, participationId, newKey())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.message").value("남은 자리가 없습니다. 참가 신청서를 다시 작성해 신청할 수 있습니다."))
			.andExpect(jsonPath("$.code").value("ORDER_SOLD_OUT"))
			.andExpect(jsonPath("$.data").isEmpty());

		Participation participation = participationRepository.findById(participationId).orElseThrow();
		assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.FAILED);
		assertThat(participation.getActiveFlag()).isNull();
		assertThat(orderRepository.count()).isZero();

		applyAndGetId(MEMBER_ID);
	}

	@Test
	@DisplayName("매진으로 끝난 신청서로 같은 키를 다시 보내도 처음과 같은 매진 응답을 받는다")
	void soldOutRetryWithSameKey() throws Exception {
		long participationId = applyAndGetId(MEMBER_ID);
		seatHoldPort.setRemainingSeats(COURSE_ID, 0);
		String key = newKey();
		create(MEMBER_ID, participationId, key).andExpect(status().isConflict());

		create(MEMBER_ID, participationId, key)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("남은 자리가 없습니다. 참가 신청서를 다시 작성해 신청할 수 있습니다."))
			.andExpect(jsonPath("$.code").value("ORDER_SOLD_OUT"));
		assertThat(seatHoldPort.holdCalls()).isEqualTo(1);
	}

	@Test
	@DisplayName("마지막 1석에 두 명이 동시에 [결제하기]하면 한 명만 자리를 확보한다")
	void lastSeatRace() throws Exception {
		long first = applyAndGetId(MEMBER_ID);
		long second = applyAndGetId(OTHER_MEMBER_ID);
		seatHoldPort.setRemainingSeats(COURSE_ID, 1);

		CountDownLatch start = new CountDownLatch(1);
		List<Callable<Integer>> requests = List.of(
			() -> createStatusAfter(start, MEMBER_ID, first),
			() -> createStatusAfter(start, OTHER_MEMBER_ID, second));
		List<Integer> statuses = new ArrayList<>();
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			List<Future<Integer>> futures = requests.stream().map(executor::submit).toList();
			start.countDown();
			for (Future<Integer> future : futures) {
				statuses.add(future.get());
			}
		}

		assertThat(statuses).containsExactlyInAnyOrder(201, 409);
		assertThat(orderRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("접수 기간이 아니면 409이고 자리를 확보하지 않으며 참가 신청은 PENDING 그대로다")
	void notAccepting() throws Exception {
		long participationId = applyAndGetId(MEMBER_ID);
		raceCoursePort.put(StubRaceCoursePort.closedCourse(COURSE_ID, RACE_ID));

		create(MEMBER_ID, participationId, newKey())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("접수 기간이 아닙니다."))
			.andExpect(jsonPath("$.code").value("ORDER_APPLICATION_CLOSED"));

		assertThat(seatHoldPort.holdCalls()).isZero();
		assertThat(participationRepository.findById(participationId).orElseThrow().getStatus())
			.isEqualTo(ParticipationStatus.PENDING);
	}

	@Test
	@DisplayName("다른 회원의 신청서는 403, 없는 신청서는 404다")
	void notOwnerOrMissing() throws Exception {
		long participationId = applyAndGetId(MEMBER_ID);

		create(OTHER_MEMBER_ID, participationId, newKey())
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("본인의 참가 신청만 결제할 수 있습니다."))
			.andExpect(jsonPath("$.code").value("ORDER_PARTICIPATION_FORBIDDEN"));
		create(MEMBER_ID, 999999, newKey())
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("존재하지 않는 참가 신청입니다."))
			.andExpect(jsonPath("$.code").value("ORDER_PARTICIPATION_NOT_FOUND"));

		assertThat(seatHoldPort.holdCalls()).isZero();
	}

	@Test
	@DisplayName("Idempotency-Key가 없으면 400, 64자를 넘으면 400이다")
	void invalidIdempotencyKey() throws Exception {
		long participationId = applyAndGetId(MEMBER_ID);

		mockMvc.perform(post(BASE_URL)
				.header("X-Member-Id", MEMBER_ID)
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody(participationId)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("필수 요청 헤더(Idempotency-Key)가 없습니다."))
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		create(MEMBER_ID, participationId, "k".repeat(65))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Idempotency-Key는 1~64자로 보내주세요."))
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("참가 신청 ID가 없으면 400이다")
	void missingParticipationId() throws Exception {
		mockMvc.perform(post(BASE_URL)
				.header("X-Member-Id", MEMBER_ID)
				.header("Idempotency-Key", newKey())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("결제할 참가 신청을 선택해주세요."))
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("만료 정리: 기한 1초 전에는 그대로, 기한이 되면 참가 신청 RELEASED·주문 EXPIRED가 되고 다시 신청할 수 있다")
	void expireOverdue() throws Exception {
		long participationId = applyAndGetId(MEMBER_ID);
		long orderId = createAndGetId(MEMBER_ID, participationId, newKey());
		LocalDateTime due = orderRepository.findById(orderId).orElseThrow().getPaymentDueAt();

		OrderExpiryUseCase.ExpiryResult early = orderExpiryUseCase.expireOverdue(due.minusSeconds(1));
		assertThat(early.releasedParticipations()).isZero();
		assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CREATED);

		OrderExpiryUseCase.ExpiryResult onTime = orderExpiryUseCase.expireOverdue(due);
		assertThat(onTime.releasedParticipations()).isEqualTo(1);
		assertThat(onTime.expiredOrders()).isEqualTo(1);
		Participation released = participationRepository.findById(participationId).orElseThrow();
		assertThat(released.getStatus()).isEqualTo(ParticipationStatus.RELEASED);
		assertThat(released.getActiveFlag()).isNull();
		assertThat(released.getReleasedAt()).isEqualTo(due);
		Order expired = orderRepository.findById(orderId).orElseThrow();
		assertThat(expired.getStatus()).isEqualTo(OrderStatus.EXPIRED);

		// 같은 사람이 같은 대회에 새 신청서를 쓸 수 있다
		applyAndGetId(MEMBER_ID);
	}

	@Test
	@DisplayName("만료 정리를 두 번 돌려도 결과가 같고, 해제 시각도 바뀌지 않는다")
	void expireOverdueTwice() throws Exception {
		long participationId = applyAndGetId(MEMBER_ID);
		createAndGetId(MEMBER_ID, participationId, newKey());
		// DB는 마이크로초까지만 저장하므로, 저장된 해제 시각과 비교할 수 있게 잘라서 넣는다
		LocalDateTime later = LocalDateTime.now().plusMinutes(30).truncatedTo(ChronoUnit.MICROS);

		orderExpiryUseCase.expireOverdue(later);
		OrderExpiryUseCase.ExpiryResult second = orderExpiryUseCase.expireOverdue(later.plusMinutes(1));

		assertThat(second.releasedParticipations()).isZero();
		assertThat(second.expiredOrders()).isZero();
		assertThat(participationRepository.findById(participationId).orElseThrow().getReleasedAt()).isEqualTo(later);
	}

	@Test
	@DisplayName("만료 정리는 결제 대기 건만 건드리고, 자리 확보 전(PENDING) 신청은 그대로 둔다")
	void expireOverdueSkipsPending() throws Exception {
		long pending = applyAndGetId(MEMBER_ID);

		orderExpiryUseCase.expireOverdue(LocalDateTime.now().plusDays(1));

		Participation participation = participationRepository.findById(pending).orElseThrow();
		assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.PENDING);
		assertThat(participation.getActiveFlag()).isTrue();
	}

	@Test
	@DisplayName("결제 기한 늘리기 API는 없다 (10/14 연장 없음으로 정함)")
	void deadlineExtensionRemoved() throws Exception {
		long orderId = createAndGetId(MEMBER_ID, applyAndGetId(MEMBER_ID), newKey());

		mockMvc.perform(post(BASE_URL + "/" + orderId + "/deadline-extension").header("X-Member-Id", MEMBER_ID))
			.andExpect(status().isNotFound());
	}

	private Integer createStatusAfter(CountDownLatch start, long memberId, long participationId) throws Exception {
		start.await();
		return create(memberId, participationId, newKey()).andReturn().getResponse().getStatus();
	}

	private ResultActions create(long memberId, long participationId, String key) throws Exception {
		return mockMvc.perform(post(BASE_URL)
			.header("X-Member-Id", memberId)
			.header("Idempotency-Key", key)
			.contentType(MediaType.APPLICATION_JSON)
			.content(createBody(participationId)));
	}

	private long createAndGetId(long memberId, long participationId, String key) throws Exception {
		String body = create(memberId, participationId, key)
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(body, "$.data.id").longValue();
	}

	private long applyAndGetId(long memberId) throws Exception {
		return applyAndGetId(memberId, COURSE_ID);
	}

	private long applyAndGetId(long memberId, long raceCourseId) throws Exception {
		QueueEntry.enter(mockMvc, memberId, raceCourseId);
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
					""".formatted(raceCourseId)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(body, "$.data.id").longValue();
	}

	private static String createBody(long participationId) {
		return "{\"participationId\": %d}".formatted(participationId);
	}

	private static String newKey() {
		return UUID.randomUUID().toString();
	}
}
