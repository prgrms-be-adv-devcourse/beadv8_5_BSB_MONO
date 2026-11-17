package com.bukang.member.recordcert.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// 판정: 기록증 규칙 × 참가자가 확정한 값 → 통과 · 반려 · 심사 필요 (BUK-273)
class JudgementTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);
	private static final LocalDate SINCE = LocalDate.of(2023, 4, 1);
	// 하프 2시간 이내
	private static final CourseRule HALF_UNDER_2H = new CourseRule(SINCE, 2 * 3600, Set.of(CourseClass.HALF));

	@Test
	@DisplayName("기간 안의 대회에서 기준 넷타임 안에 들어온 인정 종목 기록은 통과한다")
	void passesWhenAllConditionsMet() {
		CertValues values = new CertValues(5405, LocalDate.of(2024, 4, 12), CourseClass.HALF, "M-8363");

		Judgement judgement = Judgement.of(HALF_UNDER_2H, values, TODAY);

		assertThat(judgement.verdict()).isEqualTo(Verdict.PASS);
		assertThat(judgement.reasons()).isEmpty();
	}

	@Test
	@DisplayName("기록 인정 기간 시작일 당일 대회는 통과하고, 하루 전 대회는 반려한다")
	void recognizedSinceIsInclusive() {
		Judgement onStart = Judgement.of(HALF_UNDER_2H, half(5405, SINCE), TODAY);
		Judgement dayBefore = Judgement.of(HALF_UNDER_2H, half(5405, SINCE.minusDays(1)), TODAY);

		assertThat(onStart.verdict()).isEqualTo(Verdict.PASS);
		assertThat(dayBefore.verdict()).isEqualTo(Verdict.REJECT);
		assertThat(dayBefore.reasons()).containsExactly(JudgementReason.BEFORE_RECOGNIZED_PERIOD);
	}

	@Test
	@DisplayName("기준 넷타임과 같으면 통과하고, 1초라도 넘으면 반려한다")
	void maxNetTimeIsInclusive() {
		Judgement same = Judgement.of(HALF_UNDER_2H, half(7200, TODAY), TODAY);
		Judgement oneSecondOver = Judgement.of(HALF_UNDER_2H, half(7201, TODAY), TODAY);

		assertThat(same.verdict()).isEqualTo(Verdict.PASS);
		assertThat(oneSecondOver.verdict()).isEqualTo(Verdict.REJECT);
		assertThat(oneSecondOver.reasons()).containsExactly(JudgementReason.NET_TIME_OVER);
	}

	@Test
	@DisplayName("인정 종목이 아닌 기록은 반려한다")
	void rejectsCourseNotAllowed() {
		CertValues full = new CertValues(14400, TODAY, CourseClass.FULL, null);

		CourseRule halfUnder6h = new CourseRule(SINCE, 6 * 3600, Set.of(CourseClass.HALF));

		Judgement judgement = Judgement.of(halfUnder6h, full, TODAY);

		assertThat(judgement.verdict()).isEqualTo(Verdict.REJECT);
		assertThat(judgement.reasons()).containsExactly(JudgementReason.COURSE_NOT_ALLOWED);
	}

	@Test
	@DisplayName("종목 구분이 기타면 반려하지 않고 심사 필요로 보낸다")
	void otherCourseNeedsReview() {
		CertValues other = new CertValues(5405, TODAY, CourseClass.OTHER, null);

		Judgement judgement = Judgement.of(HALF_UNDER_2H, other, TODAY);

		assertThat(judgement.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
		assertThat(judgement.reasons()).containsExactly(JudgementReason.COURSE_OTHER);
	}

	@Test
	@DisplayName("대회일이 오늘이면 통과하고, 내일이면 심사 필요로 보낸다")
	void futureRaceDateNeedsReview() {
		Judgement today = Judgement.of(HALF_UNDER_2H, half(5405, TODAY), TODAY);
		Judgement tomorrow = Judgement.of(HALF_UNDER_2H, half(5405, TODAY.plusDays(1)), TODAY);

		assertThat(today.verdict()).isEqualTo(Verdict.PASS);
		assertThat(tomorrow.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
		assertThat(tomorrow.reasons()).containsExactly(JudgementReason.FUTURE_RACE_DATE);
	}

	@ParameterizedTest(name = "{0} {1}초 이하면 심사 필요, 1초 느리면 통과")
	@CsvSource({
		"FULL, 7200",
		"HALF, 3600",
		"TEN_K, 1706",
		"FIVE_K, 853",
		"ULTRA, 7200"
	})
	@DisplayName("풀 2시간 속도 이하로 빠른 넷타임은 부정으로 보고 심사 필요로 보낸다")
	void tooFastNeedsReview(CourseClass course, int limitSeconds) {
		CourseRule anyTime = new CourseRule(SINCE, 30 * 3600, Set.of(course));

		Judgement atLimit = Judgement.of(anyTime, new CertValues(limitSeconds, TODAY, course, null), TODAY);
		Judgement slower = Judgement.of(anyTime, new CertValues(limitSeconds + 1, TODAY, course, null), TODAY);

		assertThat(atLimit.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
		assertThat(atLimit.reasons()).containsExactly(JudgementReason.TOO_FAST);
		assertThat(slower.verdict()).isEqualTo(Verdict.PASS);
	}

	@Test
	@DisplayName("반려 사유와 심사 필요 사유가 같이 있으면 반려하고 사유를 모두 남긴다")
	void rejectWinsOverReview() {
		CertValues values = half(9000, TODAY.plusDays(1));

		Judgement judgement = Judgement.of(HALF_UNDER_2H, values, TODAY);

		assertThat(judgement.verdict()).isEqualTo(Verdict.REJECT);
		assertThat(judgement.reasons())
			.containsExactlyInAnyOrder(JudgementReason.NET_TIME_OVER, JudgementReason.FUTURE_RACE_DATE);
	}

	private static CertValues half(int netSeconds, LocalDate raceDate) {
		return new CertValues(netSeconds, raceDate, CourseClass.HALF, null);
	}
}
