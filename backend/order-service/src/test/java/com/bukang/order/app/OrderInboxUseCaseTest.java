package com.bukang.order.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.order.out.OrderInboxKafkaEventRepository;

@SpringBootTest
@ActiveProfiles("test")
class OrderInboxUseCaseTest {
	@Autowired
	private OrderInboxUseCase orderInboxUseCase;

	@Autowired
	private OrderInboxKafkaEventRepository repository;

	@AfterEach
	void tearDown() {
		repository.deleteAll();
	}

	@Test
	@DisplayName("남긴 메시지는 처리한 것으로 보고, 같은 메시지를 다시 남겨도 한 줄이다. 그룹이 다르면 따로 본다")
	void record() {
		assertThat(orderInboxUseCase.alreadyProcessed("product-outbox-1", "order", "order.race.cancel")).isFalse();

		orderInboxUseCase.record("product-outbox-1", "order", "order.race.cancel", LocalDateTime.now());
		orderInboxUseCase.record("product-outbox-1", "order", "order.race.cancel", LocalDateTime.now());

		assertThat(orderInboxUseCase.alreadyProcessed("product-outbox-1", "order", "order.race.cancel")).isTrue();
		assertThat(orderInboxUseCase.alreadyProcessed("product-outbox-1", "other", "order.race.cancel")).isFalse();
		assertThat(repository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("message-id 헤더가 없으면 거를 기준이 없어 처리하고, 남기지도 않는다")
	void withoutMessageId() {
		assertThat(orderInboxUseCase.alreadyProcessed(null, "order", "order.race.cancel")).isFalse();
		orderInboxUseCase.record(null, "order", "order.race.cancel", LocalDateTime.now());

		assertThat(repository.count()).isZero();
	}
}
