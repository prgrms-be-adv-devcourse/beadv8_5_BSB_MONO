package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PayoutStatusTest {
	@ParameterizedTest(name = "{0} → 지급 전 {1}")
	@CsvSource({
		"지급_예정, true",
		"지급보류, true",
		"지급_중, false",
		"지급_완료, false",
		"취소, false"
	})
	@DisplayName("지급 전 상태는 정산 예정과 지급보류뿐이다. 지급 중은 돈이 나갔을 수 있어 지급 전이 아니다")
	void isBeforePayment(PayoutStatus status, boolean expected) {
		assertThat(status.isBeforePayment()).isEqualTo(expected);
	}

	@Test
	@DisplayName("모든 상태 중 지급 전은 정확히 지급_예정 · 지급보류다. 상태를 새로 만들면 여기서 분류를 정해야 한다")
	void beforePaymentIsExactlyCalculatedAndHeld() {
		EnumSet<PayoutStatus> all = EnumSet.allOf(PayoutStatus.class);

		assertThat(all).containsExactlyInAnyOrder(PayoutStatus.지급_예정, PayoutStatus.지급보류,
			PayoutStatus.지급_중, PayoutStatus.지급_완료, PayoutStatus.취소);
		assertThat(all.stream().filter(PayoutStatus::isBeforePayment))
			.containsExactlyInAnyOrder(PayoutStatus.지급_예정, PayoutStatus.지급보류);
		assertThat(PayoutStatus.BEFORE_PAYMENT)
			.containsExactlyInAnyOrder(PayoutStatus.지급_예정, PayoutStatus.지급보류);
	}
}
