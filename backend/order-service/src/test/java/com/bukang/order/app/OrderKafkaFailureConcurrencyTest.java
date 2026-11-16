package com.bukang.order.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.LongConsumer;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.global.exception.BusinessException;
import com.bukang.common.global.notify.SlackNotifier;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.kafka.dlt.DltKafkaEventStatus;
import com.bukang.common.shared.kafka.dlt.KafkaDltMessage;
import com.bukang.common.shared.kafka.outbox.OutboxStatus;
import com.bukang.order.domain.OrderDltKafkaEvent;
import com.bukang.order.domain.OrderOutboxKafkaEvent;
import com.bukang.order.out.OrderDltKafkaEventRepository;
import com.bukang.order.out.OrderOutboxKafkaEventRepository;

/**
 * 다시 보내기·되돌리기를 동시에 두 번 불러도 한 번만 통과하는지 확인한다 (PR #436 리뷰).
 * 상태를 읽고 엔티티로 바꾸면 두 요청이 모두 처리 실패(발행 실패)를 읽고 둘 다 통과해 같은 메시지가 두 번 나간다.
 * 한 번에 맞부딪히지 않을 수 있어 여러 번 되풀이한다. Kafka 전송만 spy로 바꾸고 DB(H2)는 진짜로 쓴다.
 * spy 구성을 OrderOutboxKafkaRelayTest와 같게 두어 Spring 컨텍스트를 함께 쓴다.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderKafkaFailureConcurrencyTest {
	private static final int ROUNDS = 20;

	@Autowired
	private OrderKafkaFailureFacade orderKafkaFailureFacade;
	@Autowired
	private OrderOutboxKafkaRelayUseCase orderOutboxKafkaRelayUseCase;
	@Autowired
	private OrderOutboxKafkaAppendUseCase orderOutboxKafkaAppendUseCase;
	@Autowired
	private OrderDltKafkaEventRepository orderDltKafkaEventRepository;
	@Autowired
	private OrderOutboxKafkaEventRepository orderOutboxKafkaEventRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;

	@MockitoSpyBean
	private KafkaTemplate<?, ?> kafkaTemplate;
	@MockitoSpyBean
	private SlackNotifier slackNotifier;

	@BeforeEach
	void setUp() {
		doReturn(CompletableFuture.completedFuture(null)).when(kafka())
			.send(ArgumentMatchers.<ProducerRecord<String, String>>any());
	}

	@AfterEach
	void tearDown() {
		orderDltKafkaEventRepository.deleteAll();
		orderOutboxKafkaEventRepository.deleteAll();
	}

	@Test
	@DisplayName("처리 실패 행을 동시에 두 번 다시 보내면 한 번만 통과하고 Kafka로도 한 번만 나간다")
	void resendDltOnce() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			long id = saveFailedDlt(round);

			assertThat(runTwice(id, orderKafkaFailureFacade::resendDlt)).isEqualTo(1);
			assertThat(orderDltKafkaEventRepository.findById(id).orElseThrow().getStatus())
				.isEqualTo(DltKafkaEventStatus.RESENT);
		}
		verify(kafka(), times(ROUNDS)).send(ArgumentMatchers.<ProducerRecord<String, String>>any());
	}

	@Test
	@DisplayName("발행 실패 행을 동시에 두 번 되돌려도 한 번만 통과하고, 릴레이가 보낸 뒤에는 되돌리지 않는다")
	void retryOutboxOnce() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			long id = saveSendFailedOutbox(round);

			assertThat(runTwice(id, orderKafkaFailureFacade::retryOutbox)).isEqualTo(1);
			assertThat(orderOutboxKafkaEventRepository.findById(id).orElseThrow().getStatus())
				.isEqualTo(OutboxStatus.PENDING);
		}
		orderOutboxKafkaRelayUseCase.relay(LocalDateTime.now());
		assertThat(orderOutboxKafkaEventRepository.findAll()).extracting(OrderOutboxKafkaEvent::getStatus)
			.containsOnly(OutboxStatus.SENT);
		verify(kafka(), times(ROUNDS)).send(ArgumentMatchers.<ProducerRecord<String, String>>any());
	}

	// 두 쓰레드가 같은 행에 동시에 부르고, 통과한 수를 돌려준다. 상태 때문에 거절되면(409) 통과로 세지 않는다
	private int runTwice(long id, LongConsumer action) throws Exception {
		CyclicBarrier barrier = new CyclicBarrier(2);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Callable<Boolean> task = () -> {
				barrier.await();
				try {
					action.accept(id);
					return true;
				} catch (BusinessException exception) {
					return false;
				}
			};
			List<Future<Boolean>> futures = new ArrayList<>();
			futures.add(executor.submit(task));
			futures.add(executor.submit(task));
			int passed = 0;
			for (Future<Boolean> future : futures) {
				if (future.get()) {
					passed++;
				}
			}
			return passed;
		} finally {
			executor.shutdownNow();
		}
	}

	private long saveFailedDlt(int round) {
		KafkaDltMessage message = new KafkaDltMessage(KafkaTopics.ORDER_RACE_CANCEL,
			KafkaTopics.ORDER_RACE_CANCEL + OrderKafkaRetry.DLT_TOPIC_SUFFIX, 0, round, "300",
			"{\"raceId\":300}", "product-outbox-concurrency-" + round, "java.lang.IllegalStateException", "실패");
		return orderDltKafkaEventRepository.save(new OrderDltKafkaEvent(message)).getId();
	}

	// 자리 복구 아웃박스 행을 만들고 메시지 문제로 포기한 것처럼 발행 실패로 바꾼다
	private long saveSendFailedOutbox(int round) {
		transactionTemplate.executeWithoutResult(status ->
			orderOutboxKafkaAppendUseCase.appendSeatRestore(5000 + round));
		OrderOutboxKafkaEvent event = orderOutboxKafkaEventRepository.findAll().stream()
			.filter(row -> row.getRefId() == 5000 + round).findFirst().orElseThrow();
		event.markSendFailed(true, LocalDateTime.now());
		return orderOutboxKafkaEventRepository.save(event).getId();
	}

	@SuppressWarnings("unchecked")
	private KafkaTemplate<String, String> kafka() {
		return (KafkaTemplate<String, String>)kafkaTemplate;
	}
}
