package com.bukang.product.app;

import static com.bukang.product.support.RaceRequests.course;
import static com.bukang.product.support.RaceRequests.race;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.bukang.product.domain.exception.InvalidRaceException;

// 종목 나이 규칙(CRU-54)을 Docker 없이 확인한다. 등록·수정 API 흐름은 ApiV1RaceRegisterTest·ApiV1RaceUpdateTest가 본다
class RaceRequestValidatorAgeTest {

	private final RaceRequestValidator validator = new RaceRequestValidator();

	@ParameterizedTest
	@ValueSource(ints = {14, 19})
	@DisplayName("최소 나이 14(제한 없음)·19(성인만)에 최대 나이가 없으면 통과한다")
	void acceptsNoLimitAndAdultOnly(int minAge) {
		assertThatCode(() -> validator.validate(race().course(course().age(minAge, null)).build()))
			.doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(ints = {13, 16, 18, 20})
	@DisplayName("최소 나이가 14·19가 아니면 종목명이 붙은 메시지로 거절한다")
	void rejectsOtherMinAge(int minAge) {
		assertThatThrownBy(() -> validator.validate(race().course(course().age(minAge, null)).build()))
			.isInstanceOf(InvalidRaceException.class)
			.hasMessage("참가 최소 나이는 14(제한 없음) 또는 19(성인만)로 작성해주세요. (종목: 10K)");
	}

	@ParameterizedTest
	@CsvSource({"14,70", "19,70", "19,19"})
	@DisplayName("최대 나이가 있으면 종목명이 붙은 메시지로 거절한다")
	void rejectsMaxAge(int minAge, int maxAge) {
		assertThatThrownBy(() -> validator.validate(race().course(course().age(minAge, maxAge)).build()))
			.isInstanceOf(InvalidRaceException.class)
			.hasMessage("참가 최대 나이는 받지 않습니다. 비워주세요. (종목: 10K)");
	}
}
