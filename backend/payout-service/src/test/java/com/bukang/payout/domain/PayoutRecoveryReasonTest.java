package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.bukang.common.shared.order.event.OrderItemRefundReason;

class PayoutRecoveryReasonTest {
	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({
		"RACE_CANCELED, 대회_취소",
		"SCHEDULE_CHANGED, 일정_변경",
		"BUYER_CANCELED, 일정_변경"
	})
	@DisplayName("주문 환불 사유를 회수 사유로 옮긴다. 대회 취소만 대회_취소, 나머지는 일정_변경")
	void from(OrderItemRefundReason refundReason, PayoutRecoveryReason expected) {
		assertThat(PayoutRecoveryReason.from(refundReason)).isEqualTo(expected);
	}
}
