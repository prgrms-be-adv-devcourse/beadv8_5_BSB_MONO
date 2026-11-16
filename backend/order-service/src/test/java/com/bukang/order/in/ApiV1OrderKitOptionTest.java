package com.bukang.order.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
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
import com.bukang.order.app.OrderExpiryUseCase;
import com.bukang.order.app.OrderPayUseCase;
import com.bukang.order.app.RaceCancelRefundUseCase;
import com.bukang.order.domain.KitOptionSale;
import com.bukang.order.domain.ParticipationKitOption;
import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.domain.exception.OrderNotPayableException;
import com.bukang.order.out.KitOptionSaleRepository;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.ParticipationKitOptionRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.out.QueueAdmissionRepository;
import com.bukang.order.out.ShipmentRepository;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.support.QueueEntry;
import com.bukang.order.support.StubKitOptionPort;
import com.bukang.order.support.StubKitOptionPortConfig;
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

// 키트 옵션: 결제 때 고르고 판매 수를 세어 품절을 막는다, 취소하면 판매 수를 되돌린다 (11/07·11/09 결정)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubSeatHoldPortConfig.class, StubWalletPortConfig.class,
	StubRefundEligibilityPortConfig.class, StubKitOptionPortConfig.class, StubSellerLookupPortConfig.class})
class ApiV1OrderKitOptionTest {
	private static final String BASE_URL = "/api/v1/order/orders";
	private static final long COURSE_ID = 10;
	private static final long NO_OPTION_COURSE_ID = 20;
	private static final long RACE_ID = 100;
	private static final long NO_OPTION_RACE_ID = 200;
	private static final long SHIRT_M = 11;
	private static final long SHIRT_L = 12;
	private static final long SOCKS_FREE = 21;
	private static final long SELLER_MEMBER_ID = 900;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private ParticipationRepository participationRepository;

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
	private StubSellerLookupPort sellerLookupPort;

	@Autowired
	private OrderCancelUseCase orderCancelUseCase;

	@Autowired
	private OrderExpiryUseCase orderExpiryUseCase;

	@Autowired
	private OrderPayUseCase orderPayUseCase;

	@Autowired
	private RaceCancelRefundUseCase raceCancelRefundUseCase;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@BeforeEach
	void setUp() {
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null));
		raceCoursePort.put(StubRaceCoursePort.openCourse(NO_OPTION_COURSE_ID, NO_OPTION_RACE_ID, 14, null));
		kitOptionPort.add(RACE_ID, SHIRT_M, "T-shirt", "M", 30);
		kitOptionPort.add(RACE_ID, SHIRT_L, "T-shirt", "L", 1);
		// 맞춤 제작(준비 수량 없음)이라 품절이 없다
		kitOptionPort.add(RACE_ID, SOCKS_FREE, "socks", "FREE", null);
	}

	@AfterEach
	void tearDown() {
		shipmentRepository.deleteAll();
		orderRepository.deleteAll();
		participationRepository.deleteAll();
		queueAdmissionRepository.deleteAll();
		participationKitOptionRepository.deleteAll();
		kitOptionSaleRepository.deleteAll();
		raceCoursePort.clear();
		seatHoldPort.clear();
		walletPort.clear();
		kitOptionPort.clear();
		sellerLookupPort.clear();
	}

	@Test
	@DisplayName("옵션 이름마다 하나씩 골라 결제하면 판매 수가 1씩 늘고, 고른 옵션의 이름·값 사본이 남는다")
	void payWithKitOptions() throws Exception {
		long orderId = orderAndGetId(1, COURSE_ID);

		pay(1, orderId, SHIRT_M, SOCKS_FREE).andExpect(status().isOk());

		assertThat(sold(SHIRT_M)).isEqualTo(1);
		assertThat(sold(SOCKS_FREE)).isEqualTo(1);
		assertThat(participationKitOptionRepository.findAll())
			.extracting(ParticipationKitOption::getKitOptionId, ParticipationKitOption::getOptionName,
				ParticipationKitOption::getOptionValue)
			.containsExactlyInAnyOrder(
				tuple(SHIRT_M, "T-shirt", "M"),
				tuple(SOCKS_FREE, "socks", "FREE"));
	}

	@Test
	@DisplayName("품절된 옵션이면 예치금을 건드리지 않고 409이며, 참가 신청은 결제 대기로 남아 다른 옵션으로 결제된다")
	void soldOut() throws Exception {
		long first = orderAndGetId(1, COURSE_ID);
		pay(1, first, SHIRT_L, SOCKS_FREE).andExpect(status().isOk());
		long second = orderAndGetId(2, COURSE_ID);

		pay(2, second, SHIRT_L, SOCKS_FREE)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_KIT_OPTION_SOLD_OUT"))
			.andExpect(jsonPath("$.message").value("선택하신 T-shirt L은(는) 준비된 수량이 모두 팔렸어요. 다른 옵션을 골라 주세요."))
			.andExpect(jsonPath("$.data.soldOut.length()").value(1))
			.andExpect(jsonPath("$.data.soldOut[0].kitOptionId").value(SHIRT_L))
			.andExpect(jsonPath("$.data.soldOut[0].name").value("T-shirt"))
			.andExpect(jsonPath("$.data.soldOut[0].value").value("L"));

		assertThat(walletPort.payCalls()).hasSize(1);
		assertThat(participationStatusOf(second)).isEqualTo(ParticipationStatus.APPLIED);
		assertThat(sold(SHIRT_L)).isEqualTo(1);
		// 롤백돼 맞춤 제작 옵션의 +1도 되돌아갔다
		assertThat(sold(SOCKS_FREE)).isEqualTo(1);

		pay(2, second, SHIRT_M, SOCKS_FREE).andExpect(status().isOk());
	}

	@RepeatedTest(5)
	@DisplayName("마지막 한 장에 두 결제가 동시에 오면 한 건만 결제되고 다른 한 건은 품절 409다")
	void lastOneConcurrently() throws Exception {
		long first = orderAndGetId(1, COURSE_ID);
		long second = orderAndGetId(2, COURSE_ID);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (long[] memberAndOrder : new long[][] {{1, first}, {2, second}}) {
				results.add(executor.submit(() -> {
					start.await();
					return pay(memberAndOrder[0], memberAndOrder[1], SHIRT_L, SOCKS_FREE)
						.andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> result : results) {
				statuses.add(result.get());
			}
			assertThat(statuses).containsExactlyInAnyOrder(200, 409);
		} finally {
			executor.shutdownNow();
		}
		assertThat(sold(SHIRT_L)).isEqualTo(1);
		assertThat(sold(SOCKS_FREE)).isEqualTo(1);
		assertThat(walletPort.payCalls()).hasSize(1);
	}

	@Test
	@DisplayName("예치금이 부족해 결제가 실패하면 늘린 판매 수도 함께 되돌아가고 사본도 남지 않는다")
	void insufficientBalanceRollsBackSold() throws Exception {
		long orderId = orderAndGetId(1, COURSE_ID);
		walletPort.setBalance(StubRaceCoursePort.PRICE - 1);

		pay(1, orderId, SHIRT_L, SOCKS_FREE).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_INSUFFICIENT_BALANCE"));

		assertThat(sold(SHIRT_L)).isZero();
		assertThat(sold(SOCKS_FREE)).isZero();
		assertThat(participationKitOptionRepository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("자리 판매가 기한 지남으로 거절되면(예치금 환원) 늘린 판매 수도 되돌아간다")
	void sellExpiredRollsBackSold() throws Exception {
		long orderId = orderAndGetId(1, COURSE_ID);
		seatHoldPort.expire(participationIdOf(orderId));

		pay(1, orderId, SHIRT_L, SOCKS_FREE).andExpect(status().isConflict());

		assertThat(sold(SHIRT_L)).isZero();
	}

	@Test
	@DisplayName("참가권을 취소하면 판매 수가 1 줄고, 같은 취소가 다시 와도(재처리 겹침) 한 번만 줄어든다")
	void cancelReleasesOnce() throws Exception {
		long orderId = orderAndGetId(1, COURSE_ID);
		pay(1, orderId, SHIRT_L, SOCKS_FREE).andExpect(status().isOk());
		long orderItemId = orderItemIdOf(orderId);

		cancel(1, orderId, orderItemId).andExpect(status().isOk());
		cancel(1, orderId, orderItemId).andExpect(status().isConflict());
		orderCancelUseCase.completeCancel(orderId, orderItemId, LocalDateTime.now());

		assertThat(sold(SHIRT_L)).isZero();
		assertThat(sold(SOCKS_FREE)).isZero();
		// 풀린 한 장을 다른 사람이 산다
		long other = orderAndGetId(2, COURSE_ID);
		pay(2, other, SHIRT_L, SOCKS_FREE).andExpect(status().isOk());
	}

	@Test
	@DisplayName("대회 취소로 환불하면 판매 수가 1 줄고, 환불을 다시 돌려도 더 줄지 않는다")
	void raceCancelRefundReleasesOnce() throws Exception {
		long orderId = orderAndGetId(1, COURSE_ID);
		pay(1, orderId, SHIRT_L, SOCKS_FREE).andExpect(status().isOk());
		long otherOrder = orderAndGetId(2, COURSE_ID);
		pay(2, otherOrder, SHIRT_M, SOCKS_FREE).andExpect(status().isOk());

		raceCancelRefundUseCase.refundAll(RACE_ID, LocalDateTime.now());
		raceCancelRefundUseCase.refundAll(RACE_ID, LocalDateTime.now());

		assertThat(sold(SHIRT_L)).isZero();
		assertThat(sold(SHIRT_M)).isZero();
		assertThat(sold(SOCKS_FREE)).isZero();
	}

	@Test
	@DisplayName("옵션이 없는 대회에 옵션을 보내면 400이고, 보내지 않으면 결제된다")
	void optionsForRaceWithoutOptions() throws Exception {
		long orderId = orderAndGetId(1, NO_OPTION_COURSE_ID);

		pay(1, orderId, SHIRT_M)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ORDER_KIT_OPTION_INVALID"))
			.andExpect(jsonPath("$.message").value("이 대회의 키트 옵션이 아닙니다. (옵션 ID: 11)"));
		assertThat(walletPort.payCalls()).isEmpty();

		pay(1, orderId).andExpect(status().isOk());
	}

	@Test
	@DisplayName("고른 옵션 여럿이 한꺼번에 다 팔렸으면 409 하나에 모두 담는다")
	void severalSoldOut() throws Exception {
		kitOptionPort.clear();
		kitOptionPort.add(RACE_ID, SHIRT_L, "T-shirt", "L", 1);
		kitOptionPort.add(RACE_ID, 31, "socks", "M", 1);
		long first = orderAndGetId(1, COURSE_ID);
		pay(1, first, SHIRT_L, 31).andExpect(status().isOk());
		long second = orderAndGetId(2, COURSE_ID);

		pay(2, second, SHIRT_L, 31)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value(
				"선택하신 T-shirt L, socks M은(는) 준비된 수량이 모두 팔렸어요. 다른 옵션을 골라 주세요."))
			.andExpect(jsonPath("$.data.soldOut.length()").value(2))
			.andExpect(jsonPath("$.data.soldOut[1].kitOptionId").value(31));
	}

	@Test
	@DisplayName("옵션 이름을 빠뜨리거나, 한 이름에서 둘을 고르거나, 다른 대회 옵션·같은 ID를 보내면 400이다")
	void invalidChoices() throws Exception {
		long orderId = orderAndGetId(1, COURSE_ID);

		pay(1, orderId, SHIRT_M)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("키트 옵션을 모두 골라 주세요. (고르지 않은 옵션: socks)"));
		pay(1, orderId)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("키트 옵션을 모두 골라 주세요. (고르지 않은 옵션: T-shirt, socks)"));
		pay(1, orderId, SHIRT_M, SHIRT_L, SOCKS_FREE)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("옵션 이름마다 하나만 고를 수 있습니다. (옵션: T-shirt)"));
		pay(1, orderId, SHIRT_M, 999, SOCKS_FREE)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("이 대회의 키트 옵션이 아닙니다. (옵션 ID: 999)"));
		pay(1, orderId, SHIRT_M, SHIRT_M, SOCKS_FREE)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ORDER_KIT_OPTION_INVALID"));

		assertThat(walletPort.payCalls()).isEmpty();
		assertThat(kitOptionSaleRepository.findAll()).allMatch(sale -> sale.getSold() == 0);
	}

	@Test
	@DisplayName("판매자는 옵션별 준비 수량·판매 수·남은 수량을 보고, 키트 목록에서 구매자가 고른 옵션을 본다")
	void sellerViews() throws Exception {
		sellerLookupPort.approve(SELLER_MEMBER_ID, StubRaceCoursePort.SELLER_ID);
		long orderId = orderAndGetId(1, COURSE_ID);
		pay(1, orderId, SHIRT_L, SOCKS_FREE).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/order/seller/kits/options").param("raceId", String.valueOf(RACE_ID))
				.header("X-Member-Id", SELLER_MEMBER_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(3))
			.andExpect(jsonPath("$.data[1].kitOptionId").value(SHIRT_L))
			.andExpect(jsonPath("$.data[1].stock").value(1))
			.andExpect(jsonPath("$.data[1].sold").value(1))
			.andExpect(jsonPath("$.data[1].remaining").value(0))
			.andExpect(jsonPath("$.data[0].sold").value(0))
			.andExpect(jsonPath("$.data[0].remaining").value(30))
			.andExpect(jsonPath("$.data[2].stock").isEmpty())
			.andExpect(jsonPath("$.data[2].remaining").isEmpty());

		mockMvc.perform(get("/api/v1/order/seller/kits").header("X-Member-Id", SELLER_MEMBER_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.kits[0].tshirtSize").value("L"))
			.andExpect(jsonPath("$.data.kits[0].kitOptions[0].name").value("T-shirt"))
			.andExpect(jsonPath("$.data.kits[0].kitOptions[0].value").value("L"))
			.andExpect(jsonPath("$.data.kits[0].kitOptions[1].name").value("socks"));
	}

	@Test
	@DisplayName("이 대회에 다른 판매자의 주문이 있으면 옵션별 판매 현황은 403이다")
	void sellerViewsOtherRace() throws Exception {
		sellerLookupPort.approve(SELLER_MEMBER_ID + 1, StubRaceCoursePort.SELLER_ID + 1);
		long orderId = orderAndGetId(1, COURSE_ID);
		pay(1, orderId, SHIRT_L, SOCKS_FREE).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/order/seller/kits/options").param("raceId", String.valueOf(RACE_ID))
				.header("X-Member-Id", SELLER_MEMBER_ID + 1))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORDER_SELLER_FORBIDDEN"));
	}

	@Test
	@DisplayName("만료된 주문은 상품·옵션을 묻기 전에 409(기한 지남)이고, 빈 판매 수 줄도 만들지 않는다")
	void expiredOrderRejectedBeforeOptions() throws Exception {
		long orderId = orderAndGetId(1, COURSE_ID);
		orderExpiryUseCase.expireOverdue(LocalDateTime.now().plusHours(1));

		pay(1, orderId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PAYMENT_DEADLINE_PASSED"));
		pay(1, orderId, SHIRT_L, SOCKS_FREE)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PAYMENT_DEADLINE_PASSED"));

		assertThat(kitOptionSaleRepository.count()).isZero();
	}

	@Test
	@DisplayName("기한이 지났지만 아직 만료 처리 전인 주문도 상품을 묻기 전에 409이고, 빈 판매 수 줄을 만들지 않는다")
	void pastDueOrderRejectedBeforeOptions() throws Exception {
		long orderId = orderAndGetId(1, COURSE_ID);

		assertThatThrownBy(() -> orderPayUseCase.pay(1, orderId, List.of(SHIRT_L, SOCKS_FREE),
			LocalDateTime.now().plusHours(1)))
			.isInstanceOf(OrderNotPayableException.class)
			.extracting("code").isEqualTo("ORDER_PAYMENT_DEADLINE_PASSED");
		assertThat(kitOptionSaleRepository.count()).isZero();
	}

	@Test
	@DisplayName("개인 결제는 종목을 묻지 않고 주문 상품의 대회 ID로 옵션만 묻는다")
	void individualPayDoesNotAskCourse() throws Exception {
		long orderId = orderAndGetId(1, COURSE_ID);
		// 종목 조회가 불리면 404가 된다
		raceCoursePort.clear();

		pay(1, orderId, SHIRT_L, SOCKS_FREE).andExpect(status().isOk());
	}

	@Test
	@DisplayName("상품이 취소되지 않은 대회의 옵션 목록에 404를 주면(경로·주소 어긋남) 옵션 없음으로 넘기지 않고 503이다")
	void optionsNotFoundForOpenRace() throws Exception {
		long orderId = orderAndGetId(1, COURSE_ID);
		kitOptionPort.markNotFound(RACE_ID);

		pay(1, orderId)
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("ORDER_EXTERNAL_SERVICE_UNAVAILABLE"));
		assertThat(walletPort.payCalls()).isEmpty();
	}

	@Test
	@DisplayName("상품이 옵션 목록에 404를 주고 그 대회가 취소됐으면 409(대회 취소)다")
	void optionsNotFoundForCanceledRace() throws Exception {
		long orderId = orderAndGetId(1, COURSE_ID);
		kitOptionPort.markNotFound(RACE_ID);
		RaceCourseInfo open = StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null);
		raceCoursePort.put(new RaceCourseInfo(open.raceCourseId(), open.raceId(), open.raceDate(),
			open.applicationOpenAt(), open.applicationCloseAt(), open.minAge(), open.maxAge(), open.sellerId(),
			open.courseName(), open.price(), open.kitIncluded(), open.refundDeadline(), null, true,
			open.tshirtSizeOption()));

		pay(1, orderId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_RACE_CANCELED"));
		assertThat(walletPort.payCalls()).isEmpty();
	}

	@Test
	@DisplayName("결제한 참가권의 옵션을 바꾸면 옛 옵션 판매 수 -1, 새 옵션 +1이고 내 참가권·판매자 키트 목록에 바뀐 값이 보인다")
	void changeKitOptions() throws Exception {
		sellerLookupPort.approve(SELLER_MEMBER_ID, StubRaceCoursePort.SELLER_ID);
		long orderId = orderAndGetId(1, COURSE_ID);
		pay(1, orderId, SHIRT_M, SOCKS_FREE).andExpect(status().isOk());

		changeOptions(1, participationIdOf(orderId), SOCKS_FREE, SHIRT_L)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.kitOptions.length()").value(2))
			.andExpect(jsonPath("$.data.kitOptions[?(@.name == 'T-shirt')].value").value("L"));

		assertThat(sold(SHIRT_M)).isZero();
		assertThat(sold(SHIRT_L)).isEqualTo(1);
		// 그대로인 옵션은 건드리지 않는다
		assertThat(sold(SOCKS_FREE)).isEqualTo(1);
		mockMvc.perform(get("/api/v1/order/participations/me").header("X-Member-Id", 1))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.tickets[0].kitOptions[?(@.name == 'T-shirt')].value").value("L"));
		mockMvc.perform(get("/api/v1/order/seller/kits").header("X-Member-Id", SELLER_MEMBER_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.kits[0].kitOptions[?(@.name == 'T-shirt')].value").value("L"));

		// 같은 옵션을 다시 보내면 판매 수는 그대로다
		changeOptions(1, participationIdOf(orderId), SHIRT_L, SOCKS_FREE).andExpect(status().isOk());
		assertThat(sold(SHIRT_L)).isEqualTo(1);
		assertThat(sold(SOCKS_FREE)).isEqualTo(1);
	}

	@Test
	@DisplayName("새 옵션이 다 팔렸으면 결제와 같은 품절 409이고, 판매 수·고른 옵션은 그대로다")
	void changeToSoldOut() throws Exception {
		long other = orderAndGetId(2, COURSE_ID);
		pay(2, other, SHIRT_L, SOCKS_FREE).andExpect(status().isOk());
		long orderId = orderAndGetId(1, COURSE_ID);
		pay(1, orderId, SHIRT_M, SOCKS_FREE).andExpect(status().isOk());

		changeOptions(1, participationIdOf(orderId), SHIRT_L, SOCKS_FREE)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_KIT_OPTION_SOLD_OUT"))
			.andExpect(jsonPath("$.data.soldOut.length()").value(1))
			.andExpect(jsonPath("$.data.soldOut[0].kitOptionId").value(SHIRT_L));

		assertThat(sold(SHIRT_M)).isEqualTo(1);
		assertThat(sold(SHIRT_L)).isEqualTo(1);
		assertThat(participationKitOptionRepository.findByParticipationIdOrderByIdAsc(participationIdOf(orderId)))
			.extracting(ParticipationKitOption::getKitOptionId)
			.containsExactlyInAnyOrder(SHIRT_M, SOCKS_FREE);
	}

	@Test
	@DisplayName("접수 마감 뒤·결제 전·남의 참가권·잘못 고른 옵션이면 바꾸지 못한다")
	void changeRejected() throws Exception {
		long unpaid = orderAndGetId(2, COURSE_ID);
		changeOptions(2, participationIdOf(unpaid), SHIRT_M, SOCKS_FREE)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTICIPATION_NOT_EDITABLE"))
			.andExpect(jsonPath("$.message").value("결제한 참가권만 키트 옵션을 바꿀 수 있습니다."));

		long orderId = orderAndGetId(1, COURSE_ID);
		pay(1, orderId, SHIRT_M, SOCKS_FREE).andExpect(status().isOk());
		long participationId = participationIdOf(orderId);
		changeOptions(2, participationId, SHIRT_L, SOCKS_FREE)
			.andExpect(status().isForbidden());
		changeOptions(1, participationId, SHIRT_L)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ORDER_KIT_OPTION_INVALID"));

		raceCoursePort.put(StubRaceCoursePort.closedCourse(COURSE_ID, RACE_ID));
		changeOptions(1, participationId, SHIRT_L, SOCKS_FREE)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_APPLICATION_CLOSED"))
			.andExpect(jsonPath("$.message").value("접수가 마감되어 키트 옵션을 바꿀 수 없습니다."));

		assertThat(sold(SHIRT_M)).isEqualTo(1);
		assertThat(sold(SHIRT_L)).isZero();
	}

	@Test
	@DisplayName("환불 기한이 접수 마감보다 일러 마감 전에 키트를 보냈으면 옵션을 바꾸지 못한다")
	void changeAfterShipped() throws Exception {
		sellerLookupPort.approve(SELLER_MEMBER_ID, StubRaceCoursePort.SELLER_ID);
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null,
			LocalDate.now().minusDays(1)));
		long orderId = orderAndGetId(1, COURSE_ID);
		pay(1, orderId, SHIRT_M, SOCKS_FREE).andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/order/seller/kits/" + orderItemIdOf(orderId) + "/shipment")
				.header("X-Member-Id", SELLER_MEMBER_ID)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"carrierId\": \"kr.cjlogistics\", \"trackingNumber\": \"684912345678\"}"))
			.andExpect(status().isOk());

		changeOptions(1, participationIdOf(orderId), SHIRT_L, SOCKS_FREE)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_PARTICIPATION_NOT_EDITABLE"))
			.andExpect(jsonPath("$.message").value("이미 발송한 키트라 옵션을 바꿀 수 없습니다."));
		assertThat(sold(SHIRT_M)).isEqualTo(1);
		assertThat(sold(SHIRT_L)).isZero();
	}

	@Test
	@DisplayName("취소된 대회는 409, 상품이 옵션 목록을 주지 못하면 503이고 판매 수는 그대로다")
	void changeWhenRaceCanceledOrOptionsMissing() throws Exception {
		long orderId = orderAndGetId(1, COURSE_ID);
		pay(1, orderId, SHIRT_M, SOCKS_FREE).andExpect(status().isOk());
		long participationId = participationIdOf(orderId);

		kitOptionPort.markNotFound(RACE_ID);
		changeOptions(1, participationId, SHIRT_L, SOCKS_FREE)
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("ORDER_EXTERNAL_SERVICE_UNAVAILABLE"));

		raceCoursePort.put(StubRaceCoursePort.canceled(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null)));
		changeOptions(1, participationId, SHIRT_L, SOCKS_FREE)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_RACE_CANCELED"));
		assertThat(sold(SHIRT_M)).isEqualTo(1);
		assertThat(sold(SHIRT_L)).isZero();
	}

	@RepeatedTest(10)
	@DisplayName("같은 참가권을 동시에 두 번 바꿔도 티셔츠 판매 수는 1이고, 고른 옵션과 판매 수가 맞는다")
	void changeConcurrently() throws Exception {
		long shirtXl = 13;
		kitOptionPort.add(RACE_ID, shirtXl, "T-shirt", "XL", 30);
		long orderId = orderAndGetId(1, COURSE_ID);
		pay(1, orderId, SHIRT_M, SOCKS_FREE).andExpect(status().isOk());
		long participationId = participationIdOf(orderId);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (long shirt : new long[] {SHIRT_L, shirtXl}) {
				results.add(executor.submit(() -> {
					start.await();
					return changeOptions(1, participationId, shirt, SOCKS_FREE)
						.andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();
			for (Future<Integer> result : results) {
				assertThat(result.get()).isEqualTo(200);
			}
		} finally {
			executor.shutdownNow();
		}
		long chosenShirt = participationKitOptionRepository.findByParticipationIdOrderByIdAsc(participationId).stream()
			.filter(option -> option.getOptionName().equals("T-shirt"))
			.map(ParticipationKitOption::getKitOptionId)
			.findFirst().orElseThrow();
		assertThat(sold(SHIRT_M)).isZero();
		assertThat(sold(SHIRT_L) + sold(shirtXl)).isEqualTo(1);
		assertThat(sold(chosenShirt)).isEqualTo(1);
		assertThat(sold(SOCKS_FREE)).isEqualTo(1);
	}

	private ResultActions changeOptions(long memberId, long participationId, long... kitOptionIds) throws Exception {
		StringBuilder ids = new StringBuilder();
		for (long id : kitOptionIds) {
			ids.append(ids.isEmpty() ? "" : ",").append(id);
		}
		return mockMvc.perform(put("/api/v1/order/participations/" + participationId + "/kit-options")
			.header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"kitOptionIds\": [" + ids + "]}"));
	}

	private int sold(long kitOptionId) {
		return kitOptionSaleRepository.findById(kitOptionId).map(KitOptionSale::getSold).orElse(0);
	}

	private ResultActions pay(long memberId, long orderId, long... kitOptionIds) throws Exception {
		StringBuilder ids = new StringBuilder();
		for (long id : kitOptionIds) {
			ids.append(ids.isEmpty() ? "" : ",").append(id);
		}
		return mockMvc.perform(post(BASE_URL + "/" + orderId + "/payment").header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"kitOptionIds\": [" + ids + "]}"));
	}

	private ResultActions cancel(long memberId, long orderId, long orderItemId) throws Exception {
		return mockMvc.perform(post(BASE_URL + "/" + orderId + "/items/" + orderItemId + "/cancel")
			.header("X-Member-Id", memberId));
	}

	private long participationIdOf(long orderId) {
		return transactionTemplate.execute(status -> orderRepository.findById(orderId).orElseThrow().participationId());
	}

	private ParticipationStatus participationStatusOf(long orderId) {
		return participationRepository.findById(participationIdOf(orderId)).orElseThrow().getStatus();
	}

	private long orderItemIdOf(long orderId) {
		return transactionTemplate.execute(status ->
			orderRepository.findById(orderId).orElseThrow().getItems().getFirst().getId());
	}

	// 신청서 작성 → [결제하기]로 결제 대기 주문을 만든다
	private long orderAndGetId(long memberId, long courseId) throws Exception {
		QueueEntry.enter(mockMvc, memberId, courseId);
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
				""".formatted(courseId))));
		return read(mockMvc.perform(post(BASE_URL)
			.header("X-Member-Id", memberId)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"participationId\": %d}".formatted(participationId))));
	}

	private long read(ResultActions result) throws Exception {
		String body = result.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(body, "$.data.id").longValue();
	}
}
