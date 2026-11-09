package com.bukang.payout.in;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.RetryableTopic;

import com.bukang.payout.domain.exception.PayoutClaimConflictException;
import com.bukang.payout.domain.exception.PayoutClaimNotFoundException;
import com.bukang.payout.domain.exception.PayoutRecoveryConflictException;

/**
 * 환불 알림 소비자의 재시도 설정. 다시 해도 결과가 같은 실패는 재시도 없이 바로 DLT로 보낸다 (PR #265 리뷰)
 */
class PayoutKafkaConsumerRetryTest {

	@Test
	@DisplayName("TC-REV3-1 최종 정산 뒤 환불(회수 충돌)과 잘못된 메시지는 재시도 없이 바로 DLT로 보낸다")
	void refundConflictIsNotRetried() throws NoSuchMethodException {
		RetryableTopic retry = PayoutKafkaConsumer.class.getMethod("handleItemRefunded", String.class)
			.getAnnotation(RetryableTopic.class);

		assertThat(retry.exclude())
			.contains(PayoutRecoveryConflictException.class, IllegalArgumentException.class);
	}

	@Test
	@DisplayName("TC-DEPOSIT-6-1 입금액이 다른 청구 · 없는 청구 · 잘못된 입금 메시지는 재시도 없이 바로 DLT로 보낸다")
	void claimDepositFailuresAreNotRetried() throws NoSuchMethodException {
		RetryableTopic retry = PayoutKafkaConsumer.class.getMethod("handleClaimDeposited", String.class)
			.getAnnotation(RetryableTopic.class);

		assertThat(retry.exclude()).contains(PayoutClaimConflictException.class,
			PayoutClaimNotFoundException.class, IllegalArgumentException.class);
	}
}
