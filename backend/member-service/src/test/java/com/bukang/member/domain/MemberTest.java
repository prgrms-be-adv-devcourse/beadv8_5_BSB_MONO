package com.bukang.member.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

	@Test
	@DisplayName("만 14세 생일 당일부터 가입할 수 있다")
	void joinableOn14thBirthday() {
		assertThat(Member.isJoinableAge(LocalDate.of(2012, 10, 10), TODAY)).isTrue();
	}

	@Test
	@DisplayName("만 14세 생일 하루 전에는 가입할 수 없다")
	void notJoinableDayBefore14thBirthday() {
		assertThat(Member.isJoinableAge(LocalDate.of(2012, 10, 11), TODAY)).isFalse();
	}
}
