package com.bukang.order.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.order.domain.KitOptionSale;
import com.bukang.order.domain.Order;
import com.bukang.order.domain.OrderStatus;
import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.out.KitOptionSaleRepository;
import com.bukang.order.out.OrderItemRepository;
import com.bukang.order.out.OrderOutboxKafkaEventRepository;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationKitOptionRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.QueueAdmissionRepository;
import com.bukang.order.out.ShipmentRepository;
import com.bukang.order.support.QueueEntry;
import com.bukang.order.support.StubKitOptionPort;
import com.bukang.order.support.StubRaceCoursePort;
import com.bukang.order.support.StubSeatHoldPort;
import com.bukang.order.support.StubWalletPort;
import com.jayway.jsonpath.JsonPath;

// 결제 나누기(BUK-232) 테스트가 함께 쓰는 준비와 확인
abstract class OrderPaySplitTestSupport {
	static final String BASE_URL = "/api/v1/order/orders";
	static final long COURSE_ID = 10;
	static final long RACE_ID = 100;
	static final long SHIRT_M = 11;
	static final long BALANCE = 1_000_000;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	OrderRepository orderRepository;

	@Autowired
	OrderItemRepository orderItemRepository;

	@Autowired
	ParticipationRepository participationRepository;

	@Autowired
	QueueAdmissionRepository queueAdmissionRepository;

	@Autowired
	ShipmentRepository shipmentRepository;

	@Autowired
	KitOptionSaleRepository kitOptionSaleRepository;

	@Autowired
	ParticipationKitOptionRepository participationKitOptionRepository;

	@Autowired
	OrderOutboxKafkaEventRepository orderOutboxKafkaEventRepository;

	@Autowired
	StubRaceCoursePort raceCoursePort;

	@Autowired
	StubSeatHoldPort seatHoldPort;

	@Autowired
	StubWalletPort walletPort;

	@Autowired
	StubKitOptionPort kitOptionPort;

	@Autowired
	OrderPayUseCase orderPayUseCase;

	@Autowired
	OrderExpiryUseCase orderExpiryUseCase;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Autowired
	JsonConverter jsonConverter;

	@BeforeEach
	void setUpSplit() {
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null));
		kitOptionPort.add(RACE_ID, SHIRT_M, "T-shirt", "M", 30);
		walletPort.setBalance(BALANCE);
	}

	@AfterEach
	void tearDownSplit() {
		shipmentRepository.deleteAll();
		orderRepository.deleteAll();
		participationRepository.deleteAll();
		queueAdmissionRepository.deleteAll();
		participationKitOptionRepository.deleteAll();
		kitOptionSaleRepository.deleteAll();
		orderOutboxKafkaEventRepository.deleteAll();
		raceCoursePort.clear();
		seatHoldPort.clear();
		walletPort.clear();
		kitOptionPort.clear();
	}

	ResultActions pay(long memberId, long orderId) throws Exception {
		return mockMvc.perform(post(BASE_URL + "/" + orderId + "/payment").header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"kitOptionIds\": [" + SHIRT_M + "]}"));
	}

	// 신청서 작성 → [결제하기]로 결제 대기 주문을 만든다
	long createOrder(long memberId) throws Exception {
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
		return read(mockMvc.perform(post(BASE_URL)
			.header("X-Member-Id", memberId)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"participationId\": %d}".formatted(participationId))));
	}

	long read(ResultActions result) throws Exception {
		String body = result.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(body, "$.data.id").longValue();
	}

	Order orderOf(long orderId) {
		return orderRepository.findById(orderId).orElseThrow();
	}

	OrderStatus statusOf(long orderId) {
		return orderOf(orderId).getStatus();
	}

	LocalDateTime dueOf(long orderId) {
		return orderOf(orderId).getPaymentDueAt();
	}

	long participationIdOf(long orderId) {
		return transactionTemplate.execute(status -> orderOf(orderId).participationId());
	}

	ParticipationStatus participationStatusOf(long orderId) {
		return participationRepository.findById(participationIdOf(orderId)).orElseThrow().getStatus();
	}

	int shipmentsOf(long orderId) {
		return transactionTemplate.execute(status -> orderOf(orderId).getShipments().size());
	}

	int sold(long kitOptionId) {
		return kitOptionSaleRepository.findById(kitOptionId).map(KitOptionSale::getSold).orElse(0);
	}

	long choicesOf(long orderId) {
		return participationKitOptionRepository.findByParticipationIdOrderByIdAsc(participationIdOf(orderId)).size();
	}
}
