package com.bukang.cash.out.pg;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 토스 키 없이 개발할 때 쓰는 가짜 PG 테스트
class FakePaymentClientTest {
	private final FakePaymentClient client = new FakePaymentClient();

	@Test
	@DisplayName("가짜 PG가 발급한 가상계좌는 바로 입금된 것으로 보고, 주문번호로 조회하면 발급 금액으로 입금 완료를 돌려준다")
	void issuedVirtualAccountIsDepositedOnLookup() {
		client.issueVirtualAccount("RC-20261214-0A3F", 400_000);

		PaymentLookupResult lookup = client.lookupByOrderId("RC-20261214-0A3F");

		assertThat(lookup.status()).isEqualTo(PaymentLookupResult.Status.COMPLETED);
		assertThat(lookup.approval().approvedAmount()).isEqualTo(400_000);
	}

	@Test
	@DisplayName("가짜 PG가 발급하지 않은 주문번호(충전 등)는 지금처럼 승인 안 됨으로 돌려준다")
	void otherOrderIsNotCompleted() {
		assertThat(client.lookupByOrderId("charge-123").status()).isEqualTo(PaymentLookupResult.Status.NOT_COMPLETED);
	}
}
