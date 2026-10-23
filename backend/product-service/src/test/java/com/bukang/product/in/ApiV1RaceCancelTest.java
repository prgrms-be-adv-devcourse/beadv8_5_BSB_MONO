package com.bukang.product.in;

import static com.bukang.product.support.RaceRequests.course;
import static com.bukang.product.support.RaceRequests.race;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.common.shared.race.event.RaceCancelEvent;
import com.bukang.product.domain.CourseType;
import com.bukang.product.domain.HoldReleaseReason;
import com.bukang.product.domain.HoldStatus;
import com.bukang.product.domain.ParticipationHold;
import com.bukang.product.domain.Race;
import com.bukang.product.domain.RaceCourse;
import com.bukang.product.dto.RaceSaveRequestDto;
import com.bukang.product.support.ProductIntegrationTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 대회 취소 API(POST /api/v1/product/races/{raceId}/cancel)를 확인한다.
 * - 판매 예정·판매 중·판매 중지 대회는 취소되고(CANCELED, 취소 일시 기록), 주문 서비스에 취소 메시지를 보낸다
 * - 결제 대기 자리는 대회 취소 사유로 해제되어 종목의 결제 대기 수가 돌아오고, 판매된 자리는 그대로 남는다
 * - 취소한 대회는 공개 목록·연관검색어에서 빠지고, 상세와 내 대회 목록은 등록한 판매자만 본다
 * - 취소한 대회는 자리 확보·수정·다시 취소를 할 수 없고, 결제 대기 자리의 판매 요청은 기한 지남(EXPIRED)이다
 * - 판매가 끝난 대회(대회일 다음 날 0시부터), 다른 판매자, 판매자가 아닌 회원, 헤더 없음, 없는 대회는 막는다
 * - 취소와 자리 확보가 동시에 들어와도 결제 대기 수와 HELD 자리 수가 맞고, 남은 결제 대기 자리는 판매되지 않는다
 */
class ApiV1RaceCancelTest extends ProductIntegrationTest {
	private static final String RACES_PATH = "/api/v1/product/races";
	// RaceRequests 기본 요청의 대회명
	private static final String DEFAULT_TITLE = "2026 서울 하프 마라톤";

	@PersistenceContext
	private EntityManager entityManager;

	// ---------- 취소 성공 ----------

	@Test
	@DisplayName("판매 중인 대회를 취소하면 200 '대회를 취소했습니다.'이고 판매 상태가 CANCELED, 취소 일시가 기록된다")
	void cancelOnSaleRace() throws Exception {
		RegisteredRace onSale = registerOnSale(race().build());
		LocalDateTime before = LocalDateTime.now();

		cancelRace(onSale.raceId(), SELLER_MEMBER_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(200))
			.andExpect(jsonPath("$.message").value("대회를 취소했습니다."))
			.andExpect(jsonPath("$.data.raceId").value(onSale.raceId()))
			.andExpect(jsonPath("$.data.saleStatus").value("CANCELED"))
			.andExpect(jsonPath("$.data.courseIds[0]").value(onSale.courseId()));

		Race canceled = findRace(onSale.raceId());
		assertThat(canceled.getSaleStatus()).isEqualTo(RaceSaleStatus.CANCELED);
		assertThat(canceled.getCanceledAt()).isBetween(before.minusSeconds(1), LocalDateTime.now().plusSeconds(1));
	}

	@Test
	@DisplayName("판매 중인 대회를 취소하면 order.race.cancel로 대회 ID를 key로, raceId·판매자 7001·취소 일시를 1건 보낸다")
	void publishCancelEvent() throws Exception {
		RegisteredRace onSale = registerOnSale(race().build());

		cancelRace(onSale.raceId(), SELLER_MEMBER_ID).andExpect(status().isOk());

		LocalDateTime canceledAt = findRace(onSale.raceId()).getCanceledAt();
		assertThat(cancelEvents()).containsExactly(new RaceCancelEvent(onSale.raceId(), SELLER_ID, canceledAt));
		assertThat(outboxKeys(KafkaTopics.ORDER_RACE_CANCEL)).containsExactly(String.valueOf(onSale.raceId()));
		// 주문 서비스가 읽는 필드 이름(raceId, sellerId, canceledAt) 그대로 보냈는지 본문을 직접 읽어 확인한다
		String payload = outboxPayloads(KafkaTopics.ORDER_RACE_CANCEL).getFirst();
		int sentRaceId = JsonPath.read(payload, "$.raceId");
		int sentSellerId = JsonPath.read(payload, "$.sellerId");
		String sentCanceledAt = JsonPath.read(payload, "$.canceledAt");
		assertThat(sentRaceId).isEqualTo(onSale.raceId());
		assertThat(sentSellerId).isEqualTo(SELLER_ID);
		assertThat(LocalDateTime.parse(sentCanceledAt)).isEqualTo(canceledAt);
	}

	@Test
	@DisplayName("판매 1석·결제 대기 2석인 대회를 취소하면 결제 대기 2석만 RACE_CANCELED로 해제되어 결제 대기 수가 0이 되고 판매 1석은 남는다")
	void releaseHeldSeats() throws Exception {
		RegisteredRace onSale = registerOnSale(race().build());
		holdSeat(onSale.courseId(), 1);
		sellSeat(1);
		holdSeat(onSale.courseId(), 2);
		holdSeat(onSale.courseId(), 3);
		assertThat(findCourse(onSale.courseId()).getIndividualReserved()).isEqualTo(2);

		cancelRace(onSale.raceId(), SELLER_MEMBER_ID).andExpect(status().isOk());

		for (int participationId : List.of(2, 3)) {
			ParticipationHold released = findHold(participationId);
			assertThat(released.getStatus()).isEqualTo(HoldStatus.RELEASED);
			assertThat(released.getReleaseReason()).isEqualTo(HoldReleaseReason.RACE_CANCELED);
			assertThat(released.getReleasedAt()).isNotNull();
		}
		ParticipationHold sold = findHold(1);
		assertThat(sold.getStatus()).isEqualTo(HoldStatus.SOLD);
		assertThat(sold.getReleaseReason()).isNull();

		RaceCourse tenKm = findCourse(onSale.courseId());
		assertThat(tenKm.getIndividualReserved()).isZero();
		assertThat(tenKm.getIndividualSold()).isEqualTo(1);
		assertThat(tenKm.individualRemaining()).isEqualTo(99);
	}

	@Test
	@DisplayName("10K 결제 대기 2석·하프 1석인 대회를 취소하면 두 종목 모두 결제 대기 수가 0이 되고, 다른 대회의 결제 대기 1석은 그대로다")
	void releaseHeldSeatsOfEveryCourse() throws Exception {
		RegisteredRace target = registerOnSale(race()
			.course(course(), course().name("하프").type(CourseType.HALF).distanceKm("21.1").timeLimitMinutes(180))
			.build());
		RegisteredRace other = registerOnSale(race().title("2026 부산 바다 런").build());
		int tenKm = target.courseIds().get(0);
		int half = target.courseIds().get(1);
		holdSeat(tenKm, 1);
		holdSeat(tenKm, 2);
		holdSeat(half, 3);
		holdSeat(other.courseId(), 4);

		cancelRace(target.raceId(), SELLER_MEMBER_ID).andExpect(status().isOk());

		assertThat(List.of(1, 2, 3)).allSatisfy(participationId ->
			assertThat(findHold(participationId).getStatus()).isEqualTo(HoldStatus.RELEASED));
		assertThat(findCourse(tenKm).getIndividualReserved()).isZero();
		assertThat(findCourse(half).getIndividualReserved()).isZero();
		assertThat(findHold(4).getStatus()).isEqualTo(HoldStatus.HELD);
		assertThat(findCourse(other.courseId()).getIndividualReserved()).isEqualTo(1);
	}

	@Test
	@DisplayName("개인 예매 오픈이 이틀 뒤라 판매 예정인 대회도 취소되고 order.race.cancel 메시지를 보낸다")
	void cancelScheduledRace() throws Exception {
		RegisteredRace scheduled = register(race()
			.individualOpenAt(LocalDateTime.now().withNano(0).plusDays(2))
			.build());
		assertThat(findRace(scheduled.raceId()).getSaleStatus()).isEqualTo(RaceSaleStatus.SCHEDULED);

		cancelRace(scheduled.raceId(), SELLER_MEMBER_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.saleStatus").value("CANCELED"));

		assertThat(findRace(scheduled.raceId()).getSaleStatus()).isEqualTo(RaceSaleStatus.CANCELED);
		assertThat(cancelEvents()).extracting(RaceCancelEvent::raceId).containsExactly(scheduled.raceId());
	}

	@Test
	@DisplayName("판매 중지 대회도 취소되고 order.race.cancel 메시지를 보낸다")
	void cancelStoppedRace() throws Exception {
		RegisteredRace stopped = registerOnSale(race().build());
		changeSaleStatus(stopped.raceId(), RaceSaleStatus.STOPPED);

		cancelRace(stopped.raceId(), SELLER_MEMBER_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.saleStatus").value("CANCELED"));

		assertThat(findRace(stopped.raceId()).getSaleStatus()).isEqualTo(RaceSaleStatus.CANCELED);
		assertThat(cancelEvents()).extracting(RaceCancelEvent::raceId).containsExactly(stopped.raceId());
	}

	@Test
	@DisplayName("대회 당일(대회 일시 오늘 0시)은 대회 시각이 지났어도 판매 종료 전이라 취소된다")
	void cancelOnRaceDay() throws Exception {
		LocalDateTime today = LocalDate.now().atStartOfDay();
		RegisteredRace raceDay = registerOnSale(race()
			.raceDate(today)
			.individualOpenAt(today.minusDays(2))
			.applicationCloseAt(today)
			.build());

		cancelRace(raceDay.raceId(), SELLER_MEMBER_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.saleStatus").value("CANCELED"));

		assertThat(findRace(raceDay.raceId()).getSaleStatus()).isEqualTo(RaceSaleStatus.CANCELED);
	}

	// ---------- 취소한 대회가 보이는 곳 ----------

	@Test
	@DisplayName("취소한 대회는 공개 목록과 연관검색어('서울')에서 빠지고, 취소하지 않은 대회는 그대로 나온다")
	void hiddenFromPublicSearch() throws Exception {
		RegisteredRace canceled = registerOnSale(race().build());
		RegisteredRace kept = registerOnSale(race().build());
		// 취소 전에는 둘 다 나온다 (빠졌는지 확인하는 아래 검사가 처음부터 비어 있어 통과하는 것을 막는다)
		searchRaces()
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.races[*].raceId").value(hasItem(canceled.raceId())));
		suggestRaces("서울")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.races[*].raceId").value(hasItem(canceled.raceId())))
			.andExpect(jsonPath("$.data.courses[*].raceId").value(hasItem(canceled.raceId())));

		cancelRace(canceled.raceId(), SELLER_MEMBER_ID).andExpect(status().isOk());

		searchRaces()
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.races[*].raceId").value(not(hasItem(canceled.raceId()))))
			.andExpect(jsonPath("$.data.races[*].raceId").value(hasItem(kept.raceId())));
		suggestRaces("서울")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.races[*].raceId").value(not(hasItem(canceled.raceId()))))
			.andExpect(jsonPath("$.data.races[*].raceId").value(hasItem(kept.raceId())))
			.andExpect(jsonPath("$.data.courses[*].raceId").value(not(hasItem(canceled.raceId()))))
			.andExpect(jsonPath("$.data.courses[*].raceId").value(hasItem(kept.raceId())));
	}

	@Test
	@DisplayName("취소한 대회의 상세는 헤더 없음·다른 판매자(9002)·판매자 아닌 회원(9999)에게 404이고 등록한 판매자(9001)만 CANCELED로 본다")
	void detailVisibleOnlyToOwner() throws Exception {
		RegisteredRace onSale = registerOnSale(race().build());
		getAs(racePath(onSale.raceId()), null).andExpect(status().isOk());

		cancelRace(onSale.raceId(), SELLER_MEMBER_ID).andExpect(status().isOk());

		getAs(racePath(onSale.raceId()), null)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("존재하지 않는 대회입니다."));
		getAs(racePath(onSale.raceId()), OTHER_SELLER_MEMBER_ID)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("존재하지 않는 대회입니다."));
		getAs(racePath(onSale.raceId()), NOT_SELLER_MEMBER_ID)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("존재하지 않는 대회입니다."));
		getAs(racePath(onSale.raceId()), SELLER_MEMBER_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("대회를 조회했습니다."))
			.andExpect(jsonPath("$.data.raceId").value(onSale.raceId()))
			.andExpect(jsonPath("$.data.saleStatus").value("CANCELED"));
	}

	@Test
	@DisplayName("취소한 대회는 판매자의 내 대회 목록에 CANCELED로 남고, 종목은 정원 100·판매 1석·결제 대기 0석으로 보인다")
	void remainsInMyRaces() throws Exception {
		RegisteredRace onSale = registerOnSale(race().build());
		holdSeat(onSale.courseId(), 1);
		sellSeat(1);
		holdSeat(onSale.courseId(), 2);

		cancelRace(onSale.raceId(), SELLER_MEMBER_ID).andExpect(status().isOk());

		getAs(RACES_PATH + "/mine", SELLER_MEMBER_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].raceId").value(onSale.raceId()))
			.andExpect(jsonPath("$.data[0].saleStatus").value("CANCELED"))
			.andExpect(jsonPath("$.data[0].courses[0].quota").value(100))
			.andExpect(jsonPath("$.data[0].courses[0].reserved").value(0))
			.andExpect(jsonPath("$.data[0].courses[0].sold").value(1));
	}

	// ---------- 취소한 대회에 할 수 없는 일 ----------

	@Test
	@DisplayName("취소한 대회의 종목에 자리를 확보하면 409 '접수 기간이 아닙니다.'이고 자리도 결제 대기 수도 생기지 않는다")
	void rejectHoldAfterCancel() throws Exception {
		RegisteredRace onSale = registerOnSale(race().build());
		cancelRace(onSale.raceId(), SELLER_MEMBER_ID).andExpect(status().isOk());

		hold(onSale.courseId(), 1)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("접수 기간이 아닙니다."));

		assertThat(participationHoldRepository.findByParticipationId(1)).isEmpty();
		assertThat(findCourse(onSale.courseId()).getIndividualReserved()).isZero();
	}

	@Test
	@DisplayName("취소한 대회를 수정하면 409 '취소된 대회는 수정할 수 없습니다.'이고 대회명이 바뀌지 않는다")
	void rejectUpdateAfterCancel() throws Exception {
		RegisteredRace onSale = registerOnSale(race().build());
		cancelRace(onSale.raceId(), SELLER_MEMBER_ID).andExpect(status().isOk());

		putJson(racePath(onSale.raceId()), SELLER_MEMBER_ID,
			race().title("2026 서울 하프 마라톤 (변경)").course(course().courseId(onSale.courseId())).build())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("취소된 대회는 수정할 수 없습니다."));

		Race canceled = findRace(onSale.raceId());
		assertThat(canceled.getTitle()).isEqualTo(DEFAULT_TITLE);
		assertThat(canceled.getSaleStatus()).isEqualTo(RaceSaleStatus.CANCELED);
	}

	@Test
	@DisplayName("이미 취소한 대회를 다시 취소하면 409 '이미 취소된 대회입니다.'이고 취소 일시와 취소 메시지(1건)는 그대로다")
	void rejectCancelTwice() throws Exception {
		RegisteredRace onSale = registerOnSale(race().build());
		cancelRace(onSale.raceId(), SELLER_MEMBER_ID).andExpect(status().isOk());
		LocalDateTime firstCanceledAt = findRace(onSale.raceId()).getCanceledAt();

		cancelRace(onSale.raceId(), SELLER_MEMBER_ID)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 취소된 대회입니다."));

		assertThat(findRace(onSale.raceId()).getCanceledAt()).isEqualTo(firstCanceledAt);
		assertThat(outboxPayloads(KafkaTopics.ORDER_RACE_CANCEL)).hasSize(1);
	}

	@Test
	@DisplayName("취소 때 해제된 결제 대기 자리의 판매 요청은 200 EXPIRED '결제 기한이 지난 자리입니다.'이고 판매 수는 0이다")
	void rejectSellReleasedSeatAfterCancel() throws Exception {
		RegisteredRace onSale = registerOnSale(race().build());
		holdSeat(onSale.courseId(), 1);
		cancelRace(onSale.raceId(), SELLER_MEMBER_ID).andExpect(status().isOk());

		sell(1)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("결제 기한이 지난 자리입니다."))
			.andExpect(jsonPath("$.data.result").value("EXPIRED"));

		assertThat(findHold(1).getStatus()).isEqualTo(HoldStatus.RELEASED);
		assertThat(findCourse(onSale.courseId()).getIndividualSold()).isZero();
	}

	@Test
	@DisplayName("취소와 같은 순간 확보되어 해제되지 못한 결제 대기 자리도 대회가 취소됐으면 판매 요청이 EXPIRED이고 판매 수는 0이다")
	void rejectSellHeldSeatOfCanceledRace() throws Exception {
		RegisteredRace onSale = registerOnSale(race().build());
		holdSeat(onSale.courseId(), 1);
		// 취소의 자리 해제가 지나간 뒤에 확보가 커밋된 상황: 대회만 취소 상태로 바꾸고 자리는 결제 대기로 둔다
		LocalDateTime now = LocalDateTime.now();
		Integer updated = transactionTemplate.execute(transaction -> raceRepository.cancel(onSale.raceId(),
			List.of(RaceSaleStatus.ON_SALE), now.toLocalDate().atStartOfDay(), now));
		assertThat(updated).isEqualTo(1);

		sell(1)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("결제 기한이 지난 자리입니다."))
			.andExpect(jsonPath("$.data.result").value("EXPIRED"));

		assertThat(findHold(1).getStatus()).isEqualTo(HoldStatus.HELD);
		assertThat(findCourse(onSale.courseId()).getIndividualSold()).isZero();
	}

	// ---------- 취소할 수 없는 대회 ----------

	@Test
	@DisplayName("대회일이 이틀 지난 판매 중 대회는 상태 배치가 판매 종료로 바꾸기 전이라도 409 '판매가 끝난 대회는 취소할 수 없습니다.'이다")
	void rejectAfterRaceDate() throws Exception {
		RegisteredRace finished = registerOnSale(pastRace());
		assertThat(findRace(finished.raceId()).getSaleStatus()).isEqualTo(RaceSaleStatus.ON_SALE);

		cancelRace(finished.raceId(), SELLER_MEMBER_ID)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("판매가 끝난 대회는 취소할 수 없습니다."));

		assertNotCanceled(finished.raceId());
	}

	@Test
	@DisplayName("상태 배치가 판매 종료(CLOSED)로 바꾼 대회를 취소하면 409 '판매가 끝난 대회는 취소할 수 없습니다.'이다")
	void rejectClosedRace() throws Exception {
		RegisteredRace finished = registerOnSale(pastRace());
		productFacade.refreshSaleStatuses();
		assertThat(findRace(finished.raceId()).getSaleStatus()).isEqualTo(RaceSaleStatus.CLOSED);

		cancelRace(finished.raceId(), SELLER_MEMBER_ID)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("판매가 끝난 대회는 취소할 수 없습니다."));

		Race closed = findRace(finished.raceId());
		assertThat(closed.getSaleStatus()).isEqualTo(RaceSaleStatus.CLOSED);
		assertThat(closed.getCanceledAt()).isNull();
		assertThat(outboxPayloads(KafkaTopics.ORDER_RACE_CANCEL)).isEmpty();
	}

	// ---------- 요청한 사람·대회 확인 ----------

	@Test
	@DisplayName("다른 판매자(회원 9002)가 취소하면 403 '본인이 등록한 대회만 처리할 수 있습니다.'이고 대회와 결제 대기 자리가 그대로다")
	void rejectOtherSeller() throws Exception {
		RegisteredRace onSale = registerOnSale(race().build());
		holdSeat(onSale.courseId(), 1);

		cancelRace(onSale.raceId(), OTHER_SELLER_MEMBER_ID)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("본인이 등록한 대회만 처리할 수 있습니다."));

		assertNotCanceled(onSale.raceId());
		assertThat(findHold(1).getStatus()).isEqualTo(HoldStatus.HELD);
		assertThat(findCourse(onSale.courseId()).getIndividualReserved()).isEqualTo(1);
	}

	@Test
	@DisplayName("판매자가 아닌 회원(9999)이 취소하면 403 '승인된 판매자만 대회를 등록·수정할 수 있습니다.'이다")
	void rejectNotSeller() throws Exception {
		RegisteredRace onSale = registerOnSale(race().build());

		cancelRace(onSale.raceId(), NOT_SELLER_MEMBER_ID)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("승인된 판매자만 대회를 등록·수정할 수 있습니다."));

		assertNotCanceled(onSale.raceId());
	}

	@Test
	@DisplayName("X-Member-Id 헤더 없이 취소하면 401 '로그인이 필요합니다.'이다")
	void rejectWithoutMemberHeader() throws Exception {
		RegisteredRace onSale = registerOnSale(race().build());

		cancelRace(onSale.raceId(), null)
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("로그인이 필요합니다."));

		assertNotCanceled(onSale.raceId());
	}

	@Test
	@DisplayName("없는 대회(ID 2147483647)를 취소하면 404 '존재하지 않는 대회입니다.'이다")
	void rejectUnknownRace() throws Exception {
		cancelRace(Integer.MAX_VALUE, SELLER_MEMBER_ID)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("존재하지 않는 대회입니다."));

		assertThat(outboxPayloads(KafkaTopics.ORDER_RACE_CANCEL)).isEmpty();
	}

	@Test
	@DisplayName("대회 ID가 숫자가 아니면(abc) 400 '요청 값의 형식이 올바르지 않습니다.'이다")
	void rejectInvalidRaceId() throws Exception {
		postAs(RACES_PATH + "/abc/cancel", SELLER_MEMBER_ID)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."));
	}

	// ---------- 동시 요청 ----------

	@Test
	@DisplayName("대회 취소 1건과 자리 확보 10건이 동시에 들어와도 결제 대기 수가 HELD 자리 수와 같고, 남은 HELD 자리는 판매되지 않는다")
	void cancelWhileHolding() throws Exception {
		RegisteredRace onSale = registerOnSale(race().build());
		int holdCount = 10;

		// 마지막 요청이 취소이고, 나머지는 참가 신청 5001~5010의 자리 확보다
		List<Integer> statusCodes = runConcurrently(holdCount + 1, index -> index == holdCount
			? cancelRace(onSale.raceId(), SELLER_MEMBER_ID).andReturn().getResponse().getStatus()
			: hold(onSale.courseId(), 5001 + index).andReturn().getResponse().getStatus());

		// 취소보다 먼저 확보한 자리는 200(held=true), 취소된 뒤 확보하려 한 자리는 409(접수 기간이 아님)다
		assertThat(statusCodes.get(holdCount)).isEqualTo(200);
		List<Integer> holdCodes = statusCodes.subList(0, holdCount);
		assertThat(holdCodes).allMatch(code -> code == 200 || code == 409);
		List<ParticipationHold> seats = participationHoldRepository.findAll();
		assertThat(seats).hasSize((int)holdCodes.stream().filter(code -> code == 200).count());
		assertThat(seats).allMatch(seat -> seat.getStatus() == HoldStatus.HELD
			|| seat.getReleaseReason() == HoldReleaseReason.RACE_CANCELED);
		// 취소가 해제하지 못한 자리(같은 순간 확보)가 남더라도 결제 대기 수는 HELD 자리 수와 같아야 한다
		List<ParticipationHold> stillHeld = seats.stream()
			.filter(seat -> seat.getStatus() == HoldStatus.HELD)
			.toList();
		assertThat(findCourse(onSale.courseId()).getIndividualReserved()).isEqualTo(stillHeld.size());
		// 남은 결제 대기 자리도 대회가 취소됐으므로 판매되지 않는다
		for (ParticipationHold held : stillHeld) {
			sell(held.getParticipationId())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.result").value("EXPIRED"));
		}
		assertThat(findCourse(onSale.courseId()).getIndividualSold()).isZero();
		assertThat(findRace(onSale.raceId()).getSaleStatus()).isEqualTo(RaceSaleStatus.CANCELED);
	}

	// ---------- 도우미 ----------

	private ResultActions cancelRace(int raceId, Integer memberId) throws Exception {
		return postAs(racePath(raceId) + "/cancel", memberId);
	}

	private String racePath(int raceId) {
		return RACES_PATH + "/" + raceId;
	}

	private ResultActions searchRaces() throws Exception {
		return getAs(RACES_PATH, null);
	}

	private ResultActions suggestRaces(String keyword) throws Exception {
		return mockMvc.perform(get(RACES_PATH + "/suggestions").param("keyword", keyword));
	}

	private void holdSeat(int raceCourseId, int participationId) throws Exception {
		hold(raceCourseId, participationId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.held").value(true));
	}

	private void sellSeat(int participationId) throws Exception {
		sell(participationId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.result").value("SOLD"));
	}

	// 대회일이 이틀 전인 대회 (개인 예매 오픈 5일 전, 접수 마감 3일 전). registerOnSale()로 만들면 판매 중이다
	private RaceSaveRequestDto pastRace() {
		LocalDateTime now = LocalDateTime.now().withNano(0);
		return race()
			.raceDate(now.minusDays(2))
			.individualOpenAt(now.minusDays(5))
			.applicationCloseAt(now.minusDays(3))
			.build();
	}

	private Race findRace(int raceId) {
		return raceRepository.findById(raceId).orElseThrow();
	}

	private RaceCourse findCourse(int raceCourseId) {
		return raceCourseRepository.findById(raceCourseId).orElseThrow();
	}

	private ParticipationHold findHold(int participationId) {
		return participationHoldRepository.findByParticipationId(participationId).orElseThrow();
	}

	private List<RaceCancelEvent> cancelEvents() {
		return outboxPayloads(KafkaTopics.ORDER_RACE_CANCEL).stream()
			.map(payload -> jsonConverter.fromJson(payload, RaceCancelEvent.class))
			.toList();
	}

	// 판매 중지 API가 아직 없어 판매 상태를 DB에서 직접 바꾼다
	private void changeSaleStatus(int raceId, RaceSaleStatus saleStatus) {
		Integer updated = transactionTemplate.execute(transaction -> entityManager
			.createQuery("update Race r set r.saleStatus = :saleStatus where r.id = :id")
			.setParameter("saleStatus", saleStatus)
			.setParameter("id", raceId)
			.executeUpdate());
		assertThat(updated).isEqualTo(1);
	}

	// 취소되지 않았는지: 판매 중 그대로, 취소 일시 없음, 취소 메시지 없음
	private void assertNotCanceled(int raceId) {
		Race notCanceled = findRace(raceId);
		assertThat(notCanceled.getSaleStatus()).isEqualTo(RaceSaleStatus.ON_SALE);
		assertThat(notCanceled.getCanceledAt()).isNull();
		assertThat(outboxPayloads(KafkaTopics.ORDER_RACE_CANCEL)).isEmpty();
	}
}
