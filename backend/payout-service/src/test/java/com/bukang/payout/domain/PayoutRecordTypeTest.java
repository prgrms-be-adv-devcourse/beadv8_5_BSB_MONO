package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.EnumSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PayoutRecordTypeTest {
	@Test
	@DisplayName("bundle_key를 안 쓰는 줄 종류는 상계 · 상계 취소뿐이다 (나머지 참가권 없는 줄은 키로 막는다)")
	void usesBundleKeyIsFalseOnlyForOffsetLines() {
		assertThat(EnumSet.allOf(PayoutRecordType.class).stream().filter(type -> !type.usesBundleKey()))
			.containsExactlyInAnyOrder(PayoutRecordType.회수_상계, PayoutRecordType.회수_상계_취소);
	}

	@Test
	@DisplayName("모든 줄 종류가 usesBundleKey를 정해 둔다 (default 없는 switch라 새 종류는 컴파일되지 않는다)")
	void everyTypeClassifiesBundleKeyUse() {
		assertThatCode(() -> EnumSet.allOf(PayoutRecordType.class).forEach(PayoutRecordType::usesBundleKey))
			.doesNotThrowAnyException();
	}
}
