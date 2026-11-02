package com.bukang.product.in;

import static com.bukang.product.support.RaceRequests.course;
import static com.bukang.product.support.RaceRequests.race;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.product.domain.CourseStatus;
import com.bukang.product.domain.HoldReleaseReason;
import com.bukang.product.domain.HoldStatus;
import com.bukang.product.domain.ParticipationHold;
import com.bukang.product.domain.RaceCourse;
import com.bukang.product.support.ProductIntegrationTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 주문 서비스가 부르는 자리 확보·판매·복구 내부 API(InternalV1HoldController)와 해제 메시지(product.hold.cancel) 처리를 확인한다.
 * 흐름: [결제하기] 때 끝 시각(expiresAt)을 담아 확보(결제 대기) → 결제하면 판매 → 결제를 취소하면 복구,
 * 결제하지 않고 끝 시각이 지나면 주문 서비스가 Kafka로 해제를 알린다. 결제 기한 연장은 없다.
 * 자리 행 상태와 종목 카운터(결제 대기·판매 수)가 함께 바뀌는지, 같은 요청이 다시 오거나 겹쳐도 한 번만 바뀌는지를 본다.
 * 테스트 프로파일은 Kafka 리스너가 꺼져 있으므로, 해제는 ProductKafkaConsumer.handleHoldCancel에 JSON 문자열을 직접 넘긴다.
 * 끝 시각이 지난 자리는 기다리지 않고 자리 행의 끝 시각을 1분 전으로 옮겨 만든다.
 */
class InternalV1HoldTest extends ProductIntegrationTest {
	private static final String HOLDS_PATH = "/internal/v1/product/holds";
	private static final long PARTICIPATION_ID = 1001;
	private static final long OTHER_PARTICIPATION_ID = 1002;
	// 확보한 적 없는 참가 신청
	private static final long UNKNOWN_PARTICIPATION_ID = 99_999;
	private static final long UNKNOWN_COURSE_ID = 999_999;
	// 기본 요청(race().build())의 개인 정원
	private static final int DEFAULT_QUOTA = 100;
	// 동시 판매·해제 테스트에서 해제 메시지를 보낸 쪽이 돌려주는 값
	private static final String RELEASE_SENT = "RELEASE_SENT";

	@Autowired
	private ProductKafkaConsumer productKafkaConsumer;

	@PersistenceContext
	private EntityManager entityManager;

	// ---------- 확보 ----------

	@Test
	@DisplayName("판매 중인 종목(개인 정원 100석)에 10분 뒤 끝나는 자리를 확보하면 held=true와 요청한 끝 시각을 돌려주고, "
		+ "남은 자리가 99석이 되며 자리 행은 결제 대기(HELD)다")
	void holdSeat() throws Exception {
		long courseId = registerOnSale(race().build()).courseId();
		LocalDateTime expiresAt = tenMinutesLater();

		ResultActions result = hold(courseId, PARTICIPATION_ID, expiresAt)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(200))
			.andExpect(jsonPath("$.message").value("자리를 확보했습니다."))
			.andExpect(jsonPath("$.data.held").value(true));

		assertThat(expiresAtOf(result)).isEqualTo(expiresAt);
		RaceCourse raceCourse = courseOf(courseId);
		assertThat(raceCourse.getIndividualReserved()).isEqualTo(1);
		assertThat(raceCourse.getIndividualSold()).isZero();
		assertThat(raceCourse.individualRemaining()).isEqualTo(DEFAULT_QUOTA - 1);
		assertThat(raceCourse.getStatus()).isEqualTo(CourseStatus.OPEN);
		ParticipationHold seat = holdOf(PARTICIPATION_ID);
		assertThat(seat.getStatus()).isEqualTo(HoldStatus.HELD);
		assertThat(seat.getExpiresAt()).isEqualTo(expiresAt);
		assertThat(seat.getCrewApplicationId()).isNull();
		assertThat(seat.getSoldAt()).isNull();
		assertThat(seat.getReleasedAt()).isNull();
		assertThat(seat.getReleaseReason()).isNull();
	}

	@Test
	@DisplayName("같은 참가 신청(1001)으로 끝 시각을 20분 뒤로 바꿔 다시 확보해도 처음 결과(held=true, 10분 뒤)를 돌려주고 "
		+ "자리는 1석만 줄어든다")
	void holdAgainWithSameParticipation() throws Exception {
		long courseId = registerOnSale(race().build()).courseId();
		LocalDateTime firstExpiresAt = tenMinutesLater();
		hold(courseId, PARTICIPATION_ID, firstExpiresAt).andExpect(status().isOk());

		ResultActions result = hold(courseId, PARTICIPATION_ID, firstExpiresAt.plusMinutes(10))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("자리를 확보했습니다."))
			.andExpect(jsonPath("$.data.held").value(true));

		assertThat(expiresAtOf(result)).isEqualTo(firstExpiresAt);
		RaceCourse raceCourse = courseOf(courseId);
		assertThat(raceCourse.getIndividualReserved()).isEqualTo(1);
		assertThat(raceCourse.individualRemaining()).isEqualTo(DEFAULT_QUOTA - 1);
		assertThat(participationHoldRepository.count()).isEqualTo(1);
		assertThat(holdOf(PARTICIPATION_ID).getExpiresAt()).isEqualTo(firstExpiresAt);
	}

	@Test
	@DisplayName("판매까지 끝난 참가 신청(1001)으로 다시 확보해도 처음 결과(held=true)를 돌려주고 결제 대기 0·판매 1 그대로다")
	void holdAgainAfterSold() throws Exception {
		long courseId = registerOnSale(race().build()).courseId();
		LocalDateTime expiresAt = tenMinutesLater();
		hold(courseId, PARTICIPATION_ID, expiresAt).andExpect(status().isOk());
		sell(PARTICIPATION_ID).andExpect(status().isOk());

		ResultActions result = hold(courseId, PARTICIPATION_ID, expiresAt.plusMinutes(5))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.held").value(true));

		assertThat(expiresAtOf(result)).isEqualTo(expiresAt);
		RaceCourse raceCourse = courseOf(courseId);
		assertThat(raceCourse.getIndividualReserved()).isZero();
		assertThat(raceCourse.getIndividualSold()).isEqualTo(1);
		assertThat(holdOf(PARTICIPATION_ID).getStatus()).isEqualTo(HoldStatus.SOLD);
	}

	@Test
	@DisplayName("개인 정원 1석 종목에서 1001이 확보한 뒤 1002가 확보하면 200 held=false(매진)이고 종목 상태는 SOLD_OUT이다")
	void holdSoldOut() throws Exception {
		long courseId = registerOnSale(race().course(course().quota(0, 1)).build()).courseId();
		hold(courseId, PARTICIPATION_ID).andExpect(status().isOk());

		hold(courseId, OTHER_PARTICIPATION_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(200))
			.andExpect(jsonPath("$.message").value("남은 자리가 없습니다."))
			.andExpect(jsonPath("$.data.held").value(false))
			.andExpect(jsonPath("$.data.expiresAt").doesNotExist());

		RaceCourse raceCourse = courseOf(courseId);
		assertThat(raceCourse.getIndividualReserved()).isEqualTo(1);
		assertThat(raceCourse.individualRemaining()).isZero();
		assertThat(raceCourse.getStatus()).isEqualTo(CourseStatus.SOLD_OUT);
		assertThat(participationHoldRepository.findByParticipationId(OTHER_PARTICIPATION_ID)).isEmpty();
	}

	@Test
	@DisplayName("끝 시각이 1분 지난 확보 요청은 400 '임시 확보 끝 시각이 이미 지났습니다.'이고 자리를 잡지 않는다")
	void rejectPastExpiresAt() throws Exception {
		long courseId = registerOnSale(race().build()).courseId();

		hold(courseId, PARTICIPATION_ID, LocalDateTime.now().minusMinutes(1))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.message").value("임시 확보 끝 시각이 이미 지났습니다."))
			.andExpect(jsonPath("$.data").doesNotExist());

		assertNothingHeld(courseId);
	}

	@Test
	@DisplayName("확보 요청 값이 잘못되면(끝 시각·종목 ID·참가 신청 ID 누락, 종목 ID 0, 참가 신청 ID -1, 날짜가 아닌 끝 시각) "
		+ "400이고 자리를 잡지 않는다")
	void rejectInvalidHoldRequest() throws Exception {
		long courseId = registerOnSale(race().build()).courseId();
		String expiresAt = tenMinutesLater().toString();

		holdWithBody("{\"raceCourseId\":%d,\"participationId\":%d}".formatted(courseId, PARTICIPATION_ID))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("임시 확보 끝 시각을 작성해주세요."));
		holdWithBody("{\"participationId\":%d,\"expiresAt\":\"%s\"}".formatted(PARTICIPATION_ID, expiresAt))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("종목 ID를 작성해주세요."));
		holdWithBody("{\"raceCourseId\":%d,\"expiresAt\":\"%s\"}".formatted(courseId, expiresAt))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("참가 신청 ID를 작성해주세요."));
		holdWithBody(holdJson(0, PARTICIPATION_ID, expiresAt))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("종목 ID는 0보다 커야 합니다."));
		holdWithBody(holdJson(courseId, -1, expiresAt))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("참가 신청 ID는 0보다 커야 합니다."));
		holdWithBody(holdJson(courseId, PARTICIPATION_ID, "내일 10시"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("요청 본문 형식이 올바르지 않습니다."));

		assertNothingHeld(courseId);
	}

	@Test
	@DisplayName("판매·복구 주소의 참가 신청 ID가 숫자가 아니면(abc) 400 '요청 값의 형식이 올바르지 않습니다.'")
	void rejectNonNumericParticipationId() throws Exception {
		mockMvc.perform(post(HOLDS_PATH + "/abc/sell"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."));
		mockMvc.perform(post(HOLDS_PATH + "/abc/restore"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."));
	}

	@Test
	@DisplayName("개인 예매가 이틀 뒤 열리는 판매 예정(SCHEDULED) 대회의 종목을 확보하면 409 '접수 기간이 아닙니다.'")
	void rejectBeforeIndividualOpen() throws Exception {
		LocalDateTime openAt = LocalDateTime.now().withNano(0).plusDays(2);
		RegisteredRace registered = register(race().individualOpenAt(openAt).build());
		assertThat(raceRepository.findById(registered.raceId()).orElseThrow().getSaleStatus())
			.isEqualTo(RaceSaleStatus.SCHEDULED);

		hold(registered.courseId(), PARTICIPATION_ID)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.message").value("접수 기간이 아닙니다."));

		assertNothingHeld(registered.courseId());
	}

	@Test
	@DisplayName("판매 시작일이 오늘이라 판매 중(ON_SALE)이어도, 개인 예매가 오늘 23:59에 열리면 그 전에는 확보가 409 '접수 기간이 아닙니다.'")
	void rejectOnSaleRaceBeforeIndividualOpen() throws Exception {
		LocalDateTime openAt = LocalDate.now().atTime(23, 59);
		RegisteredRace registered = registerOnSale(race().individualOpenAt(openAt).build());
		assertThat(raceRepository.findById(registered.raceId()).orElseThrow().getSaleStatus())
			.isEqualTo(RaceSaleStatus.ON_SALE);

		hold(registered.courseId(), PARTICIPATION_ID)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("접수 기간이 아닙니다."));

		assertNothingHeld(registered.courseId());
	}

	@Test
	@DisplayName("접수 마감이 1시간 지난 판매 중 대회의 종목을 확보하면 409 '접수 기간이 아닙니다.'")
	void rejectAfterApplicationClose() throws Exception {
		LocalDateTime now = LocalDateTime.now().withNano(0);
		RegisteredRace registered = registerOnSale(race()
			.individualOpenAt(now.minusHours(2))
			.applicationCloseAt(now.minusHours(1))
			.build());

		hold(registered.courseId(), PARTICIPATION_ID)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("접수 기간이 아닙니다."));

		assertNothingHeld(registered.courseId());
	}

	@Test
	@DisplayName("없는 종목(999999)을 확보하면 404 '존재하지 않는 종목입니다.'이고 자리 행을 만들지 않는다")
	void rejectUnknownCourse() throws Exception {
		hold(UNKNOWN_COURSE_ID, PARTICIPATION_ID)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.message").value("존재하지 않는 종목입니다."));

		assertThat(participationHoldRepository.count()).isZero();
	}

	@Test
	@DisplayName("해제된 참가 신청(1001)으로 다시 확보하면 409 '…(자리 상태: 해제)'이고 자리를 다시 잡지 않는다")
	void rejectHoldAfterRelease() throws Exception {
		long courseId = registerOnSale(race().build()).courseId();
		hold(courseId, PARTICIPATION_ID).andExpect(status().isOk());
		releaseByMessage(PARTICIPATION_ID);

		hold(courseId, PARTICIPATION_ID)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.message").value("이미 끝난 참가 신청이라 자리를 다시 확보할 수 없습니다. (자리 상태: 해제)"));

		assertThat(courseOf(courseId).getIndividualReserved()).isZero();
		assertThat(participationHoldRepository.count()).isEqualTo(1);
		assertThat(holdOf(PARTICIPATION_ID).getStatus()).isEqualTo(HoldStatus.RELEASED);
	}

	@Test
	@DisplayName("환불 복구된 참가 신청(1001)으로 다시 확보하면 409 '…(자리 상태: 환불 복구)'이고 자리를 다시 잡지 않는다")
	void rejectHoldAfterRestore() throws Exception {
		long courseId = registerOnSale(race().build()).courseId();
		hold(courseId, PARTICIPATION_ID).andExpect(status().isOk());
		sell(PARTICIPATION_ID).andExpect(status().isOk());
		restore(PARTICIPATION_ID).andExpect(status().isOk());

		hold(courseId, PARTICIPATION_ID)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 끝난 참가 신청이라 자리를 다시 확보할 수 없습니다. (자리 상태: 환불 복구)"));

		RaceCourse raceCourse = courseOf(courseId);
		assertThat(raceCourse.getIndividualReserved()).isZero();
		assertThat(raceCourse.getIndividualSold()).isZero();
		assertThat(holdOf(PARTICIPATION_ID).getStatus()).isEqualTo(HoldStatus.RESTORED);
	}

	// ---------- 판매 ----------

	@Test
	@DisplayName("결제 대기 자리(1001)를 판매하면 200 result=SOLD이고, 결제 대기 1이 판매 1로 옮겨 가며 남은 자리는 99석 그대로다")
	void sellSeat() throws Exception {
		long courseId = registerOnSale(race().build()).courseId();
		hold(courseId, PARTICIPATION_ID).andExpect(status().isOk());

		sell(PARTICIPATION_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(200))
			.andExpect(jsonPath("$.message").value("자리를 판매했습니다."))
			.andExpect(jsonPath("$.data.result").value("SOLD"));

		RaceCourse raceCourse = courseOf(courseId);
		assertThat(raceCourse.getIndividualReserved()).isZero();
		assertThat(raceCourse.getIndividualSold()).isEqualTo(1);
		assertThat(raceCourse.individualRemaining()).isEqualTo(DEFAULT_QUOTA - 1);
		ParticipationHold seat = holdOf(PARTICIPATION_ID);
		assertThat(seat.getStatus()).isEqualTo(HoldStatus.SOLD);
		assertThat(seat.getSoldAt()).isNotNull();
	}

	@Test
	@DisplayName("이미 판매한 자리(1001)는 끝 시각이 지난 뒤 다시 판매해도 SOLD이고 판매 수는 1 그대로다")
	void sellAgainAfterSold() throws Exception {
		long courseId = registerOnSale(race().build()).courseId();
		hold(courseId, PARTICIPATION_ID).andExpect(status().isOk());
		sell(PARTICIPATION_ID).andExpect(status().isOk());
		LocalDateTime soldAt = holdOf(PARTICIPATION_ID).getSoldAt();
		moveExpiresAtToPast(PARTICIPATION_ID);

		sell(PARTICIPATION_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("자리를 판매했습니다."))
			.andExpect(jsonPath("$.data.result").value("SOLD"));

		RaceCourse raceCourse = courseOf(courseId);
		assertThat(raceCourse.getIndividualReserved()).isZero();
		assertThat(raceCourse.getIndividualSold()).isEqualTo(1);
		assertThat(holdOf(PARTICIPATION_ID).getSoldAt()).isEqualTo(soldAt);
	}

	@Test
	@DisplayName("확보한 적 없는 참가 신청(99999)을 판매하면 200 result=EXPIRED '결제 기한이 지난 자리입니다.'")
	void sellUnknownParticipation() throws Exception {
		sell(UNKNOWN_PARTICIPATION_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(200))
			.andExpect(jsonPath("$.message").value("결제 기한이 지난 자리입니다."))
			.andExpect(jsonPath("$.data.result").value("EXPIRED"));

		assertThat(participationHoldRepository.count()).isZero();
	}

	@Test
	@DisplayName("해제 메시지로 해제된 자리(1001)를 판매하면 EXPIRED이고 판매 수는 0 그대로다")
	void sellReleasedSeat() throws Exception {
		long courseId = registerOnSale(race().build()).courseId();
		hold(courseId, PARTICIPATION_ID).andExpect(status().isOk());
		releaseByMessage(PARTICIPATION_ID);

		sell(PARTICIPATION_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("결제 기한이 지난 자리입니다."))
			.andExpect(jsonPath("$.data.result").value("EXPIRED"));

		RaceCourse raceCourse = courseOf(courseId);
		assertThat(raceCourse.getIndividualReserved()).isZero();
		assertThat(raceCourse.getIndividualSold()).isZero();
		ParticipationHold seat = holdOf(PARTICIPATION_ID);
		assertThat(seat.getStatus()).isEqualTo(HoldStatus.RELEASED);
		assertThat(seat.getSoldAt()).isNull();
	}

	@Test
	@DisplayName("끝 시각이 1분 지난 결제 대기 자리(1001)를 판매하면 EXPIRED이고 결제 대기로 남았다가, "
		+ "해제 메시지가 오면 정원으로 돌아간다")
	void sellExpiredSeat() throws Exception {
		long courseId = registerOnSale(race().build()).courseId();
		hold(courseId, PARTICIPATION_ID).andExpect(status().isOk());
		moveExpiresAtToPast(PARTICIPATION_ID);

		sell(PARTICIPATION_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("결제 기한이 지난 자리입니다."))
			.andExpect(jsonPath("$.data.result").value("EXPIRED"));

		RaceCourse expired = courseOf(courseId);
		assertThat(expired.getIndividualReserved()).isEqualTo(1);
		assertThat(expired.getIndividualSold()).isZero();
		assertThat(holdOf(PARTICIPATION_ID).getStatus()).isEqualTo(HoldStatus.HELD);

		releaseByMessage(PARTICIPATION_ID);

		RaceCourse released = courseOf(courseId);
		assertThat(released.getIndividualReserved()).isZero();
		assertThat(released.individualRemaining()).isEqualTo(DEFAULT_QUOTA);
		assertThat(holdOf(PARTICIPATION_ID).getStatus()).isEqualTo(HoldStatus.RELEASED);
	}

	// ---------- 복구 ----------

	@Test
	@DisplayName("개인 정원 1석 종목에서 판매한 자리(1001)를 복구하면 restored=true이고, 판매 수가 0이 되며 "
		+ "종목 상태가 SOLD_OUT에서 OPEN으로 돌아간다")
	void restoreSoldSeat() throws Exception {
		long courseId = registerOnSale(race().course(course().quota(0, 1)).build()).courseId();
		hold(courseId, PARTICIPATION_ID).andExpect(status().isOk());
		sell(PARTICIPATION_ID).andExpect(status().isOk());
		assertThat(courseOf(courseId).getStatus()).isEqualTo(CourseStatus.SOLD_OUT);

		restore(PARTICIPATION_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(200))
			.andExpect(jsonPath("$.message").value("자리를 정원으로 되돌렸습니다."))
			.andExpect(jsonPath("$.data.restored").value(true));

		RaceCourse raceCourse = courseOf(courseId);
		assertThat(raceCourse.getIndividualSold()).isZero();
		assertThat(raceCourse.getIndividualReserved()).isZero();
		assertThat(raceCourse.individualRemaining()).isEqualTo(1);
		assertThat(raceCourse.getStatus()).isEqualTo(CourseStatus.OPEN);
		assertThat(holdOf(PARTICIPATION_ID).getStatus()).isEqualTo(HoldStatus.RESTORED);
	}

	@Test
	@DisplayName("복구한 자리(1001)를 다시 복구하면 restored=false '되돌릴 판매 자리가 없습니다.'이고 판매 수는 0 그대로다")
	void restoreAgain() throws Exception {
		long courseId = registerOnSale(race().build()).courseId();
		hold(courseId, PARTICIPATION_ID).andExpect(status().isOk());
		sell(PARTICIPATION_ID).andExpect(status().isOk());
		restore(PARTICIPATION_ID).andExpect(status().isOk());

		restore(PARTICIPATION_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("되돌릴 판매 자리가 없습니다."))
			.andExpect(jsonPath("$.data.restored").value(false));

		RaceCourse raceCourse = courseOf(courseId);
		assertThat(raceCourse.getIndividualSold()).isZero();
		assertThat(raceCourse.individualRemaining()).isEqualTo(DEFAULT_QUOTA);
		assertThat(holdOf(PARTICIPATION_ID).getStatus()).isEqualTo(HoldStatus.RESTORED);
	}

	@Test
	@DisplayName("판매한 자리(1001)의 복구 메시지(product.hold.restore)가 두 번 와도 판매 수는 한 번만 줄고(1 → 0), "
		+ "남은 자리는 정원 그대로이며 자리는 RESTORED다")
	void restoreMessageTwiceIsIdempotent() throws Exception {
		long courseId = registerOnSale(race().build()).courseId();
		hold(courseId, PARTICIPATION_ID).andExpect(status().isOk());
		sell(PARTICIPATION_ID).andExpect(status().isOk());
		assertThat(courseOf(courseId).getIndividualSold()).isEqualTo(1);

		restoreByMessage(PARTICIPATION_ID);
		restoreByMessage(PARTICIPATION_ID);

		RaceCourse raceCourse = courseOf(courseId);
		assertThat(raceCourse.getIndividualSold()).isZero();
		assertThat(raceCourse.getIndividualReserved()).isZero();
		assertThat(raceCourse.individualRemaining()).isEqualTo(DEFAULT_QUOTA);
		assertThat(holdOf(PARTICIPATION_ID).getStatus()).isEqualTo(HoldStatus.RESTORED);
	}

	@Test
	@DisplayName("판매하지 않은 결제 대기 자리(1001)에 복구 메시지가 와도 예외 없이 건너뛰고 결제 대기 1석 그대로다")
	void restoreMessageIgnoresHeldSeat() throws Exception {
		long courseId = registerOnSale(race().build()).courseId();
		hold(courseId, PARTICIPATION_ID).andExpect(status().isOk());

		restoreByMessage(PARTICIPATION_ID);

		assertThat(courseOf(courseId).getIndividualReserved()).isEqualTo(1);
		assertThat(courseOf(courseId).getIndividualSold()).isZero();
		assertThat(holdOf(PARTICIPATION_ID).getStatus()).isEqualTo(HoldStatus.HELD);
	}

	@Test
	@DisplayName("판매하지 않은 결제 대기 자리(1001)와 확보한 적 없는 신청(99999)을 복구하면 restored=false이고 카운터는 그대로다")
	void restoreNotSoldSeat() throws Exception {
		long courseId = registerOnSale(race().build()).courseId();
		hold(courseId, PARTICIPATION_ID).andExpect(status().isOk());

		restore(PARTICIPATION_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("되돌릴 판매 자리가 없습니다."))
			.andExpect(jsonPath("$.data.restored").value(false));
		restore(UNKNOWN_PARTICIPATION_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.restored").value(false));

		RaceCourse raceCourse = courseOf(courseId);
		assertThat(raceCourse.getIndividualReserved()).isEqualTo(1);
		assertThat(raceCourse.getIndividualSold()).isZero();
		assertThat(holdOf(PARTICIPATION_ID).getStatus()).isEqualTo(HoldStatus.HELD);
	}

	// ---------- 해제 (Kafka product.hold.cancel) ----------

	@Test
	@DisplayName("개인 정원 1석 종목의 결제 대기 자리(1001)에 해제 메시지가 오면 결제 대기가 0이 되고, 자리 행은 RELEASED·"
		+ "사유 EXPIRED·해제 일시가 남으며 종목은 다시 OPEN이다")
	void releaseByHoldCancelMessage() throws Exception {
		long courseId = registerOnSale(race().course(course().quota(0, 1)).build()).courseId();
		hold(courseId, PARTICIPATION_ID).andExpect(status().isOk());
		assertThat(courseOf(courseId).getStatus()).isEqualTo(CourseStatus.SOLD_OUT);

		releaseByMessage(PARTICIPATION_ID);

		RaceCourse raceCourse = courseOf(courseId);
		assertThat(raceCourse.getIndividualReserved()).isZero();
		assertThat(raceCourse.getIndividualSold()).isZero();
		assertThat(raceCourse.individualRemaining()).isEqualTo(1);
		assertThat(raceCourse.getStatus()).isEqualTo(CourseStatus.OPEN);
		ParticipationHold seat = holdOf(PARTICIPATION_ID);
		assertThat(seat.getStatus()).isEqualTo(HoldStatus.RELEASED);
		assertThat(seat.getReleaseReason()).isEqualTo(HoldReleaseReason.EXPIRED);
		assertThat(seat.getReleasedAt()).isNotNull();
		assertThat(seat.getSoldAt()).isNull();
	}

	@Test
	@DisplayName("같은 해제 메시지(1001)가 두 번 와도 결제 대기는 한 번만 줄고(1 → 0), 해제 일시도 처음 그대로다")
	void releaseTwiceIsIdempotent() throws Exception {
		long courseId = registerOnSale(race().build()).courseId();
		hold(courseId, PARTICIPATION_ID).andExpect(status().isOk());
		releaseByMessage(PARTICIPATION_ID);
		LocalDateTime firstReleasedAt = holdOf(PARTICIPATION_ID).getReleasedAt();

		releaseByMessage(PARTICIPATION_ID);

		RaceCourse raceCourse = courseOf(courseId);
		assertThat(raceCourse.getIndividualReserved()).isZero();
		assertThat(raceCourse.individualRemaining()).isEqualTo(DEFAULT_QUOTA);
		ParticipationHold seat = holdOf(PARTICIPATION_ID);
		assertThat(seat.getStatus()).isEqualTo(HoldStatus.RELEASED);
		assertThat(seat.getReleasedAt()).isEqualTo(firstReleasedAt);
	}

	@Test
	@DisplayName("판매한 자리(1001)에 해제 메시지가 늦게 와도 자리는 SOLD 그대로이고 판매 수 1이 유지된다")
	void releaseIgnoresSoldSeat() throws Exception {
		long courseId = registerOnSale(race().build()).courseId();
		hold(courseId, PARTICIPATION_ID).andExpect(status().isOk());
		sell(PARTICIPATION_ID).andExpect(status().isOk());

		releaseByMessage(PARTICIPATION_ID);

		RaceCourse raceCourse = courseOf(courseId);
		assertThat(raceCourse.getIndividualReserved()).isZero();
		assertThat(raceCourse.getIndividualSold()).isEqualTo(1);
		assertThat(raceCourse.individualRemaining()).isEqualTo(DEFAULT_QUOTA - 1);
		ParticipationHold seat = holdOf(PARTICIPATION_ID);
		assertThat(seat.getStatus()).isEqualTo(HoldStatus.SOLD);
		assertThat(seat.getReleasedAt()).isNull();
		assertThat(seat.getReleaseReason()).isNull();
	}

	@Test
	@DisplayName("확보한 적 없는 참가 신청(99999)의 해제 메시지는 예외 없이 건너뛰고, 다른 신청(1001)의 결제 대기 자리는 그대로다")
	void releaseUnknownParticipation() throws Exception {
		long courseId = registerOnSale(race().build()).courseId();
		hold(courseId, PARTICIPATION_ID).andExpect(status().isOk());

		assertThatCode(() -> releaseByMessage(UNKNOWN_PARTICIPATION_ID)).doesNotThrowAnyException();

		assertThat(courseOf(courseId).getIndividualReserved()).isEqualTo(1);
		assertThat(participationHoldRepository.count()).isEqualTo(1);
		assertThat(holdOf(PARTICIPATION_ID).getStatus()).isEqualTo(HoldStatus.HELD);
	}

	// ---------- 동시 요청 ----------

	@Test
	@DisplayName("개인 정원 3석에 20명이 동시에 확보하면 3명만 확보되고, 결제 대기 카운터와 HELD 자리 행 수가 모두 3이다")
	void holdConcurrentlyOverQuota() throws Exception {
		long courseId = registerOnSale(race().course(course().quota(0, 3)).build()).courseId();

		List<Boolean> results = runConcurrently(20, index ->
			heldOf(hold(courseId, 2000 + index).andExpect(status().isOk())));

		assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(3);
		RaceCourse raceCourse = courseOf(courseId);
		assertThat(raceCourse.getIndividualReserved()).isEqualTo(3);
		assertThat(raceCourse.individualRemaining()).isZero();
		assertThat(raceCourse.getStatus()).isEqualTo(CourseStatus.SOLD_OUT);
		assertThat(countHolds(HoldStatus.HELD)).isEqualTo(raceCourse.getIndividualReserved());
		assertThat(participationHoldRepository.count()).isEqualTo(3);
	}

	@Test
	@DisplayName("같은 참가 신청(3001)으로 10번 동시에 확보해도 모두 held=true이고, 자리는 1석만 줄며 자리 행도 1개다")
	void holdSameParticipationConcurrently() throws Exception {
		long courseId = registerOnSale(race().build()).courseId();
		LocalDateTime expiresAt = tenMinutesLater();

		List<Boolean> results = runConcurrently(10, index ->
			heldOf(hold(courseId, 3001, expiresAt).andExpect(status().isOk())));

		assertThat(results).hasSize(10).containsOnly(true);
		RaceCourse raceCourse = courseOf(courseId);
		assertThat(raceCourse.getIndividualReserved()).isEqualTo(1);
		assertThat(raceCourse.individualRemaining()).isEqualTo(DEFAULT_QUOTA - 1);
		assertThat(participationHoldRepository.count()).isEqualTo(1);
		assertThat(holdOf(3001).getExpiresAt()).isEqualTo(expiresAt);
	}

	@Test
	@DisplayName("결제 대기 자리 10석마다 판매와 해제 메시지가 동시에 와도 한쪽만 반영되어, 결제 대기 + 판매 수가 "
		+ "HELD·SOLD 자리 행 수와 같다")
	void sellAndReleaseConcurrently() throws Exception {
		long courseId = registerOnSale(race().build()).courseId();
		int seatCount = 10;
		int firstParticipationId = 4000;
		for (int i = 0; i < seatCount; i++) {
			hold(courseId, firstParticipationId + i).andExpect(status().isOk());
		}

		// 짝수 번째는 판매, 홀수 번째는 같은 자리의 해제 메시지 (자리 i = 요청 2i, 2i + 1)
		List<String> results = runConcurrently(seatCount * 2, index -> {
			long participationId = firstParticipationId + index / 2;
			if (index % 2 == 0) {
				return sellResultOf(sell(participationId).andExpect(status().isOk()));
			}
			releaseByMessage(participationId);
			return RELEASE_SENT;
		});

		int soldRows = countHolds(HoldStatus.SOLD);
		int heldRows = countHolds(HoldStatus.HELD);
		assertThat(soldRows + countHolds(HoldStatus.RELEASED)).isEqualTo(seatCount);
		RaceCourse raceCourse = courseOf(courseId);
		assertThat(raceCourse.getIndividualReserved()).isEqualTo(heldRows).isZero();
		assertThat(raceCourse.getIndividualSold()).isEqualTo(soldRows);
		assertThat(raceCourse.getIndividualReserved() + raceCourse.getIndividualSold()).isEqualTo(heldRows + soldRows);
		assertThat(raceCourse.individualRemaining()).isEqualTo(DEFAULT_QUOTA - soldRows);
		// 판매 응답이 SOLD인 자리만 판매로 남고, EXPIRED인 자리는 해제되어 있다
		for (int i = 0; i < seatCount; i++) {
			HoldStatus expected = "SOLD".equals(results.get(i * 2)) ? HoldStatus.SOLD : HoldStatus.RELEASED;
			assertThat(holdOf(firstParticipationId + i).getStatus()).isEqualTo(expected);
		}
	}

	// ---------- 도우미 ----------

	private static LocalDateTime tenMinutesLater() {
		return LocalDateTime.now().plusMinutes(10).withNano(0);
	}

	private String holdJson(long raceCourseId, long participationId, String expiresAt) {
		return "{\"raceCourseId\":%d,\"participationId\":%d,\"expiresAt\":\"%s\"}"
			.formatted(raceCourseId, participationId, expiresAt);
	}

	// 기본 hold()로 만들 수 없는 요청 본문(칸 누락, 날짜가 아닌 값)을 그대로 보낸다
	private ResultActions holdWithBody(String body) throws Exception {
		return mockMvc.perform(post(HOLDS_PATH)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	// 주문 서비스가 product.hold.cancel로 보내는 본문 그대로 리스너 메서드에 넘긴다
	private void releaseByMessage(long participationId) {
		productKafkaConsumer.handleHoldCancel("{\"participationId\": %d}".formatted(participationId));
	}

	// 주문 서비스가 product.hold.restore로 보내는 본문 그대로 리스너 메서드에 넘긴다
	private void restoreByMessage(long participationId) {
		productKafkaConsumer.handleHoldRestore("{\"participationId\":%d}".formatted(participationId));
	}

	// 끝 시각이 지난 것처럼 자리 행의 끝 시각을 1분 전으로 옮긴다 (시간이 지나기를 기다리지 않으려고)
	private void moveExpiresAtToPast(long participationId) {
		transactionTemplate.executeWithoutResult(transaction -> entityManager
			.createQuery("update ParticipationHold h set h.expiresAt = :expiresAt "
				+ "where h.participationId = :participationId")
			.setParameter("expiresAt", LocalDateTime.now().minusMinutes(1))
			.setParameter("participationId", participationId)
			.executeUpdate());
	}

	private String bodyOf(ResultActions result) throws Exception {
		return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	private boolean heldOf(ResultActions result) throws Exception {
		return JsonPath.read(bodyOf(result), "$.data.held");
	}

	private String sellResultOf(ResultActions result) throws Exception {
		return JsonPath.read(bodyOf(result), "$.data.result");
	}

	// 응답의 끝 시각 (초가 0이어도 비교할 수 있게 문자열이 아니라 시각으로 바꿔 비교한다)
	private LocalDateTime expiresAtOf(ResultActions result) throws Exception {
		String expiresAt = JsonPath.read(bodyOf(result), "$.data.expiresAt");
		return LocalDateTime.parse(expiresAt);
	}

	private RaceCourse courseOf(long raceCourseId) {
		return raceCourseRepository.findById(raceCourseId).orElseThrow();
	}

	private ParticipationHold holdOf(long participationId) {
		return participationHoldRepository.findByParticipationId(participationId).orElseThrow();
	}

	private int countHolds(HoldStatus holdStatus) {
		return Math.toIntExact(participationHoldRepository.findAll().stream()
			.filter(seat -> seat.getStatus() == holdStatus)
			.count());
	}

	private void assertNothingHeld(long raceCourseId) {
		assertThat(courseOf(raceCourseId).getIndividualReserved()).isZero();
		assertThat(participationHoldRepository.count()).isZero();
	}
}
