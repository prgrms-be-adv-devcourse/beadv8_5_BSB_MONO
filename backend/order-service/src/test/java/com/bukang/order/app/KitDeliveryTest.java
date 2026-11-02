package com.bukang.order.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.order.domain.KitDeliveryStatus;
import com.bukang.order.domain.Order;
import com.bukang.order.domain.OrderItem;
import com.bukang.order.domain.OrderItemStatus;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.Shipment;
import com.bukang.order.domain.ShipmentStatus;
import com.bukang.order.domain.exception.KitNotShippableException;
import com.bukang.order.domain.exception.KitShippingInfoInvalidException;
import com.bukang.order.domain.exception.OrderAccessDeniedException;
import com.bukang.order.domain.exception.OrderItemNotFoundException;
import com.bukang.order.dto.OrderItemDto;
import com.bukang.order.dto.SellerKitDto;
import com.bukang.order.dto.SellerKitSummaryDto;
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
import com.bukang.order.support.StubWalletPort;
import com.bukang.order.support.StubWalletPortConfig;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.Table;

/**
 * 판매자가 키트를 발송 처리하고, 구매자 주문 조회에 배송 상태가 보이는지 본다 (PRO-136)
 * 결제·취소·대회 취소·주소 수정이 배송(ORDER_SHIPMENT)을 어떻게 바꾸는지도 본다 (BUK-6)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubSeatHoldPortConfig.class, StubWalletPortConfig.class,
	StubRefundEligibilityPortConfig.class})
class KitDeliveryTest {
	private static final long COURSE_ID = 40;
	private static final long OPEN_COURSE_ID = 41;
	private static final String CARRIER = "CJ대한통운";
	private static final String TRACKING_NUMBER = "684912345678";
	private static final long RACE_ID = 400;
	private static final long MEMBER_ID = 1;
	private static final long SELLER_ID = StubRaceCoursePort.SELLER_ID;
	private static final long OTHER_SELLER_ID = SELLER_ID + 1;

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
	private KitDeliveryUseCase kitDeliveryUseCase;

	@Autowired
	private RaceCancelRefundUseCase raceCancelRefundUseCase;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private StubRaceCoursePort raceCoursePort;

	@Autowired
	private StubSeatHoldPort seatHoldPort;

	@Autowired
	private StubWalletPort walletPort;

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
	}

	@Test
	@DisplayName("결제한 키트는 배송 준비 → 배송 중(송장) → 배송 완료로 바뀌고, 구매자 주문 조회에 그대로 보인다")
	void shipAndDeliverAfterRefundDeadline() throws Exception {
		raceCoursePort.put(courseWithRefundDeadline(LocalDate.now().minusDays(1)));
		long orderId = paidOrder();
		long itemId = itemIdOf(orderId);
		findOrder(orderId)
			.andExpect(jsonPath("$.data.items[0].kitDeliveryStatus").value("PREPARING"))
			.andExpect(jsonPath("$.data.items[0].kitTrackingNumber").isEmpty())
			.andExpect(jsonPath("$.data.items[0].kitShippedAt").isEmpty());

		// DB는 마이크로초까지만 저장한다. 리눅스(CI)는 지금 시각을 나노초까지 줘 저장한 값과 비교하면 어긋난다
		LocalDateTime shippedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
		OrderItemDto shipped = kitDeliveryUseCase.ship(SELLER_ID, itemId, CARRIER, TRACKING_NUMBER, shippedAt);

		assertThat(shipped.getKitDeliveryStatus()).isEqualTo(KitDeliveryStatus.SHIPPING);
		findOrder(orderId)
			.andExpect(jsonPath("$.data.items[0].kitDeliveryStatus").value("SHIPPING"))
			.andExpect(jsonPath("$.data.items[0].kitCarrier").value(CARRIER))
			.andExpect(jsonPath("$.data.items[0].kitTrackingNumber").value(TRACKING_NUMBER))
			.andExpect(jsonPath("$.data.items[0].kitDeliveredAt").isEmpty());
		assertThat(shipmentOf(orderId).getShippedAt()).isEqualTo(shippedAt);

		LocalDateTime deliveredAt = shippedAt.plusDays(2);
		kitDeliveryUseCase.completeDelivery(SELLER_ID, itemId, deliveredAt);

		findOrder(orderId)
			.andExpect(jsonPath("$.data.items[0].kitDeliveryStatus").value("DELIVERED"))
			.andExpect(jsonPath("$.data.items[0].kitDeliveredAt").isNotEmpty());
		assertThat(shipmentOf(orderId).getDeliveredAt()).isEqualTo(deliveredAt);
	}

	@Test
	@DisplayName("배송 완료는 배송 중인 키트만, 한 번만 바꾼다")
	void completeDeliveryRejected() throws Exception {
		raceCoursePort.put(courseWithRefundDeadline(LocalDate.now().minusDays(1)));
		long itemId = itemIdOf(paidOrder());

		assertThatThrownBy(() -> kitDeliveryUseCase.completeDelivery(SELLER_ID, itemId, LocalDateTime.now()))
			.isInstanceOf(KitNotShippableException.class)
			.hasMessage("아직 발송하지 않은 키트입니다.");
		kitDeliveryUseCase.ship(SELLER_ID, itemId, CARRIER, TRACKING_NUMBER, LocalDateTime.now());
		assertThatThrownBy(() -> kitDeliveryUseCase.completeDelivery(OTHER_SELLER_ID, itemId, LocalDateTime.now()))
			.isInstanceOf(OrderAccessDeniedException.class);
		kitDeliveryUseCase.completeDelivery(SELLER_ID, itemId, LocalDateTime.now());
		assertThatThrownBy(() -> kitDeliveryUseCase.completeDelivery(SELLER_ID, itemId, LocalDateTime.now()))
			.isInstanceOf(KitNotShippableException.class)
			.hasMessage("이미 배송 완료한 키트입니다.");
		assertThatThrownBy(() -> ship(itemId))
			.isInstanceOf(KitNotShippableException.class)
			.hasMessage("이미 발송한 키트입니다.");
	}

	@Test
	@DisplayName("판매자 키트 목록은 결제한 내 키트만 상태로 걸러 보이고, 건수는 상태별·환불 기한 전으로 센다")
	void sellerKitsAndSummary() throws Exception {
		raceCoursePort.put(courseWithRefundDeadline(LocalDate.now().minusDays(1)));
		raceCoursePort.put(StubRaceCoursePort.openCourse(OPEN_COURSE_ID, RACE_ID, 14, null,
			LocalDate.now().plusDays(10)));
		long shippingItemId = itemIdOf(paidOrder(MEMBER_ID));
		long preparingOrderId = paidOrder(MEMBER_ID + 1);
		long beforeDeadlineItemId = itemIdOf(paidOrder(MEMBER_ID + 2, OPEN_COURSE_ID));
		createOrder(MEMBER_ID + 3, COURSE_ID);
		createOrder(MEMBER_ID + 4, OPEN_COURSE_ID);
		ship(shippingItemId);

		assertThat(kitDeliveryUseCase.findKits(SELLER_ID, null, null, 0, 20).getKits())
			.extracting(SellerKitDto::getOrderItemId)
			.containsExactly(beforeDeadlineItemId, itemIdOf(preparingOrderId), shippingItemId);
		List<SellerKitDto> preparing =
			kitDeliveryUseCase.findKits(SELLER_ID, RACE_ID, KitDeliveryStatus.PREPARING, 0, 20).getKits();
		assertThat(preparing).extracting(SellerKitDto::getOrderItemId)
			.containsExactly(beforeDeadlineItemId, itemIdOf(preparingOrderId));
		SellerKitDto row = preparing.get(1);
		assertThat(row.getOrderNo()).isNotBlank();
		assertThat(row.getParticipantName()).isEqualTo("김러너");
		assertThat(row.getTshirtSize()).isEqualTo("L");
		assertThat(row.getShippingAddress().getAddress()).isEqualTo("서울 성동구 왕십리로 00");
		assertThat(kitDeliveryUseCase.findKits(OTHER_SELLER_ID, null, null, 0, 20).getKits()).isEmpty();
		assertThat(kitDeliveryUseCase.findKits(SELLER_ID, RACE_ID + 1, null, 0, 20).getKits()).isEmpty();

		SellerKitSummaryDto summary = kitDeliveryUseCase.summarize(SELLER_ID, null, LocalDateTime.now());
		assertThat(summary.getPreparing()).isEqualTo(2);
		assertThat(summary.getShipping()).isEqualTo(1);
		assertThat(summary.getDelivered()).isZero();
		assertThat(summary.getBeforeRefundDeadline()).isEqualTo(1);
		SellerKitSummaryDto otherRace = kitDeliveryUseCase.summarize(SELLER_ID, RACE_ID + 1, LocalDateTime.now());
		assertThat(otherRace.getPreparing() + otherRace.getShipping() + otherRace.getBeforeRefundDeadline()).isZero();
	}

	@Test
	@DisplayName("건수를 셀 때 상품 서비스에서 찾지 못한 종목은 환불 기한 전 건수에서만 빼고 나머지는 센다")
	void summarizeWithMissingCourse() throws Exception {
		raceCoursePort.put(courseWithRefundDeadline(LocalDate.now().plusDays(10)));
		paidOrder();
		raceCoursePort.clear();

		SellerKitSummaryDto summary = kitDeliveryUseCase.summarize(SELLER_ID, null, LocalDateTime.now());

		assertThat(summary.getPreparing()).isEqualTo(1);
		assertThat(summary.getBeforeRefundDeadline()).isZero();
	}

	@Test
	@DisplayName("일정 변경 뒤 7일처럼 환불 기한이 지나도 환불할 수 있는 동안은 발송을 막는다 (취소와 같은 기준)")
	void shipWhileScheduleChangeRefund() throws Exception {
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null, LocalDate.now().minusDays(1),
			LocalDateTime.now().minusDays(1)));
		long itemId = itemIdOf(paidOrder());

		assertThatThrownBy(() -> ship(itemId))
			.isInstanceOf(KitNotShippableException.class)
			.hasMessageStartingWith("아직 환불할 수 있는 참가권이라");
	}

	@Test
	@DisplayName("보낸 뒤 취소된 참가권도 판매자 목록에 취소 상태로 남고, 배송 완료로 바꿀 수 있다")
	void canceledAfterShipping() throws Exception {
		raceCoursePort.put(courseWithRefundDeadline(LocalDate.now().minusDays(1)));
		long orderId = paidOrder();
		long itemId = itemIdOf(orderId);
		ship(itemId);
		// 일정이 바뀌어 7일 동안 다시 환불할 수 있게 되자 구매자가 취소했다
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null, LocalDate.now().minusDays(1),
			LocalDateTime.now()));
		mockMvc.perform(post("/api/v1/order/orders/" + orderId + "/items/" + itemId + "/cancel")
				.header("X-Member-Id", MEMBER_ID))
			.andExpect(status().isOk());

		assertThat(kitDeliveryUseCase.findKits(SELLER_ID, null, KitDeliveryStatus.SHIPPING, 0, 20).getKits())
			.extracting(SellerKitDto::getOrderItemId, SellerKitDto::getOrderItemStatus)
			.containsExactly(tuple(itemId, OrderItemStatus.CANCELED));
		assertThat(kitDeliveryUseCase.summarize(SELLER_ID, null, LocalDateTime.now()).getShipping()).isEqualTo(1);
		kitDeliveryUseCase.completeDelivery(SELLER_ID, itemId, LocalDateTime.now());
		assertThat(shipmentOf(orderId).getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
	}

	@Test
	@DisplayName("택배사·송장 번호가 비었거나 너무 길면 400으로 막고 배송 준비로 남긴다")
	void shipWithInvalidShippingInfo() throws Exception {
		raceCoursePort.put(courseWithRefundDeadline(LocalDate.now().minusDays(1)));
		long orderId = paidOrder();
		long itemId = itemIdOf(orderId);

		assertThatThrownBy(() -> kitDeliveryUseCase.ship(SELLER_ID, itemId, " ", TRACKING_NUMBER, LocalDateTime.now()))
			.isInstanceOf(KitShippingInfoInvalidException.class);
		assertThatThrownBy(() -> kitDeliveryUseCase.ship(SELLER_ID, itemId, CARRIER, null, LocalDateTime.now()))
			.isInstanceOf(KitShippingInfoInvalidException.class);
		assertThatThrownBy(() -> kitDeliveryUseCase.ship(SELLER_ID, itemId, CARRIER,
			"1".repeat(Shipment.MAX_TRACKING_NUMBER_LENGTH + 1), LocalDateTime.now()))
			.isInstanceOf(KitShippingInfoInvalidException.class);
		assertThat(shipmentOf(orderId).getStatus()).isEqualTo(ShipmentStatus.READY);

		// 종목을 묻기 전에 막으므로, 상품 서비스에 종목이 없어도 입력 오류로 알린다
		raceCoursePort.clear();
		assertThatThrownBy(() -> kitDeliveryUseCase.ship(SELLER_ID, itemId, "", TRACKING_NUMBER, LocalDateTime.now()))
			.isInstanceOf(KitShippingInfoInvalidException.class);
	}

	@Test
	@DisplayName("택배사·송장 번호의 앞뒤 공백은 빼고 저장한다")
	void shipStripsShippingInfo() throws Exception {
		raceCoursePort.put(courseWithRefundDeadline(LocalDate.now().minusDays(1)));
		long itemId = itemIdOf(paidOrder());

		OrderItemDto shipped = kitDeliveryUseCase.ship(SELLER_ID, itemId, " " + CARRIER + " ",
			" " + TRACKING_NUMBER + " ", LocalDateTime.now());

		assertThat(shipped.getKitCarrier()).isEqualTo(CARRIER);
		assertThat(shipped.getKitTrackingNumber()).isEqualTo(TRACKING_NUMBER);
	}

	@Test
	@DisplayName("환불 기한 안에는 발송 처리를 막는다 (정책: 키트는 환불 기한이 지난 뒤 배송)")
	void shipBeforeRefundDeadline() throws Exception {
		LocalDate refundDeadline = LocalDate.now();
		raceCoursePort.put(courseWithRefundDeadline(refundDeadline));
		long orderId = paidOrder();

		assertThatThrownBy(() -> ship(itemIdOf(orderId)))
			.isInstanceOf(KitNotShippableException.class)
			.hasMessage("아직 환불할 수 있는 참가권이라 발송할 수 없습니다(환불 기한: %s).".formatted(refundDeadline));
		assertThat(shipmentOf(orderId).getStatus()).isEqualTo(ShipmentStatus.READY);
	}

	@Test
	@DisplayName("다른 판매자의 참가권은 403, 없는 주문 상품은 404, 이미 발송한 키트는 409다")
	void shipRejected() throws Exception {
		raceCoursePort.put(courseWithRefundDeadline(LocalDate.now().minusDays(1)));
		long itemId = itemIdOf(paidOrder());

		assertThatThrownBy(() -> kitDeliveryUseCase.ship(OTHER_SELLER_ID, itemId, CARRIER, TRACKING_NUMBER,
			LocalDateTime.now()))
			.isInstanceOf(OrderAccessDeniedException.class);
		assertThatThrownBy(() -> ship(999_999))
			.isInstanceOf(OrderItemNotFoundException.class);

		ship(itemId);
		assertThatThrownBy(() -> ship(itemId))
			.isInstanceOf(KitNotShippableException.class)
			.hasMessage("이미 발송한 키트입니다.");
	}

	@Test
	@DisplayName("결제 전이거나 취소한 참가권의 키트는 발송하지 않는다")
	void shipUnpaidOrCanceled() throws Exception {
		raceCoursePort.put(courseWithRefundDeadline(LocalDate.now().plusDays(10)));
		long waitingOrderId = createOrder(MEMBER_ID);
		long canceledOrderId = paidOrder(MEMBER_ID + 1);
		long canceledItemId = itemIdOf(canceledOrderId);
		mockMvc.perform(post("/api/v1/order/orders/" + canceledOrderId + "/items/" + canceledItemId + "/cancel")
				.header("X-Member-Id", MEMBER_ID + 1))
			.andExpect(status().isOk());
		// 환불 기한이 지난 뒤에도 결제 상태를 따로 확인한다
		raceCoursePort.put(courseWithRefundDeadline(LocalDate.now().minusDays(1)));

		assertThatThrownBy(() -> ship(itemIdOf(waitingOrderId)))
			.isInstanceOf(KitNotShippableException.class)
			.hasMessage("결제한 참가권의 키트만 발송할 수 있습니다(현재 상태: 결제 대기).");
		assertThatThrownBy(() -> ship(canceledItemId))
			.isInstanceOf(KitNotShippableException.class)
			.hasMessage("결제한 참가권의 키트만 발송할 수 있습니다(현재 상태: 취소).");
	}

	@Test
	@DisplayName("키트가 없는 종목은 배송 상태가 없고 발송 처리할 수 없다")
	void courseWithoutKit() throws Exception {
		raceCoursePort.put(StubRaceCoursePort.courseWithoutKit(COURSE_ID, RACE_ID));
		long orderId = paidOrder();

		findOrder(orderId).andExpect(jsonPath("$.data.items[0].kitDeliveryStatus").isEmpty());
		assertThatThrownBy(() -> ship(itemIdOf(orderId)))
			.isInstanceOf(KitNotShippableException.class)
			.hasMessage("키트가 없는 참가권입니다.");
	}

	@Test
	@DisplayName("결제하면 키트 배송 1건이 발송 대기로 생기고, 받는 분·주소는 참가 신청의 값이 복사된다 (BUK-6)")
	void payCreatesReadyShipment() throws Exception {
		raceCoursePort.put(courseWithRefundDeadline(LocalDate.now().plusDays(10)));
		long unpaidOrderId = createOrder(MEMBER_ID + 1);
		long orderId = paidOrder();

		Shipment shipment = shipmentOf(orderId);
		assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.READY);
		assertThat(shipment.getItems()).singleElement()
			.satisfies(item -> {
				assertThat(item.getOrderItemId()).isEqualTo(itemIdOf(orderId));
				assertThat(item.getQuantity()).isEqualTo(1);
			});
		assertThat(shipment.getShippingAddress().getRecipientName()).isEqualTo("김러너");
		assertThat(shipment.getShippingAddress().getZipcode()).isEqualTo("04700");
		assertThat(shipment.getShippingAddress().getAddress()).isEqualTo("서울 성동구 왕십리로 00");
		assertThat(shipment.getShippingAddress().getAddressDetail()).isEqualTo("101동 1001호");
		assertThat(shipment.getDeliveryMemo()).isNull();
		// 결제 전 주문에는 배송이 없다
		assertThat(shipmentsOf(unpaidOrderId)).isEmpty();
	}

	@Test
	@DisplayName("같은 결제 요청이 두 번 와도 배송은 1건이다")
	void payTwiceCreatesOneShipment() throws Exception {
		raceCoursePort.put(courseWithRefundDeadline(LocalDate.now().plusDays(10)));
		long orderId = paidOrder();

		OrderPayResult again = transactionTemplate.execute(status ->
			orderPayUseCase.pay(MEMBER_ID, orderId, LocalDateTime.now()));

		assertThat(again.alreadyPaid()).isTrue();
		assertThat(shipmentsOf(orderId)).hasSize(1);
		assertThat(jdbcTemplate.queryForObject("select count(*) from order_shipment_item", Long.class)).isEqualTo(1);
	}

	@Test
	@DisplayName("키트가 없는 종목은 결제해도 배송이 생기지 않는다")
	void payWithoutKitCreatesNoShipment() throws Exception {
		raceCoursePort.put(StubRaceCoursePort.courseWithoutKit(COURSE_ID, RACE_ID));

		assertThat(shipmentsOf(paidOrder())).isEmpty();
	}

	@Test
	@DisplayName("발송 전에 참가권을 취소하면 배송도 취소되어 판매자 목록·건수에서 빠지고, 구매자 응답은 그대로다")
	void cancelBeforeShippingCancelsShipment() throws Exception {
		raceCoursePort.put(courseWithRefundDeadline(LocalDate.now().plusDays(10)));
		long orderId = paidOrder();
		long itemId = itemIdOf(orderId);

		mockMvc.perform(post("/api/v1/order/orders/" + orderId + "/items/" + itemId + "/cancel")
				.header("X-Member-Id", MEMBER_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[0].kitDeliveryStatus").value("PREPARING"))
			.andExpect(jsonPath("$.data.shippingAddress.address").value("서울 성동구 왕십리로 00"));

		Shipment shipment = shipmentOf(orderId);
		assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.CANCELED);
		assertThat(shipment.getCanceledAt()).isNotNull();
		assertThat(kitDeliveryUseCase.findKits(SELLER_ID, null, null, 0, 20).getKits()).isEmpty();
		assertThat(kitDeliveryUseCase.summarize(SELLER_ID, null, LocalDateTime.now()).getPreparing()).isZero();
	}

	@Test
	@DisplayName("대회가 취소되면 발송 전 배송만 취소되고, 이미 보낸 배송은 배송 중으로 남는다")
	void raceCancelCancelsOnlyReadyShipments() throws Exception {
		raceCoursePort.put(courseWithRefundDeadline(LocalDate.now().minusDays(1)));
		long shippedOrderId = paidOrder(MEMBER_ID);
		long readyOrderId = paidOrder(MEMBER_ID + 1);
		ship(itemIdOf(shippedOrderId));

		raceCancelRefundUseCase.refundAll(RACE_ID, LocalDateTime.now());

		assertThat(shipmentOf(readyOrderId).getStatus()).isEqualTo(ShipmentStatus.CANCELED);
		assertThat(shipmentOf(shippedOrderId).getStatus()).isEqualTo(ShipmentStatus.SHIPPING);
		assertThat(kitDeliveryUseCase.findKits(SELLER_ID, null, null, 0, 20).getKits())
			.extracting(SellerKitDto::getOrderItemId, SellerKitDto::getOrderItemStatus)
			.containsExactly(tuple(itemIdOf(shippedOrderId), OrderItemStatus.CANCELED));
	}

	@Test
	@DisplayName("참가자 정보에서 주소를 바꾸면 발송 대기 배송은 따라 바뀌고, 이미 보낸 배송의 주소는 그대로다")
	void changeAddressAfterShipping() throws Exception {
		raceCoursePort.put(courseWithRefundDeadline(LocalDate.now().minusDays(1)));
		long readyOrderId = paidOrder(MEMBER_ID);
		long shippedOrderId = paidOrder(MEMBER_ID + 1);
		ship(itemIdOf(shippedOrderId));

		changeAddress(MEMBER_ID, participationIdOf(readyOrderId)).andExpect(status().isOk());
		changeAddress(MEMBER_ID + 1, participationIdOf(shippedOrderId)).andExpect(status().isOk());

		assertThat(shipmentOf(readyOrderId).getShippingAddress().getAddress()).isEqualTo("부산 해운대구 해운대로 00");
		assertThat(shipmentOf(readyOrderId).getShippingAddress().getRecipientName()).isEqualTo("김가족");
		assertThat(shipmentOf(shippedOrderId).getShippingAddress().getAddress()).isEqualTo("서울 성동구 왕십리로 00");
		assertThat(shipmentOf(shippedOrderId).getShippingAddress().getRecipientName()).isEqualTo("김러너");
		assertThat(kitDeliveryUseCase.findKits(SELLER_ID, null, KitDeliveryStatus.SHIPPING, 0, 20).getKits())
			.singleElement()
			.satisfies(row -> assertThat(row.getShippingAddress().getAddress()).isEqualTo("서울 성동구 왕십리로 00"));
	}

	@Test
	@DisplayName("옛 키트 칸을 배송 표로 옮기는 SQL은 상태·송장과 참가 신청의 최신 주소를 옮기고, 다시 돌려도 두 번 만들지 않는다")
	void migrationSql() throws Exception {
		raceCoursePort.put(courseWithRefundDeadline(LocalDate.now().plusDays(10)));
		long readyOrderId = paidOrder(MEMBER_ID);
		long shippingOrderId = paidOrder(MEMBER_ID + 1);
		long deliveredOrderId = paidOrder(MEMBER_ID + 2);
		long canceledOrderId = paidOrder(MEMBER_ID + 3);
		mockMvc.perform(post("/api/v1/order/orders/" + canceledOrderId + "/items/" + itemIdOf(canceledOrderId)
				+ "/cancel").header("X-Member-Id", MEMBER_ID + 3))
			.andExpect(status().isOk());
		long unpaidOrderId = createOrder(MEMBER_ID + 4);
		// 배송 표 전처럼 만든다: 새 배송을 지우고, 옛 칸(엔티티에서 뺀 컬럼)을 다시 붙여 값을 넣는다
		jdbcTemplate.update("delete from order_shipment_item");
		jdbcTemplate.update("delete from order_shipment");
		addLegacyColumns();
		jdbcTemplate.update("update order_order set recipient_name = '김러너', recipient_phone = '010-1234-5678',"
			+ " zipcode = '04700', address = '서울 성동구 왕십리로 00', address_detail = '101동 1001호'");
		// 배포 뒤 SQL 전에 구매자가 주소를 고쳤다. 새 코드는 참가 신청만 고치므로 주문의 옛 칸은 옛 주소 그대로다
		changeAddress(MEMBER_ID, participationIdOf(readyOrderId)).andExpect(status().isOk());
		jdbcTemplate.update("update order_order_item set kit_delivery_status = 'PREPARING'");
		LocalDateTime shippedAt = LocalDateTime.now().minusDays(2).truncatedTo(ChronoUnit.MICROS);
		jdbcTemplate.update("update order_order_item set kit_delivery_status = 'SHIPPING', kit_carrier = ?,"
			+ " kit_tracking_number = ?, kit_shipped_at = ? where order_id = ?",
			CARRIER, TRACKING_NUMBER, shippedAt, shippingOrderId);
		jdbcTemplate.update("update order_order_item set kit_delivery_status = 'DELIVERED', kit_carrier = ?,"
			+ " kit_tracking_number = ?, kit_shipped_at = ?, kit_delivered_at = ? where order_id = ?",
			CARRIER, TRACKING_NUMBER, shippedAt, shippedAt.plusDays(1), deliveredOrderId);

		runSql(migrationSqlText());
		runSql(migrationSqlText());

		assertThat(shipmentsOf(readyOrderId)).singleElement()
			.satisfies(shipment -> {
				assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.READY);
				assertThat(shipment.getShippingAddress().getRecipientName()).isEqualTo("김가족");
				assertThat(shipment.getShippingAddress().getAddress()).isEqualTo("부산 해운대구 해운대로 00");
				assertThat(shipment.getShippingAddress().getAddressDetail()).isNull();
				assertThat(shipment.getItems()).singleElement()
					.satisfies(item -> assertThat(item.getOrderItemId()).isEqualTo(itemIdOf(readyOrderId)));
			});
		assertThat(shipmentsOf(shippingOrderId)).singleElement()
			.satisfies(shipment -> {
				assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.SHIPPING);
				assertThat(shipment.getCarrier()).isEqualTo(CARRIER);
				assertThat(shipment.getTrackingNumber()).isEqualTo(TRACKING_NUMBER);
				assertThat(shipment.getShippedAt()).isEqualTo(shippedAt);
				assertThat(shipment.getShippingAddress().getAddressDetail()).isEqualTo("101동 1001호");
			});
		assertThat(shipmentOf(deliveredOrderId).getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
		assertThat(shipmentOf(deliveredOrderId).getDeliveredAt()).isEqualTo(shippedAt.plusDays(1));
		assertThat(shipmentOf(canceledOrderId).getStatus()).isEqualTo(ShipmentStatus.CANCELED);
		assertThat(shipmentOf(canceledOrderId).getCanceledAt()).isNotNull();
		assertThat(shipmentsOf(unpaidOrderId)).isEmpty();
		assertThat(jdbcTemplate.queryForObject("select count(*) from order_shipment_item", Long.class)).isEqualTo(4);
		// 옮긴 배송으로 판매자 화면이 배송 표 전과 같이 보인다
		assertThat(kitDeliveryUseCase.findKits(SELLER_ID, null, null, 0, 20).getKits())
			.extracting(SellerKitDto::getOrderItemId, SellerKitDto::getKitDeliveryStatus)
			.containsExactly(tuple(itemIdOf(deliveredOrderId), KitDeliveryStatus.DELIVERED),
				tuple(itemIdOf(shippingOrderId), KitDeliveryStatus.SHIPPING),
				tuple(itemIdOf(readyOrderId), KitDeliveryStatus.PREPARING));
	}

	@Test
	@DisplayName("되돌리는 SQL은 배송 표 값을 옛 키트 칸·주문 배송지로 다시 쓰고, 다시 돌려도 결과가 같다")
	void rollbackSql() throws Exception {
		raceCoursePort.put(courseWithRefundDeadline(LocalDate.now().minusDays(1)));
		raceCoursePort.put(StubRaceCoursePort.openCourse(OPEN_COURSE_ID, RACE_ID, 14, null,
			LocalDate.now().plusDays(10)));
		long shippedOrderId = paidOrder(MEMBER_ID);
		LocalDateTime shippedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
		kitDeliveryUseCase.ship(SELLER_ID, itemIdOf(shippedOrderId), CARRIER, TRACKING_NUMBER, shippedAt);
		// 보낸 뒤 신청서 주소를 고쳤다. 주문 칸에는 신청서가 아니라 실제로 보낸 배송의 주소가 가야 한다
		changeAddress(MEMBER_ID, participationIdOf(shippedOrderId)).andExpect(status().isOk());
		long readyOrderId = paidOrder(MEMBER_ID + 1);
		changeAddress(MEMBER_ID + 1, participationIdOf(readyOrderId)).andExpect(status().isOk());
		long canceledOrderId = paidOrder(MEMBER_ID + 2, OPEN_COURSE_ID);
		mockMvc.perform(post("/api/v1/order/orders/" + canceledOrderId + "/items/" + itemIdOf(canceledOrderId)
				+ "/cancel").header("X-Member-Id", MEMBER_ID + 2))
			.andExpect(status().isOk());
		long unpaidOrderId = createOrder(MEMBER_ID + 3);
		addLegacyColumns();

		String rollback = rollbackSqlText();
		runSql(rollback);
		runSql(rollback);

		assertThat(legacyKitOf(shippedOrderId))
			.containsEntry("kit_delivery_status", "SHIPPING")
			.containsEntry("kit_carrier", CARRIER)
			.containsEntry("kit_tracking_number", TRACKING_NUMBER)
			.containsEntry("kit_shipped_at", Timestamp.valueOf(shippedAt));
		assertThat(legacyKitOf(readyOrderId)).containsEntry("kit_delivery_status", "PREPARING");
		assertThat(legacyKitOf(canceledOrderId)).containsEntry("kit_delivery_status", "PREPARING");
		assertThat(legacyKitOf(unpaidOrderId)).containsEntry("kit_delivery_status", "PREPARING");
		// 발송 대기 배송은 고친 주소를, 결제 전 주문은 참가 신청의 주소를 주문 칸에 둔다
		assertThat(legacyAddressOf(readyOrderId)).containsEntry("address", "부산 해운대구 해운대로 00")
			.containsEntry("recipient_name", "김가족");
		assertThat(legacyAddressOf(shippedOrderId)).containsEntry("address", "서울 성동구 왕십리로 00")
			.containsEntry("address_detail", "101동 1001호");
		assertThat(legacyAddressOf(unpaidOrderId)).containsEntry("address", "서울 성동구 왕십리로 00");
		// 파일의 되돌리기 확인 쿼리도 0이다
		Matcher checks = Pattern.compile("(SELECT COUNT\\(\\*\\) AS rollback_\\w+.*?);", Pattern.DOTALL)
			.matcher(rollback);
		int checked = 0;
		while (checks.find()) {
			assertThat(jdbcTemplate.queryForObject(checks.group(1), Long.class)).isZero();
			checked++;
		}
		assertThat(checked).isEqualTo(2);
	}

	// 배송 표 전의 옛 칸. 엔티티에서 빼서 테스트 DB에는 없으므로 다시 붙인다
	private void addLegacyColumns() {
		jdbcTemplate.execute("""
			alter table order_order_item add column if not exists kit_delivery_status varchar(20);
			alter table order_order_item add column if not exists kit_carrier varchar(30);
			alter table order_order_item add column if not exists kit_tracking_number varchar(50);
			alter table order_order_item add column if not exists kit_shipped_at timestamp(6);
			alter table order_order_item add column if not exists kit_delivered_at timestamp(6);
			alter table order_order add column if not exists recipient_name varchar(50);
			alter table order_order add column if not exists recipient_phone varchar(20);
			alter table order_order add column if not exists zipcode varchar(10);
			alter table order_order add column if not exists address varchar(255);
			alter table order_order add column if not exists address_detail varchar(255)
			""");
	}

	// SQL은 운영의 참가 신청 표 이름(order_participation, #237)을 쓴다. 테스트는 엔티티의 지금 표 이름으로 바꿔 돌린다
	private String migrationSqlText() throws Exception {
		String participationTable = Participation.class.getAnnotation(Table.class).name().toLowerCase();
		return Files.readString(Path.of("../../docs/erd/order-shipment-migration-BUK-6.sql"))
			.replace("order_participation", participationTable);
	}

	// 파일에 주석으로 둔 되돌리기 부분만 꺼내 주석을 벗긴다
	private String rollbackSqlText() throws Exception {
		String text = migrationSqlText();
		String block = text.substring(text.indexOf("-- [되돌리기 시작]"), text.indexOf("-- [되돌리기 끝]"));
		return block.lines()
			.skip(1)
			.map(line -> line.startsWith("-- ") ? line.substring(3) : line.replaceFirst("^--", ""))
			.collect(Collectors.joining("\n"));
	}

	// H2는 START TRANSACTION을 모른다. 트랜잭션 묶음은 MySQL에서 확인하고, 테스트는 그 두 줄만 빼고 돌린다
	private void runSql(String sql) {
		String withoutTransaction = sql.replace("START TRANSACTION;", "").replace("COMMIT;", "");
		new ResourceDatabasePopulator(new ByteArrayResource(withoutTransaction.getBytes(StandardCharsets.UTF_8)))
			.execute(jdbcTemplate.getDataSource());
	}

	private Map<String, Object> legacyKitOf(long orderId) {
		return lowerKeys(jdbcTemplate.queryForMap("select kit_delivery_status, kit_carrier, kit_tracking_number,"
			+ " kit_shipped_at from order_order_item where order_id = ?", orderId));
	}

	private Map<String, Object> legacyAddressOf(long orderId) {
		return lowerKeys(jdbcTemplate.queryForMap(
			"select recipient_name, address, address_detail from order_order where id = ?", orderId));
	}

	private static Map<String, Object> lowerKeys(Map<String, Object> row) {
		Map<String, Object> lowered = new HashMap<>();
		row.forEach((key, value) -> lowered.put(key.toLowerCase(), value));
		return lowered;
	}

	private ResultActions changeAddress(long memberId, long participationId) throws Exception {
		return mockMvc.perform(put("/api/v1/order/participations/" + participationId + "/participant")
			.header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{ "name": "김러너", "birth": "1994-06-25", "gender": "MALE", "phone": "010-1234-5678",
					"emergencyContactName": "김가족", "emergencyContactPhone": "010-9876-5432", "tshirtSize": "L",
					"shippingAddress": { "recipientName": "김가족", "recipientPhone": "010-9876-5432",
						"zipcode": "48094", "address": "부산 해운대구 해운대로 00" } }
				"""));
	}

	private RaceCourseInfo courseWithRefundDeadline(LocalDate refundDeadline) {
		return StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null, refundDeadline);
	}

	private OrderItemDto ship(long orderItemId) {
		return kitDeliveryUseCase.ship(SELLER_ID, orderItemId, CARRIER, TRACKING_NUMBER, LocalDateTime.now());
	}

	private ResultActions findOrder(long orderId) throws Exception {
		return mockMvc.perform(get("/api/v1/order/orders/" + orderId).header("X-Member-Id", MEMBER_ID))
			.andExpect(status().isOk());
	}

	private long paidOrder() throws Exception {
		return paidOrder(MEMBER_ID);
	}

	private long paidOrder(long memberId) throws Exception {
		return paidOrder(memberId, COURSE_ID);
	}

	private long paidOrder(long memberId, long courseId) throws Exception {
		long orderId = createOrder(memberId, courseId);
		transactionTemplate.execute(status -> orderPayUseCase.pay(memberId, orderId, LocalDateTime.now()));
		return orderId;
	}

	private long createOrder(long memberId) throws Exception {
		return createOrder(memberId, COURSE_ID);
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

	private OrderItem itemOf(long orderId) {
		return transactionTemplate.execute(status -> {
			Order order = orderRepository.findById(orderId).orElseThrow();
			return order.getItems().getFirst();
		});
	}

	private Shipment shipmentOf(long orderId) {
		return shipmentsOf(orderId).getFirst();
	}

	// 배송과 상자 속 주문 상품을 트랜잭션 안에서 읽어 둔다
	private List<Shipment> shipmentsOf(long orderId) {
		return transactionTemplate.execute(status -> {
			List<Shipment> shipments = orderRepository.findById(orderId).orElseThrow().getShipments();
			shipments.forEach(shipment -> shipment.getItems().size());
			return List.copyOf(shipments);
		});
	}

	private long participationIdOf(long orderId) {
		return itemOf(orderId).getParticipationId();
	}

	private long itemIdOf(long orderId) {
		return itemOf(orderId).getId();
	}
}
