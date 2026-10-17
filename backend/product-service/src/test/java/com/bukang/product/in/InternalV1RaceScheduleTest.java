package com.bukang.product.in;

import static com.bukang.product.support.RaceRequests.race;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.product.support.ProductIntegrationTest;
import com.bukang.product.support.RaceRequests.RaceBuilder;

/**
 * 정산 서비스가 부르는 대상 대회 조회 내부 API(InternalV1RaceScheduleController)를 확인한다 (PRO-230).
 * - 환불 마감일 · 대회 날짜가 from ~ to(양 끝 포함)인 대회만, 마감일(대회 일시) · 대회 ID 순으로 돌려준다
 * - 대회 일시는 시각이 있으므로 끝 날 23:59도 넣고 다음 날 0시는 뺀다
 * - 취소된 대회는 돌려주지 않는다
 * - 잘못된 기간(시작일 > 종료일, 31일 초과, 누락, 형식 오류)은 400이다
 * 대회는 판매 예정(개인 예매가 이틀 뒤 열림)으로 등록하고, 날짜는 접수 마감(10일 뒤)보다 늦은 20일 뒤부터 쓴다.
 */
class InternalV1RaceScheduleTest extends ProductIntegrationTest {
	private static final String BY_REFUND_DEADLINE_PATH = "/internal/v1/product/races/by-refund-deadline";
	private static final String BY_RACE_DATE_PATH = "/internal/v1/product/races/by-race-date";

	// 조회 기간 시작일. 기본 요청의 접수 마감(10일 뒤)보다 늦어야 대회를 등록할 수 있다
	private final LocalDate from = LocalDate.now().plusDays(20);
	// 정산 배치처럼 7일 기간으로 묻는다
	private final LocalDate to = from.plusDays(6);

	// ---------- 환불 마감일 ----------

	@Test
	@DisplayName("환불 마감일 기간 [D, D+6]으로 물으면 마감일이 D · D+6인 대회만 마감일 순으로 오고, D-1 · D+7은 빠진다")
	void findByRefundDeadlineWithinPeriod() throws Exception {
		registerRace("마감 전날 대회", from.minusDays(1));
		int first = registerRace("마감 첫날 대회", from);
		int last = registerRace("마감 끝날 대회", to);
		registerRace("마감 다음날 대회", to.plusDays(1));

		findByRefundDeadline(from, to)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(200))
			.andExpect(jsonPath("$.message").value("정산 대상 대회를 조회했습니다."))
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].raceId").value(first))
			.andExpect(jsonPath("$.data[0].raceName").value("마감 첫날 대회"))
			.andExpect(jsonPath("$.data[0].refundDeadline").value(from.toString()))
			.andExpect(jsonPath("$.data[0].raceDate").value(json(raceDateOf(from))))
			.andExpect(jsonPath("$.data[1].raceId").value(last))
			.andExpect(jsonPath("$.data[1].refundDeadline").value(to.toString()));
	}

	@Test
	@DisplayName("환불 마감일이 기간 안이어도 취소된 대회는 오지 않는다")
	void findByRefundDeadlineExcludesCanceledRace() throws Exception {
		int canceled = registerRace("취소된 대회", from);
		int open = registerRace("판매 예정 대회", from);
		postAs("/api/v1/product/races/" + canceled + "/cancel", SELLER_MEMBER_ID).andExpect(status().isOk());

		findByRefundDeadline(from, to)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].raceId").value(open));
	}

	@Test
	@DisplayName("기간 안에 대회가 없으면 200과 빈 목록이다")
	void findByRefundDeadlineEmpty() throws Exception {
		findByRefundDeadline(from, to)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	// ---------- 대회 날짜 ----------

	@Test
	@DisplayName("대회 날짜 기간 [D, D+6]으로 물으면 D 0시 · D+6 23:59 대회는 오고, D-1 23:59 · D+7 0시 대회는 빠진다")
	void findByRaceDateWithinPeriod() throws Exception {
		registerRaceOn("전날 밤 대회", from.minusDays(1).atTime(23, 59));
		int first = registerRaceOn("첫날 0시 대회", from.atStartOfDay());
		int last = registerRaceOn("끝날 밤 대회", to.atTime(23, 59));
		registerRaceOn("다음날 0시 대회", to.plusDays(1).atStartOfDay());

		findByRaceDate(from, to)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("정산 대상 대회를 조회했습니다."))
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].raceId").value(first))
			.andExpect(jsonPath("$.data[0].raceName").value("첫날 0시 대회"))
			.andExpect(jsonPath("$.data[0].raceDate").value(json(from.atStartOfDay())))
			.andExpect(jsonPath("$.data[0].refundDeadline").value(from.minusDays(1).toString()))
			.andExpect(jsonPath("$.data[1].raceId").value(last));
	}

	@Test
	@DisplayName("대회 날짜가 기간 안이어도 취소된 대회는 오지 않는다")
	void findByRaceDateExcludesCanceledRace() throws Exception {
		int canceled = registerRaceOn("취소된 대회", from.atTime(8, 0));
		int open = registerRaceOn("판매 예정 대회", from.atTime(8, 0));
		postAs("/api/v1/product/races/" + canceled + "/cancel", SELLER_MEMBER_ID).andExpect(status().isOk());

		findByRaceDate(from, to)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].raceId").value(open));
	}

	// ---------- 잘못된 기간 ----------

	@Test
	@DisplayName("시작일이 종료일보다 늦으면 400 '조회 시작일이 종료일보다 늦습니다.'이다")
	void fromAfterTo() throws Exception {
		findByRefundDeadline(to, from)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("조회 시작일이 종료일보다 늦습니다."));
		findByRaceDate(to, from)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("조회 시작일이 종료일보다 늦습니다."));
	}

	@Test
	@DisplayName("32일을 물으면 400 '대회는 한 번에 31일까지 조회할 수 있습니다.'이다")
	void periodTooLong() throws Exception {
		findByRefundDeadline(from, from.plusDays(31))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("대회는 한 번에 31일까지 조회할 수 있습니다."));
		findByRaceDate(from, from.plusDays(31))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("대회는 한 번에 31일까지 조회할 수 있습니다."));
	}

	@Test
	@DisplayName("from이 없으면 400 '필수 요청 값(from)이 없습니다.'이다")
	void missingFrom() throws Exception {
		mockMvc.perform(get(BY_REFUND_DEADLINE_PATH).param("to", to.toString()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("필수 요청 값(from)이 없습니다."));
	}

	@Test
	@DisplayName("날짜 형식이 아니면 400 '요청 값의 형식이 올바르지 않습니다.'이다")
	void invalidDateFormat() throws Exception {
		mockMvc.perform(get(BY_RACE_DATE_PATH).param("from", "2026/10/25").param("to", to.toString()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."));
	}

	// ---------- 도우미 ----------

	// 환불 마감일이 refundDeadline이고, 대회는 그날 08:00인 대회를 등록한다
	private int registerRace(String title, LocalDate refundDeadline) throws Exception {
		return register(scheduled(title)
			.raceDate(raceDateOf(refundDeadline))
			.refundDeadline(refundDeadline)
			.build()).raceId();
	}

	// 대회 일시가 raceDate이고, 환불 마감일은 그 전날인 대회를 등록한다
	private int registerRaceOn(String title, LocalDateTime raceDate) throws Exception {
		return register(scheduled(title)
			.raceDate(raceDate)
			.refundDeadline(raceDate.toLocalDate().minusDays(1))
			.build()).raceId();
	}

	// 판매 예정 대회 요청: 개인 예매는 이틀 뒤 열린다 (판매 시작일은 오늘로부터 이틀 뒤부터)
	private static RaceBuilder scheduled(String title) {
		RaceBuilder builder = race().title(title);
		return builder.individualOpenAt(builder.now().plusDays(2));
	}

	private static LocalDateTime raceDateOf(LocalDate date) {
		return date.atTime(8, 0);
	}

	// 응답 JSON의 일시 형식 (초까지 쓴다. LocalDateTime.toString()은 0초를 생략한다)
	private static String json(LocalDateTime dateTime) {
		return dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
	}

	private ResultActions findByRefundDeadline(LocalDate fromDate, LocalDate toDate) throws Exception {
		return mockMvc.perform(get(BY_REFUND_DEADLINE_PATH)
			.param("from", fromDate.toString())
			.param("to", toDate.toString()));
	}

	private ResultActions findByRaceDate(LocalDate fromDate, LocalDate toDate) throws Exception {
		return mockMvc.perform(get(BY_RACE_DATE_PATH)
			.param("from", fromDate.toString())
			.param("to", toDate.toString()));
	}
}
