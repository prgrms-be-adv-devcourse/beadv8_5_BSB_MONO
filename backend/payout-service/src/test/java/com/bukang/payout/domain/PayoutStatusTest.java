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
		"CALCULATED, true",
		"HELD, true",
		"PAYING, false",
		"PAID, false",
		"CANCELED, false",
		"PAY_MISMATCH, false"
	})
	@DisplayName("지급 전 상태는 정산 예정과 지급보류뿐이다. 지급 중은 돈이 나갔을 수 있어 지급 전이 아니다")
	void isBeforePayment(PayoutStatus status, boolean expected) {
		assertThat(status.isBeforePayment()).isEqualTo(expected);
	}

	@Test
	@DisplayName("모든 상태 중 지급 전은 정확히 CALCULATED · HELD다. 상태를 새로 만들면 여기서 분류를 정해야 한다")
	void beforePaymentIsExactlyCalculatedAndHeld() {
		EnumSet<PayoutStatus> all = EnumSet.allOf(PayoutStatus.class);

		assertThat(all).containsExactlyInAnyOrder(PayoutStatus.CALCULATED, PayoutStatus.HELD,
			PayoutStatus.PAYING, PayoutStatus.PAID, PayoutStatus.CANCELED, PayoutStatus.PAY_MISMATCH);
		assertThat(all.stream().filter(PayoutStatus::isBeforePayment))
			.containsExactlyInAnyOrder(PayoutStatus.CALCULATED, PayoutStatus.HELD);
		assertThat(PayoutStatus.BEFORE_PAYMENT)
			.containsExactlyInAnyOrder(PayoutStatus.CALCULATED, PayoutStatus.HELD);
	}
}
