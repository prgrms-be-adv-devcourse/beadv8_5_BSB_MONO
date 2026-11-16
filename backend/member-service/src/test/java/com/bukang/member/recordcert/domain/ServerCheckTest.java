package com.bukang.member.recordcert.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// 서버 확인: 참가자가 확정한 값 × 서버가 원본에서 다시 읽은 글자 → 칸별 맞음 · 다름 · 읽지 못함 (BUK-273)
class ServerCheckTest {
	private static final CertValues CONFIRMED =
		new CertValues(5405, LocalDate.of(2023, 4, 12), CourseClass.HALF, "M-8363");

	@Test
	@DisplayName("네 칸을 다시 읽은 값이 확정 값과 모두 같으면 서버 확인 맞음이다")
	void allFieldsMatch() {
		ServerCheck check = ServerCheck.of(CONFIRMED, Map.of(
			CheckField.NET_TIME, "01:30:05",
			CheckField.NET_LABEL, "넷타임",
			CheckField.RACE_DATE, "2023.04.12",
			CheckField.COURSE, "하프코스"));

		assertThat(check.results()).containsOnly(
			Map.entry(CheckField.NET_TIME, FieldCheck.MATCH),
			Map.entry(CheckField.NET_LABEL, FieldCheck.MATCH),
			Map.entry(CheckField.RACE_DATE, FieldCheck.MATCH),
			Map.entry(CheckField.COURSE, FieldCheck.MATCH));
		assertThat(check.allMatch()).isTrue();
	}

	@ParameterizedTest(name = "\"{0}\" → {1}")
	@CsvSource(delimiter = '|', value = {
		"01:30:05|MATCH",
		"1:30:05|MATCH",
		"1시간 30분 5초|MATCH",
		"1 : 30 : 05|MATCH",
		"01:30:06|MISMATCH",
		"01:30:05 01:31:10|UNREADABLE",
		"기록 없음|UNREADABLE",
		"23.04.12|UNREADABLE"
	})
	@DisplayName("넷타임: 시간이 하나일 때만 비교하고, 없거나 둘 이상이면 읽지 못함")
	void netTime(String text, FieldCheck expected) {
		assertThat(checkOne(CheckField.NET_TIME, text)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "\"{0}\" → {1}")
	@CsvSource(delimiter = '|', value = {
		"넷타임|MATCH",
		"Net Time|MATCH",
		"순기록|MATCH",
		"건타임|MISMATCH",
		"넷타임(구간)|MISMATCH",
		"완주|MISMATCH"
	})
	@DisplayName("넷타임 표시 단어: 넷타임 단어가 있고 건타임 · 구간 단어가 없어야 맞음")
	void netLabel(String text, FieldCheck expected) {
		assertThat(checkOne(CheckField.NET_LABEL, text)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "\"{0}\" → {1}")
	@CsvSource(delimiter = '|', value = {
		"2023.04.12|MATCH",
		"2023년 4월 12일|MATCH",
		"2023-4-12|MATCH",
		"Apr 12, 2023|MATCH",
		"12th April 2023|MATCH",
		"2023.04.13|MISMATCH",
		"2023.04.12 ~ 2023.04.13|UNREADABLE",
		"4월 12일|UNREADABLE"
	})
	@DisplayName("대회일: 날짜가 하나일 때만 비교한다")
	void raceDate(String text, FieldCheck expected) {
		assertThat(checkOne(CheckField.RACE_DATE, text)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "\"{0}\" (확정 {1}) → {2}")
	@CsvSource(delimiter = '|', value = {
		"하프코스|HALF|MATCH",
		"Half Marathon|HALF|MATCH",
		"21.0975km|HALF|MATCH",
		"풀코스|FULL|MATCH",
		"42.195km|FULL|MATCH",
		"10km|TEN_K|MATCH",
		"5K|FIVE_K|MATCH",
		"울트라 100km|ULTRA|MATCH",
		"50km|ULTRA|MATCH",
		"32km|OTHER|MATCH",
		"울트라 32km|OTHER|MATCH",
		"울트라마라톤|ULTRA|UNREADABLE",
		"10km|HALF|MISMATCH",
		"5K · 10K|FIVE_K|UNREADABLE",
		"참가 부문|HALF|UNREADABLE"
	})
	@DisplayName("종목: 종목 구분으로 바꿔 비교한다")
	void course(String text, CourseClass confirmedClass, FieldCheck expected) {
		CertValues confirmed = new CertValues(5405, CONFIRMED.raceDate(), confirmedClass, null);

		ServerCheck check = ServerCheck.of(confirmed, Map.of(CheckField.COURSE, text));

		assertThat(check.results().get(CheckField.COURSE)).isEqualTo(expected);
	}

	@Test
	@DisplayName("상자가 없는 칸(직접 입력 · 표시 단어 없음)은 읽지 못함이고 서버 확인은 맞음이 아니다")
	void missingBoxIsUnreadable() {
		ServerCheck check = ServerCheck.of(CONFIRMED, Map.of(
			CheckField.NET_TIME, "01:30:05",
			CheckField.RACE_DATE, "2023.04.12",
			CheckField.COURSE, "하프"));

		assertThat(check.results().get(CheckField.NET_LABEL)).isEqualTo(FieldCheck.UNREADABLE);
		assertThat(check.allMatch()).isFalse();
	}

	@Test
	@DisplayName("칸 결과가 비었거나 일부만 있으면 서버 확인 맞음이 아니다")
	void incompleteResultsAreNotAllMatch() {
		ServerCheck empty = new ServerCheck(Map.of());
		ServerCheck partial = new ServerCheck(Map.of(CheckField.NET_TIME, FieldCheck.MATCH));

		assertThat(empty.allMatch()).isFalse();
		assertThat(partial.allMatch()).isFalse();
	}

	private static FieldCheck checkOne(CheckField field, String text) {
		return ServerCheck.of(CONFIRMED, Map.of(field, text)).results().get(field);
	}
}
