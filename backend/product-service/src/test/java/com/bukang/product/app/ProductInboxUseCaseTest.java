package com.bukang.product.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.product.support.ProductIntegrationTest;

/**
 * 인박스 기록(ProductInboxUseCase.tryRecord)이 처리할 메시지와 이미 처리한 메시지를 가르는지 확인한다.
 * 리스너에서는 메시지 처리와 같은 트랜잭션에서 부르므로, 테스트도 트랜잭션 안에서 부른다.
 */
class ProductInboxUseCaseTest extends ProductIntegrationTest {
	private static final String GROUP = "product";
	private static final String TOPIC = KafkaTopics.PRODUCT_HOLD_CANCEL;
	private static final String MESSAGE_ID = "order-outbox-9f1c2b7a-4d3e-4a5b-8c6d-7e8f9a0b1c42";

	@Autowired
	private ProductInboxUseCase productInboxUseCase;

	private final LocalDateTime now = LocalDateTime.now();

	@Test
	@DisplayName("처음 받은 메시지(order-outbox-{UUID})는 처리하고, 같은 메시지가 다시 오면 건너뛴다")
	void recordOnlyFirstTime() {
		boolean first = tryRecord(MESSAGE_ID);
		boolean again = tryRecord(MESSAGE_ID);

		assertThat(first).isTrue();
		assertThat(again).isFalse();
		assertThat(productInboxKafkaEventRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("메시지 처리가 실패해 롤백되면 인박스 행도 남지 않아, 다시 받은 같은 메시지를 처리한다")
	void retryAfterRollback() {
		transactionTemplate.executeWithoutResult(status -> {
			productInboxUseCase.tryRecord(MESSAGE_ID, GROUP, TOPIC, now);
			status.setRollbackOnly();
		});

		boolean retried = tryRecord(MESSAGE_ID);

		assertThat(retried).isTrue();
	}

	// 메시지 처리 트랜잭션처럼 트랜잭션 안에서 부른다
	private boolean tryRecord(String messageId) {
		return transactionTemplate.execute(status -> productInboxUseCase.tryRecord(messageId, GROUP, TOPIC, now));
	}
}
