package com.bukang.cash.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.bukang.cash.out.member.CashMemberRepository;
import com.bukang.cash.out.seller.CashSellerRepository;
import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.member.event.MemberJoinedEvent;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.common.shared.seller.event.SellerChangedEvent;

// 실제 임베디드 Kafka 브로커로 CashEventListener가 토픽을 올바로 구독하는지 확인한다.
// 토픽 이름이 안 맞으면(생산자-소비자 불일치) 리스너가 메시지를 못 받아 타임아웃으로 실패한다.
// - 다른 테스트의 공유 H2(bukang-cash-test)와 분리된 전용 DB를 쓴다 (create-drop이 다른 테스트 데이터를 건드리지 않게)
// - 전역 auto-startup은 그대로 꺼 두고, 이 테스트에 필요한 리스너만 직접 켠다 (불필요한 컨슈머 리밸런싱 방지)
// - 테스트용 컨슈머로 읽는 토픽은 @EmbeddedKafka에 적어 둔다 (consumeFromAnEmbeddedTopic은 여기 적힌 토픽만 읽는다)
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {
	KafkaTopics.MEMBER_MEMBER_JOINED,
	CashEventListenerKafkaTest.MEMBER_JOINED_FIRST_RETRY,
	CashKafkaRetry.MEMBER_JOINED_DLT,
	KafkaTopics.MEMBER_MODIFIED,
	CashEventListenerKafkaTest.MEMBER_MODIFIED_FIRST_RETRY,
	CashKafkaRetry.MEMBER_MODIFIED_DLT,
	KafkaTopics.SELLER_CHANGED,
	CashEventListenerKafkaTest.SELLER_CHANGED_FIRST_RETRY,
	CashEventListenerKafkaTest.SELLER_CHANGED_SECOND_RETRY,
	CashKafkaRetry.SELLER_CHANGED_DLT
})
@TestPropertySource(properties =
	"spring.datasource.url=jdbc:h2:mem:bukang-cash-kafka-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class CashEventListenerKafkaTest {
	// 첫 번째 재시도 토픽은 접미사 뒤에 순번 -0, 두 번째는 -1이 붙는다
	static final String MEMBER_JOINED_FIRST_RETRY = KafkaTopics.MEMBER_MEMBER_JOINED
		+ CashKafkaRetry.RETRY_TOPIC_SUFFIX + "-0";
	static final String MEMBER_MODIFIED_FIRST_RETRY = KafkaTopics.MEMBER_MODIFIED
		+ CashKafkaRetry.RETRY_TOPIC_SUFFIX + "-0";
	static final String SELLER_CHANGED_FIRST_RETRY = KafkaTopics.SELLER_CHANGED
		+ CashKafkaRetry.RETRY_TOPIC_SUFFIX + "-0";
	static final String SELLER_CHANGED_SECOND_RETRY = KafkaTopics.SELLER_CHANGED
		+ CashKafkaRetry.RETRY_TOPIC_SUFFIX + "-1";
	private static final String MEMBER_JOINED_GROUP_ID = "CashEventListener__handleMemberJoined__1";
	private static final String MEMBER_MODIFIED_GROUP_ID = "CashEventListener__handleMemberModified__1";
	private static final String SELLER_CHANGED_GROUP_ID = "CashEventListener__handleSellerChanged__1";

	@Autowired
	private KafkaListenerEndpointRegistry registry;
	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;
	@Autowired
	private CashMemberRepository cashMemberRepository;
	@Autowired
	private CashSellerRepository cashSellerRepository;
	@Autowired
	private JsonConverter jsonConverter;
	@Autowired
	private EmbeddedKafkaBroker embeddedKafka;

	@BeforeEach
	void startMemberJoinedListener() {
		startListeners(MEMBER_JOINED_GROUP_ID);
	}

	@Test
	@DisplayName("queuing.member.joined 토픽에 메시지를 보내면 CashEventListener가 받아 CashMember를 저장한다")
	void handleMemberJoined() {
		long memberId = 1001;
		String message = """
			{"id":%d,"createDate":"2026-10-14T10:00:00","modifyDate":"2026-10-14T10:00:00","nickname":"테스터"}
			""".formatted(memberId);

		kafkaTemplate.send(KafkaTopics.MEMBER_MEMBER_JOINED, message);

		await().atMost(Duration.ofSeconds(5))
			.untilAsserted(() -> assertThat(cashMemberRepository.findById(memberId)).isPresent());
	}

	@Test
	@DisplayName("깨진 JSON 가입 메시지는 재시도 토픽을 거치지 않고 queuing.member.joined.cash.dlt로 간다")
	void brokenMemberJoinedGoesToDlt() {
		String brokenMessage = "{\"id\":1002,\"nickname\":";

		kafkaTemplate.send(KafkaTopics.MEMBER_MEMBER_JOINED, brokenMessage);

		try (Consumer<String, String> dltConsumer = testConsumer("cash-dlt-test")) {
			embeddedKafka.consumeFromAnEmbeddedTopic(dltConsumer, CashKafkaRetry.MEMBER_JOINED_DLT);
			ConsumerRecord<String, String> dltRecord = KafkaTestUtils.getSingleRecord(
				dltConsumer, CashKafkaRetry.MEMBER_JOINED_DLT, Duration.ofSeconds(10));
			assertThat(dltRecord.value()).isEqualTo(brokenMessage);
		}
		// 재시도했다면 DLT보다 먼저 첫 번째 재시도 토픽에 들어갔어야 한다
		// DLT에 도착한 뒤 재시도 토픽을 처음부터 읽어 이 메시지가 없으면, 재시도 없이 바로 DLT로 간 것이다
		assertThat(readValues(MEMBER_JOINED_FIRST_RETRY, "cash-retry-test", Duration.ofSeconds(2)))
			.doesNotContain(brokenMessage);
	}

	@Test
	@DisplayName("회원보다 먼저 온 판매자 메시지는 재시도 토픽으로 갔다가, 가입 메시지가 처리된 뒤 재시도에서 저장된다")
	void sellerBeforeMemberIsSavedByRetry() {
		long memberId = 1003;
		long sellerApplicationId = 2003;
		LocalDateTime now = LocalDateTime.of(2026, 10, 20, 10, 0);
		startListeners(SELLER_CHANGED_GROUP_ID);

		String sellerMessage = sellerMessage(sellerApplicationId, memberId, now);

		kafkaTemplate.send(KafkaTopics.SELLER_CHANGED, sellerMessage);

		// 첫 시도가 실패해 재시도 토픽에 들어간 것을 확인한 뒤에 가입 메시지를 보낸다
		// (먼저 보내면 첫 시도에서 성공해 재시도를 거치지 않을 수 있다)
		awaitValue(SELLER_CHANGED_FIRST_RETRY, "cash-seller-retry-test", sellerMessage, Duration.ofSeconds(10));
		assertThat(cashSellerRepository.findById(sellerApplicationId)).isEmpty();

		kafkaTemplate.send(KafkaTopics.MEMBER_MEMBER_JOINED,
			jsonConverter.toJson(new MemberJoinedEvent(memberId, now, now, "판매자")));

		// 재시도 간격은 1초 → 2초라 두 번째 재시도까지 기다려도 10초 안에 끝난다
		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(cashSellerRepository.findById(sellerApplicationId))
				.hasValueSatisfying(seller -> assertThat(seller.getMember().getId()).isEqualTo(memberId)));
	}

	@Test
	@DisplayName("회원이 끝내 오지 않은 판매자 메시지는 재시도 토픽 두 곳을 모두 거친 뒤 queuing.seller.changed.cash.dlt로 간다")
	void sellerWithoutMemberGoesToDltAfterRetries() {
		long memberId = 1004;
		long sellerApplicationId = 2004;
		String sellerMessage = sellerMessage(sellerApplicationId, memberId, LocalDateTime.of(2026, 10, 20, 10, 0));
		startListeners(SELLER_CHANGED_GROUP_ID);

		kafkaTemplate.send(KafkaTopics.SELLER_CHANGED, sellerMessage);

		// 처음 시도 → 1초 뒤 재시도 → 2초 뒤 재시도가 모두 실패하면 DLT로 간다
		awaitValue(CashKafkaRetry.SELLER_CHANGED_DLT, "cash-seller-dlt-test", sellerMessage, Duration.ofSeconds(15));
		// DLT보다 먼저 재시도 토픽 두 곳에 들어갔어야 한다
		awaitValue(SELLER_CHANGED_FIRST_RETRY, "cash-seller-first-retry-test", sellerMessage, Duration.ofSeconds(5));
		awaitValue(SELLER_CHANGED_SECOND_RETRY, "cash-seller-second-retry-test", sellerMessage, Duration.ofSeconds(5));
		assertThat(cashSellerRepository.findById(sellerApplicationId)).isEmpty();
	}

	@Test
	@DisplayName("깨진 JSON 수정 메시지는 재시도 토픽을 거치지 않고 queuing.member.modified.cash.dlt로 간다")
	void brokenMemberModifiedGoesToDlt() {
		String brokenMessage = "{\"id\":1005,\"nickname\":";
		startListeners(MEMBER_MODIFIED_GROUP_ID);

		kafkaTemplate.send(KafkaTopics.MEMBER_MODIFIED, brokenMessage);

		awaitValue(CashKafkaRetry.MEMBER_MODIFIED_DLT, "cash-modified-dlt-test", brokenMessage, Duration.ofSeconds(10));
		assertThat(readValues(MEMBER_MODIFIED_FIRST_RETRY, "cash-modified-retry-test", Duration.ofSeconds(2)))
			.doesNotContain(brokenMessage);
	}

	private String sellerMessage(long sellerApplicationId, long memberId, LocalDateTime now) {
		return jsonConverter.toJson(new SellerChangedEvent(
			sellerApplicationId, memberId, SellerType.INDIVIDUAL, null, null, "김판매", SellerStatus.PENDING, now, now));
	}

	// 본 리스너와 재시도 토픽 리스너(그룹 이름이 본 그룹 + 재시도 접미사)를 켠다
	// DLT 리스너는 CashEventDltListener가 따로 받으므로 켜지 않는다
	private void startListeners(String groupId) {
		registry.getListenerContainers().stream()
			.filter(container -> container.getGroupId() != null && container.getGroupId().startsWith(groupId))
			.filter(container -> !container.getGroupId().endsWith(CashKafkaRetry.DLT_TOPIC_SUFFIX))
			.filter(container -> !container.isRunning())
			.forEach(container -> container.start());
	}

	// 정해진 시간 동안 토픽을 처음부터 읽어 메시지 본문을 모은다
	private List<String> readValues(String topic, String groupId, Duration duration) {
		List<String> values = new ArrayList<>();
		try (Consumer<String, String> consumer = testConsumer(groupId)) {
			embeddedKafka.consumeFromAnEmbeddedTopic(consumer, topic);
			long deadline = System.nanoTime() + duration.toNanos();
			while (System.nanoTime() < deadline) {
				consumer.poll(Duration.ofMillis(200)).forEach(consumerRecord -> values.add(consumerRecord.value()));
			}
		}
		return values;
	}

	// 토픽을 처음부터 읽어, 본문이 같은 메시지가 들어올 때까지 기다린다
	// 같은 토픽에 다른 테스트의 메시지도 쌓이므로, 개수가 아니라 본문으로 찾는다
	// (컨슈머는 여러 스레드에서 쓰면 안 되므로 pollInSameThread로 이 스레드에서만 읽는다)
	private void awaitValue(String topic, String groupId, String value, Duration timeout) {
		List<String> values = new ArrayList<>();
		try (Consumer<String, String> consumer = testConsumer(groupId)) {
			embeddedKafka.consumeFromAnEmbeddedTopic(consumer, topic);
			await().atMost(timeout).pollInSameThread().until(() -> {
				consumer.poll(Duration.ofMillis(200)).forEach(consumerRecord -> values.add(consumerRecord.value()));
				return values.contains(value);
			});
		}
	}

	// 앱의 리스너와 오프셋이 섞이지 않게 테스트 전용 그룹으로 토픽을 처음부터 읽는다
	private Consumer<String, String> testConsumer(String groupId) {
		Map<String, Object> props = KafkaTestUtils.consumerProps(embeddedKafka, groupId, false);
		return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer())
			.createConsumer();
	}
}
