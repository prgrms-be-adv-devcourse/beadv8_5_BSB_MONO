package com.bukang.order.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.order.domain.ParticipantInfo;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.out.OrderOutboxKafkaEventRepository;
import com.bukang.order.out.ParticipationRepository;
import com.bukang.order.support.SeatOutboxMessages;
import com.bukang.order.support.StubRaceCoursePort;
import com.bukang.order.support.StubRaceCoursePortConfig;
import com.bukang.order.support.StubSeatHoldPort;
import com.bukang.order.support.StubSeatHoldPortConfig;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({StubRaceCoursePortConfig.class, StubSeatHoldPortConfig.class})
class ApiV1ParticipationTest {
	private static final String BASE_URL = "/api/v1/order/participations";
	private static final long MEMBER_ID = 1;
	private static final long OTHER_MEMBER_ID = 2;
	private static final long COURSE_ID = 10;
	private static final long RACE_ID = 100;
	private static final String ADDRESS = "서울 성동구 왕십리로 00";

	@Autowired
	private MockMvc mockMvc;

	// 새 신청서 저장만 일부러 실패시킬 때 쓴다. 그 밖에는 진짜 저장소처럼 동작한다
	@MockitoSpyBean
	private ParticipationRepository participationRepository;

	@Autowired
	private StubSeatHoldPort seatHoldPort;

	@Autowired
	private OrderOutboxKafkaEventRepository orderOutboxKafkaEventRepository;

	@Autowired
	private StubRaceCoursePort raceCoursePort;

	@BeforeEach
	void setUp() {
		orderOutboxKafkaEventRepository.deleteAll();
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null));
	}

	@AfterEach
	void tearDown() {
		participationRepository.deleteAll();
		raceCoursePort.clear();
		seatHoldPort.clear();
		orderOutboxKafkaEventRepository.deleteAll();
	}

	@Test
	@DisplayName("신청서를 저장하면 자리 확보 전(PENDING) 개인 신청이 되고 동의 일시가 남는다")
	void apply() throws Exception {
		apply(MEMBER_ID, applyBody(COURSE_ID, participant("김러너", "1994-06-25", "L"), true))
			.andExpect(status().isCreated())
			.andExpect(header().exists("Location"))
			.andExpect(jsonPath("$.status").value(201))
			.andExpect(jsonPath("$.message").value("참가 신청서를 저장했습니다."))
			.andExpect(jsonPath("$.data.raceId").value(RACE_ID))
			.andExpect(jsonPath("$.data.raceCourseId").value(COURSE_ID))
			.andExpect(jsonPath("$.data.memberId").value(MEMBER_ID))
			.andExpect(jsonPath("$.data.participationType").value("INDIVIDUAL"))
			.andExpect(jsonPath("$.data.status").value("PENDING"))
			.andExpect(jsonPath("$.data.participant.name").value("김러너"))
			.andExpect(jsonPath("$.data.participant.emergencyContactName").value("김가족"))
			.andExpect(jsonPath("$.data.thirdPartyConsent").value(true))
			.andExpect(jsonPath("$.data.consentedAt").isNotEmpty())
			.andExpect(jsonPath("$.data.paymentDueAt").isEmpty());

		Participation saved = participationRepository.findAll().getFirst();
		assertThat(saved.getStatus()).isEqualTo(ParticipationStatus.PENDING);
		assertThat(saved.getActiveFlag()).isTrue();
		assertThat(saved.getCrewApplicationId()).isNull();
	}

	@Test
	@DisplayName("휴대폰 번호는 010-1234-5678 형식으로, 티셔츠 사이즈는 대문자로, 빈 상세 주소는 null로, 배송지는 앞뒤 공백을 빼고 저장한다")
	void applyNormalizesInput() throws Exception {
		String body = """
			{ "raceCourseId": %d, "thirdPartyConsent": true,
				"participant": { "name": " 김러너 ", "birth": "1994-06-25", "gender": "FEMALE",
				"phone": "01012345678", "emergencyContactName": "김가족", "emergencyContactPhone": "011-987-6543",
				"tshirtSize": " l ", "shippingAddress": { "recipientName": " 김러너 ", "recipientPhone": "01012345678",
				"zipcode": "04700", "address": "  서울 성동구 왕십리로 00  ", "addressDetail": " " } } }
			""".formatted(COURSE_ID);

		apply(MEMBER_ID, body)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.participant.name").value("김러너"))
			.andExpect(jsonPath("$.data.participant.phone").value("010-1234-5678"))
			.andExpect(jsonPath("$.data.participant.emergencyContactPhone").value("011-987-6543"))
			.andExpect(jsonPath("$.data.participant.tshirtSize").value("L"))
			.andExpect(jsonPath("$.data.participant.shippingAddress.recipientName").value("김러너"))
			.andExpect(jsonPath("$.data.participant.shippingAddress.recipientPhone").value("010-1234-5678"))
			.andExpect(jsonPath("$.data.participant.shippingAddress.zipcode").value("04700"))
			.andExpect(jsonPath("$.data.participant.shippingAddress.address").value("서울 성동구 왕십리로 00"))
			.andExpect(jsonPath("$.data.participant.shippingAddress.addressDetail").isEmpty());
	}

	@Test
	@DisplayName("키트가 있는 종목은 배송지 없이 신청하거나, 주소가 비었거나 255자를 넘거나, 우편번호가 5자리 숫자가 아니면 400이다")
	void applyWithoutShippingAddress() throws Exception {
		apply(MEMBER_ID, applyBody(COURSE_ID, participant("김러너", "1994-06-25", "L", null), true))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("키트를 받을 배송 주소를 작성해주세요."))
			.andExpect(jsonPath("$.code").value("ORDER_SHIPPING_ADDRESS_REQUIRED"));
		apply(MEMBER_ID, applyBody(COURSE_ID, participant("김러너", "1994-06-25", "L", " "), true))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("주소를 작성해주세요."));
		apply(MEMBER_ID, applyBody(COURSE_ID, participant("김러너", "1994-06-25", "L", "가".repeat(256)), true))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("주소는 255자 이하로 작성해주세요."));
		String wrongZipcode = participant("김러너", "1994-06-25", "L", ADDRESS).replace("\"04700\"", "\"0470\"");
		apply(MEMBER_ID, applyBody(COURSE_ID, wrongZipcode, true))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("우편번호는 숫자 5자리로 작성해주세요."));

		assertThat(participationRepository.count()).isZero();
	}

	@Test
	@DisplayName("키트가 없는 종목은 배송지 없이 신청되고, 배송지를 보내도 저장하지 않는다")
	void applyCourseWithoutKit() throws Exception {
		raceCoursePort.put(StubRaceCoursePort.courseWithoutKit(COURSE_ID, RACE_ID));

		apply(MEMBER_ID, applyBody(COURSE_ID, participant("김러너", "1994-06-25", "", null), true))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.participant.shippingAddress").isEmpty());

		apply(OTHER_MEMBER_ID, applyBody(COURSE_ID, participant("박러너", "1990-01-01", "", "서울 어딘가"), true))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.participant.shippingAddress").isEmpty());
		assertThat(participationRepository.findAll())
			.allSatisfy(saved -> assertThat(saved.getParticipant().getShippingAddress()).isNull());
	}

	@Test
	@DisplayName("티셔츠를 주는 종목은 사이즈를 고르지 않거나 종목이 주지 않는 사이즈를 고르면 400이고 저장되지 않는다")
	void applyWithUnavailableTshirtSize() throws Exception {
		apply(MEMBER_ID, applyBody(COURSE_ID, participant("김러너", "1994-06-25", " "), true))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("티셔츠 사이즈를 골라주세요(고를 수 있는 사이즈: S, M, L, XL)."))
			.andExpect(jsonPath("$.code").value("ORDER_TSHIRT_SIZE_REQUIRED"));

		apply(MEMBER_ID, applyBody(COURSE_ID, participant("김러너", "1994-06-25", "XXL"), true))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message")
				.value("이 종목에서 고를 수 없는 티셔츠 사이즈입니다(고를 수 있는 사이즈: S, M, L, XL)."))
			.andExpect(jsonPath("$.code").value("ORDER_TSHIRT_SIZE_NOT_AVAILABLE"));

		assertThat(participationRepository.count()).isZero();
	}

	@Test
	@DisplayName("티셔츠를 주지 않는 종목은 사이즈 없이 신청되고, 사이즈를 보내도 저장하지 않는다")
	void applyCourseWithoutTshirt() throws Exception {
		raceCoursePort.put(StubRaceCoursePort.courseWithoutTshirt(COURSE_ID, RACE_ID));

		apply(MEMBER_ID, applyBody(COURSE_ID, participant("김러너", "1994-06-25", ""), true))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.participant.tshirtSize").isEmpty());
		apply(OTHER_MEMBER_ID, applyBody(COURSE_ID, participant("박러너", "1990-01-01", "L"), true))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.participant.tshirtSize").isEmpty());
		assertThat(participationRepository.findAll())
			.allSatisfy(saved -> assertThat(saved.getParticipant().getTshirtSize()).isNull());
	}

	@Test
	@DisplayName("필수 항목(참가자 정보, 비상 연락처, 제3자 제공 동의)이 빠지면 400이고 저장되지 않는다")
	void applyWithoutRequired() throws Exception {
		apply(MEMBER_ID, applyBody(COURSE_ID, participant("", "1994-06-25", "L"), true))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("참가자 이름을 작성해주세요."))
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		String noEmergency = """
			{ "raceCourseId": %d, "thirdPartyConsent": true,
				"participant": { "name": "김러너", "birth": "1994-06-25", "gender": "MALE",
				"phone": "010-1234-5678", "emergencyContactPhone": "010-9876-5432" } }
			""".formatted(COURSE_ID);
		apply(MEMBER_ID, noEmergency)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("비상 연락처 이름을 작성해주세요."));

		apply(MEMBER_ID, applyBody(COURSE_ID, participant("김러너", "1994-06-25", "L"), false))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("개인정보 제3자 제공에 동의해야 신청할 수 있습니다."))
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		assertThat(participationRepository.count()).isZero();
	}

	@Test
	@DisplayName("대회 당일 만 나이가 종목의 나이 제한 밖이면 400이다")
	void applyOutOfAgeRange() throws Exception {
		String birth = LocalDate.now().minusYears(12).toString();

		apply(MEMBER_ID, applyBody(COURSE_ID, participant("김어린", birth, "S"), true))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("참가할 수 있는 나이가 아닙니다(대회 당일 만 14세 이상, 신청자 만 12세)."))
			.andExpect(jsonPath("$.code").value("ORDER_AGE_RESTRICTED"));

		assertThat(participationRepository.count()).isZero();
	}

	@Test
	@DisplayName("같은 대회에 결제 전 신청서가 남아 있으면 그 신청서를 취소하고 새 신청서를 받는다 (같은 대회의 다른 종목이어도)")
	void applyReplacesPendingParticipation() throws Exception {
		long first = applyAndGetId(MEMBER_ID);

		// 주문 생성이 실패했거나 결제 없이 떠나 결제 전 신청서가 남은 뒤, 신청서 화면에 다시 들어와 저장한 상황
		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID + 1, RACE_ID, 14, null));
		apply(MEMBER_ID, applyBody(COURSE_ID + 1, participant("박러너", "1990-01-01", "M"), true))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.status").value("PENDING"))
			.andExpect(jsonPath("$.data.raceCourseId").value(COURSE_ID + 1))
			.andExpect(jsonPath("$.data.participant.name").value("박러너"));

		Participation replaced = participationRepository.findById(first).orElseThrow();
		assertThat(replaced.getStatus()).isEqualTo(ParticipationStatus.CANCELED);
		assertThat(replaced.getActiveFlag()).isNull();
		assertThat(activeCount(MEMBER_ID)).isEqualTo(1);

		// 다른 회원은 따로 신청할 수 있다
		apply(OTHER_MEMBER_ID, applyBody(COURSE_ID, participant("최러너", "1990-01-01", "M"), true))
			.andExpect(status().isCreated());
		assertThat(activeCount(OTHER_MEMBER_ID)).isEqualTo(1);
	}

	@Test
	@DisplayName("다시 신청한 내용이 잘못되면(나이 제한 밖) 400이고, 남아 있던 결제 전 신청서는 취소하지 않는다")
	void invalidApplyKeepsPendingParticipation() throws Exception {
		long first = applyAndGetId(MEMBER_ID);
		String birth = LocalDate.now().minusYears(12).toString();

		apply(MEMBER_ID, applyBody(COURSE_ID, participant("김어린", birth, "S"), true))
			.andExpect(status().isBadRequest());

		assertThat(participationRepository.findById(first).orElseThrow().getStatus())
			.isEqualTo(ParticipationStatus.PENDING);
		assertThat(activeCount(MEMBER_ID)).isEqualTo(1);
	}

	@Test
	@DisplayName("남은 결제 전 신청서를 교체로 취소하면, 그 신청서의 자리 해제 메시지를 아웃박스에 한 번 남긴다 (커밋 뒤 상품에 간다)")
	void replaceReleasesSeatOfCanceledParticipation() throws Exception {
		long first = applyAndGetId(MEMBER_ID);

		apply(MEMBER_ID, applyBody(COURSE_ID, participant("김러너", "1994-06-25", "L"), true))
			.andExpect(status().isCreated());

		// [결제하기]에서 상품은 자리를 잡았는데 주문이 롤백됐을 수 있어, 자리를 잡았는지 몰라도 보낸다(상품은 멱등)
		assertThat(releasedSeats()).containsExactly(first);
	}

	@Test
	@DisplayName("교체 중 새 신청서 저장이 실패해 롤백되면 자리 해제 메시지도 함께 롤백되어 남지 않고, 남은 신청서도 그대로다")
	void replaceRollbackDoesNotReleaseSeat() throws Exception {
		long first = applyAndGetId(MEMBER_ID);
		doThrow(new IllegalStateException("저장 실패 (테스트)"))
			.when(participationRepository).saveAndFlush(any(Participation.class));

		apply(MEMBER_ID, applyBody(COURSE_ID, participant("김러너", "1994-06-25", "L"), true))
			.andExpect(status().isInternalServerError());

		// 보낸 뒤 롤백되면 살아 있는 신청서의 자리만 풀려, 그 신청서로 다시 결제할 수 없게 된다
		assertThat(releasedSeats()).isEmpty();
		assertThat(participationRepository.findById(first).orElseThrow().getStatus())
			.isEqualTo(ParticipationStatus.PENDING);
	}

	@Test
	@DisplayName("같은 대회에 결제 대기·결제 완료 신청이 있으면 409이고, 그 신청은 그대로다")
	void applyTwiceWhenAppliedOrConfirmed() throws Exception {
		for (ParticipationStatus status : new ParticipationStatus[] {ParticipationStatus.APPLIED,
			ParticipationStatus.CONFIRMED}) {
			long id = applyAndGetId(MEMBER_ID);
			moveTo(id, status);

			apply(MEMBER_ID, applyBody(COURSE_ID, participant("김러너", "1994-06-25", "L"), true))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("이미 이 대회에 신청했습니다."))
				.andExpect(jsonPath("$.code").value("ORDER_DUPLICATE_PARTICIPATION"));

			assertThat(participationRepository.findById(id).orElseThrow().getStatus()).isEqualTo(status);
			assertThat(activeCount(MEMBER_ID)).isEqualTo(1);
			participationRepository.deleteAll();
		}
	}

	@Test
	@DisplayName("같은 회원이 같은 대회에 동시에 신청해도 살아 있는 신청은 1건만 남는다")
	void applyConcurrently() throws Exception {
		applyAndGetId(MEMBER_ID);
		int threads = 4;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> results = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			results.add(executor.submit(() -> {
				start.await();
				return apply(MEMBER_ID, applyBody(COURSE_ID, participant("김러너", "1994-06-25", "L"), true))
					.andReturn().getResponse().getStatus();
			}));
		}
		start.countDown();
		List<Integer> statuses = new ArrayList<>();
		for (Future<Integer> result : results) {
			statuses.add(result.get(30, TimeUnit.SECONDS));
		}
		executor.shutdown();

		// 늦은 쪽은 새 신청서를 다시 취소하고 받거나(201), 먼저 끝난 신청과 겹쳐 409로 끝난다
		assertThat(statuses).contains(201).allMatch(code -> code == 201 || code == 409);
		assertThat(activeCount(MEMBER_ID)).isEqualTo(1);
	}

	@Test
	@DisplayName("끝난 신청(취소 등)만 있으면 같은 대회에 다시 신청할 수 있다")
	void applyAgainAfterCanceled() throws Exception {
		long id = applyAndGetId(MEMBER_ID);
		finish(id, ParticipationStatus.CANCELED);

		apply(MEMBER_ID, applyBody(COURSE_ID, participant("김러너", "1994-06-25", "L"), true))
			.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("판매자가 취소한 대회는 접수 기간 안이어도 409 \"취소된 대회입니다.\"로 신청을 막는다")
	void applyCanceledRace() throws Exception {
		raceCoursePort.put(StubRaceCoursePort.canceled(StubRaceCoursePort.openCourse(COURSE_ID, RACE_ID, 14, null)));

		apply(MEMBER_ID, applyBody(COURSE_ID, participant("김러너", "1994-06-25", "L"), true))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("취소된 대회입니다."))
			.andExpect(jsonPath("$.code").value("ORDER_RACE_CANCELED"));
	}

	@Test
	@DisplayName("접수 기간이 아니면 409, 없는 종목이면 404다")
	void applyNotAccepting() throws Exception {
		raceCoursePort.put(StubRaceCoursePort.closedCourse(COURSE_ID, RACE_ID));
		apply(MEMBER_ID, applyBody(COURSE_ID, participant("김러너", "1994-06-25", "L"), true))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("접수 기간이 아닙니다."))
			.andExpect(jsonPath("$.code").value("ORDER_APPLICATION_CLOSED"));

		apply(MEMBER_ID, applyBody(999, participant("김러너", "1994-06-25", "L"), true))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("존재하지 않는 종목입니다."))
			.andExpect(jsonPath("$.code").value("ORDER_COURSE_NOT_FOUND"));
	}

	@Test
	@DisplayName("회원 ID가 0 이하면 400이다")
	void applyWithInvalidMemberId() throws Exception {
		apply(0, applyBody(COURSE_ID, participant("김러너", "1994-06-25", "L"), true))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("회원 ID가 올바르지 않습니다."))
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("두 번째 신청부터 최근 신청의 참가자 정보가 채워져 내려온다. 신청한 적이 없으면 data가 null이다")
	void latestParticipant() throws Exception {
		mockMvc.perform(get(BASE_URL + "/latest-participant").header("X-Member-Id", MEMBER_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("이전에 신청한 참가자 정보가 없습니다."))
			.andExpect(jsonPath("$.data").isEmpty());

		applyAndGetId(MEMBER_ID);

		mockMvc.perform(get(BASE_URL + "/latest-participant").header("X-Member-Id", MEMBER_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("최근 참가자 정보를 조회했습니다."))
			.andExpect(jsonPath("$.data.name").value("김러너"))
			.andExpect(jsonPath("$.data.birth").value("1994-06-25"))
			.andExpect(jsonPath("$.data.gender").value("MALE"))
			.andExpect(jsonPath("$.data.phone").value("010-1234-5678"))
			.andExpect(jsonPath("$.data.emergencyContactName").value("김가족"))
			.andExpect(jsonPath("$.data.emergencyContactPhone").value("010-9876-5432"))
			.andExpect(jsonPath("$.data.tshirtSize").value("L"))
			.andExpect(jsonPath("$.data.shippingAddress.address").value(ADDRESS));

		// 다른 회원의 정보는 채워 주지 않는다
		mockMvc.perform(get(BASE_URL + "/latest-participant").header("X-Member-Id", OTHER_MEMBER_ID))
			.andExpect(jsonPath("$.data").isEmpty());
	}

	@Test
	@DisplayName("마지막 신청이 키트 없는 종목이면, 다음 신청서의 배송 주소·티셔츠 사이즈는 각각 적은 최근 신청에서 채운다")
	void latestParticipantKeepsShippingAddress() throws Exception {
		applyAndGetId(MEMBER_ID);
		raceCoursePort.put(StubRaceCoursePort.courseWithoutKit(COURSE_ID + 1, RACE_ID + 1));
		apply(MEMBER_ID, applyBody(COURSE_ID + 1, participant("김러너", "1994-06-25", "M", null), true))
			.andExpect(status().isCreated());

		mockMvc.perform(get(BASE_URL + "/latest-participant").header("X-Member-Id", MEMBER_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.tshirtSize").value("L"))
			.andExpect(jsonPath("$.data.shippingAddress.address").value(ADDRESS));
	}

	@Test
	@DisplayName("본인 신청은 조회되고, 다른 회원의 신청은 403, 없는 신청은 404다")
	void findById() throws Exception {
		long id = applyAndGetId(MEMBER_ID);

		mockMvc.perform(get(BASE_URL + "/" + id).header("X-Member-Id", MEMBER_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("참가 신청을 조회했습니다."))
			.andExpect(jsonPath("$.data.id").value(id));

		mockMvc.perform(get(BASE_URL + "/" + id).header("X-Member-Id", OTHER_MEMBER_ID))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("본인의 참가 신청만 보거나 고칠 수 있습니다."))
			.andExpect(jsonPath("$.code").value("ORDER_PARTICIPATION_FORBIDDEN"));

		mockMvc.perform(get(BASE_URL + "/999999").header("X-Member-Id", MEMBER_ID))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("존재하지 않는 참가 신청입니다."))
			.andExpect(jsonPath("$.code").value("ORDER_PARTICIPATION_NOT_FOUND"));
	}

	@Test
	@DisplayName("접수 마감 전에는 참가자 정보가 수정된다")
	void changeParticipantBeforeClose() throws Exception {
		long id = applyAndGetId(MEMBER_ID);

		change(MEMBER_ID, id, participant("김러너", "1994-06-25", "XL", "부산 해운대구 해운대로 00"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("참가자 정보를 수정했습니다."))
			.andExpect(jsonPath("$.data.participant.tshirtSize").value("XL"))
			.andExpect(jsonPath("$.data.participant.shippingAddress.address").value("부산 해운대구 해운대로 00"));

		ParticipantInfo saved = participationRepository.findById(id).orElseThrow().getParticipant();
		assertThat(saved.getTshirtSize()).isEqualTo("XL");
		assertThat(saved.getShippingAddress().getAddress()).isEqualTo("부산 해운대구 해운대로 00");

		// 키트가 있는 종목은 수정할 때도 배송 주소를 비울 수 없다
		change(MEMBER_ID, id, participant("김러너", "1994-06-25", "XL", null))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("키트를 받을 배송 주소를 작성해주세요."))
			.andExpect(jsonPath("$.code").value("ORDER_SHIPPING_ADDRESS_REQUIRED"));
		ParticipantInfo unchanged = participationRepository.findById(id).orElseThrow().getParticipant();
		assertThat(unchanged.getShippingAddress().getAddress()).isEqualTo("부산 해운대구 해운대로 00");
	}

	@Test
	@DisplayName("키트가 없는 종목은 참가자 정보를 고칠 때 보낸 배송지도 저장하지 않는다")
	void changeParticipantCourseWithoutKit() throws Exception {
		raceCoursePort.put(StubRaceCoursePort.courseWithoutKit(COURSE_ID, RACE_ID));
		long id = applyAndGetId(MEMBER_ID);

		change(MEMBER_ID, id, participant("김러너", "1994-06-25", "", "서울 어딘가"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.participant.shippingAddress").isEmpty());
		assertThat(participationRepository.findById(id).orElseThrow().getParticipant().getShippingAddress()).isNull();
	}

	@Test
	@DisplayName("참가자 정보를 고칠 때도 종목이 주지 않는 티셔츠 사이즈는 400이고 원래 사이즈가 남는다")
	void changeParticipantUnavailableTshirtSize() throws Exception {
		long id = applyAndGetId(MEMBER_ID);

		change(MEMBER_ID, id, participant("김러너", "1994-06-25", "XXXL"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message")
				.value("이 종목에서 고를 수 없는 티셔츠 사이즈입니다(고를 수 있는 사이즈: S, M, L, XL)."))
			.andExpect(jsonPath("$.code").value("ORDER_TSHIRT_SIZE_NOT_AVAILABLE"));

		assertThat(participationRepository.findById(id).orElseThrow().getParticipant().getTshirtSize())
			.isEqualTo("L");
	}

	@Test
	@DisplayName("접수 마감 뒤에는 참가자 정보 수정이 409로 거절된다")
	void changeParticipantAfterClose() throws Exception {
		long id = applyAndGetId(MEMBER_ID);
		raceCoursePort.put(StubRaceCoursePort.closedCourse(COURSE_ID, RACE_ID));

		change(MEMBER_ID, id, participant("김러너", "1994-06-25", "XL"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("접수가 마감되어 참가자 정보를 고칠 수 없습니다."))
			.andExpect(jsonPath("$.code").value("ORDER_APPLICATION_CLOSED"));

		assertThat(participationRepository.findById(id).orElseThrow().getParticipant().getTshirtSize())
			.isEqualTo("L");
	}

	@Test
	@DisplayName("다른 회원의 신청은 403, 끝난 신청은 409, 나이 제한 밖으로 고치면 400이다")
	void changeParticipantRejected() throws Exception {
		long id = applyAndGetId(MEMBER_ID);

		change(OTHER_MEMBER_ID, id, participant("박러너", "1990-01-01", "M"))
			.andExpect(status().isForbidden());

		change(MEMBER_ID, id, participant("김러너", LocalDate.now().minusYears(10).toString(), "L"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("참가할 수 있는 나이가 아닙니다(대회 당일 만 14세 이상, 신청자 만 10세)."))
			.andExpect(jsonPath("$.code").value("ORDER_AGE_RESTRICTED"));

		finish(id, ParticipationStatus.CANCELED);
		change(MEMBER_ID, id, participant("김러너", "1994-06-25", "XL"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("참가자 정보를 고칠 수 없는 신청입니다(현재 상태: 취소)."))
			.andExpect(jsonPath("$.code").value("ORDER_PARTICIPATION_NOT_EDITABLE"));
	}

	@Test
	@DisplayName("결제 전 신청서를 취소하면 끝난 신청이 되고, 같은 대회의 다른 종목으로 바로 다시 신청할 수 있다")
	void cancelBeforePayment() throws Exception {
		long id = applyAndGetId(MEMBER_ID);

		cancel(MEMBER_ID, id)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("참가 신청서를 취소했습니다."))
			.andExpect(jsonPath("$.data.id").value(id))
			.andExpect(jsonPath("$.data.status").value("CANCELED"));

		Participation canceled = participationRepository.findById(id).orElseThrow();
		assertThat(canceled.getStatus()).isEqualTo(ParticipationStatus.CANCELED);
		assertThat(canceled.getActiveFlag()).isNull();
		assertThat(canceled.getCanceledAt()).isNotNull();
		// 자리만 잡히고 주문이 롤백된 신청서일 수 있어, 상품에 자리를 풀어 달라고 아웃박스에 남긴다
		assertThat(releasedSeats()).containsExactly(id);

		raceCoursePort.put(StubRaceCoursePort.openCourse(COURSE_ID + 1, RACE_ID, 14, null));
		apply(MEMBER_ID, applyBody(COURSE_ID + 1, participant("김러너", "1994-06-25", "L"), true))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.raceCourseId").value(COURSE_ID + 1));
	}

	@Test
	@DisplayName("다른 회원의 신청서는 403, 없는 신청서는 404이고 신청서는 그대로 남는다")
	void cancelBeforePaymentNotOwned() throws Exception {
		long id = applyAndGetId(MEMBER_ID);

		cancel(OTHER_MEMBER_ID, id)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("본인의 참가 신청만 보거나 고칠 수 있습니다."))
			.andExpect(jsonPath("$.code").value("ORDER_PARTICIPATION_FORBIDDEN"));
		cancel(MEMBER_ID, id + 1000)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("존재하지 않는 참가 신청입니다."))
			.andExpect(jsonPath("$.code").value("ORDER_PARTICIPATION_NOT_FOUND"));

		assertThat(participationRepository.findById(id).orElseThrow().getStatus())
			.isEqualTo(ParticipationStatus.PENDING);
	}

	@Test
	@DisplayName("결제 대기·결제 완료·이미 끝난 신청서는 409이고 상태가 바뀌지 않는다")
	void cancelBeforePaymentRejected() throws Exception {
		long id = applyAndGetId(MEMBER_ID);

		moveTo(id, ParticipationStatus.APPLIED);
		cancel(MEMBER_ID, id)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("결제 대기 중인 신청은 취소할 수 없습니다. 결제하지 않으면 결제 기한이 지난 뒤 자동으로 풀립니다."))
			.andExpect(jsonPath("$.code").value("ORDER_PARTICIPATION_NOT_CANCELABLE"));
		assertThat(participationRepository.findById(id).orElseThrow().getStatus())
			.isEqualTo(ParticipationStatus.APPLIED);

		moveTo(id, ParticipationStatus.CONFIRMED);
		cancel(MEMBER_ID, id)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("결제한 참가권은 주문 상품 취소로 취소해주세요."))
			.andExpect(jsonPath("$.code").value("ORDER_PARTICIPATION_NOT_CANCELABLE"));
		assertThat(participationRepository.findById(id).orElseThrow().getStatus())
			.isEqualTo(ParticipationStatus.CONFIRMED);

		moveTo(id, ParticipationStatus.PENDING);
		cancel(MEMBER_ID, id).andExpect(status().isOk());
		cancel(MEMBER_ID, id)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 끝난 신청입니다(현재 상태: 취소)."))
			.andExpect(jsonPath("$.code").value("ORDER_PARTICIPATION_NOT_CANCELABLE"));
	}

	private long activeCount(long memberId) {
		return participationRepository.findAll().stream()
			.filter(participation -> participation.getMemberId() == memberId)
			.filter(participation -> Boolean.TRUE.equals(participation.getActiveFlag()))
			.count();
	}

	private ResultActions apply(long memberId, String body) throws Exception {
		return mockMvc.perform(post(BASE_URL)
			.header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions change(long memberId, long participationId, String participantBody) throws Exception {
		return mockMvc.perform(put(BASE_URL + "/" + participationId + "/participant")
			.header("X-Member-Id", memberId)
			.contentType(MediaType.APPLICATION_JSON)
			.content(participantBody));
	}

	private ResultActions cancel(long memberId, long participationId) throws Exception {
		return mockMvc.perform(post(BASE_URL + "/" + participationId + "/cancel")
			.header("X-Member-Id", memberId));
	}

	// [결제하기]·결제는 자리·예치금 스텁이 필요해, 이 테스트에서는 살아 있는 상태로 직접 옮긴다
	private void moveTo(long participationId, ParticipationStatus status) {
		Participation participation = participationRepository.findById(participationId).orElseThrow();
		ReflectionTestUtils.setField(participation, "status", status);
		ReflectionTestUtils.setField(participation, "activeFlag", status.isActive() ? Boolean.TRUE : null);
		participationRepository.save(participation);
	}

	private long applyAndGetId(long memberId) throws Exception {
		apply(memberId, applyBody(COURSE_ID, participant("김러너", "1994-06-25", "L"), true))
			.andExpect(status().isCreated());
		return participationRepository.findFirstByMemberIdOrderByIdDesc(memberId).orElseThrow().getId();
	}

	// 상태 전이 API(결제·취소)가 아직 없어 테스트에서 직접 끝난 신청으로 만든다
	private void finish(long participationId, ParticipationStatus status) {
		Participation participation = participationRepository.findById(participationId).orElseThrow();
		ReflectionTestUtils.setField(participation, "status", status);
		ReflectionTestUtils.setField(participation, "activeFlag", null);
		participationRepository.save(participation);
	}

	private static String applyBody(long raceCourseId, String participant, boolean consent) {
		return """
			{ "raceCourseId": %d, "participant": %s, "thirdPartyConsent": %s }
			""".formatted(raceCourseId, participant, consent);
	}

	private static String participant(String name, String birth, String tshirtSize) {
		return participant(name, birth, tshirtSize, ADDRESS);
	}

	// address가 null이면 배송지(shippingAddress)를 보내지 않는다
	private static String participant(String name, String birth, String tshirtSize, String address) {
		String shipping = address == null ? "" : """
			, "shippingAddress": { "recipientName": "김러너", "recipientPhone": "010-1234-5678", "zipcode": "04700",
				"address": "%s", "addressDetail": "101동 1001호" }""".formatted(address);
		return """
			{ "name": "%s", "birth": "%s", "gender": "MALE", "phone": "010-1234-5678",
				"emergencyContactName": "김가족", "emergencyContactPhone": "010-9876-5432", "tshirtSize": "%s"%s }
			""".formatted(name, birth, tshirtSize, shipping);
	}

	private List<Long> restoredSeats() {
		return SeatOutboxMessages.restored(orderOutboxKafkaEventRepository);
	}

	private List<Long> releasedSeats() {
		return SeatOutboxMessages.released(orderOutboxKafkaEventRepository);
	}
}
