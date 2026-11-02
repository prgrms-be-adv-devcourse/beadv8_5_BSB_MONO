package com.bukang.member.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaEventHeaders;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.member.event.MemberJoinedEvent;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.app.MemberOutboxRelayUseCase;
import com.bukang.member.domain.MemberOutboxEvent;
import com.bukang.member.domain.TermsType;
import com.bukang.member.dto.MemberDto;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;

// 실제 임베디드 Kafka 브로커로 아웃박스 행이 헤더 다섯 개를 달고 나가는지 확인한다 (BUK-41)
// - 다른 테스트의 공유 H2(bukang-test)와 분리된 전용 DB를 쓴다 (create-drop이 다른 테스트 데이터를 건드리지 않게)
// - 스케줄러는 테스트에서 꺼져 있어 발행 작업을 직접 부른다
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = KafkaTopics.MEMBER_MEMBER_JOINED)
@TestPropertySource(properties =
	"spring.datasource.url=jdbc:h2:mem:bukang-member-outbox-kafka-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@Import(InMemoryIdentityVerificationStore.Config.class)
class MemberOutboxKafkaTest {
	private static final Duration POLL_TIMEOUT = Duration.ofSeconds(10);

	@Autowired
	private InMemoryIdentityVerificationStore identityStore;
	@Autowired
	private EmbeddedKafkaBroker embeddedKafka;
	@Autowired
	private MemberFacade memberFacade;
	@Autowired
	private MemberOutboxRelayUseCase memberOutboxRelayUseCase;
	@Autowired
	private MemberOutboxEventRepository memberOutboxEventRepository;
	@Autowired
	private MemberRepository memberRepository;
	@Autowired
	private TermsAgreementRepository termsAgreementRepository;
	@Autowired
	private JsonConverter jsonConverter;

	private Consumer<String, String> consumer;

	@BeforeEach
	void setUp() {
		Map<String, Object> props = KafkaTestUtils.consumerProps(
			embeddedKafka, "member-outbox-kafka-test-" + System.nanoTime(), false);
		consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer())
			.createConsumer();
		embeddedKafka.consumeFromAnEmbeddedTopic(consumer, KafkaTopics.MEMBER_MEMBER_JOINED);
	}

	@AfterEach
	void tearDown() {
		consumer.close();
		memberOutboxEventRepository.deleteAll();
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("가입 행을 보내면 브로커를 거친 메시지의 key·헤더 다섯 개가 행과 같고, 본문 eventId가 헤더 eventId와 같다")
	void headersAndBodyEventIdArriveThroughBroker() {
		MemberDto member = memberFacade.join(identityStore.joinRequest(
			"outbox-kafka@crewrun.com", "password1234", "김러너", "헤더러너", "010-4000-0001",
			LocalDate.of(1995, 4, 12), null, List.of(TermsType.SERVICE, TermsType.PRIVACY)));
		MemberOutboxEvent row = memberOutboxEventRepository.findAll().get(0);

		memberOutboxRelayUseCase.relay(LocalDateTime.now());

		ConsumerRecord<String, String> record =
			KafkaTestUtils.getSingleRecord(consumer, KafkaTopics.MEMBER_MEMBER_JOINED, POLL_TIMEOUT);
		MemberJoinedEvent body = jsonConverter.fromJson(record.value(), MemberJoinedEvent.class);
		assertThat(record.key()).isEqualTo(String.valueOf(member.getId()));
		assertThat(header(record, KafkaEventHeaders.EVENT_ID)).isEqualTo(row.getEventId());
		assertThat(header(record, KafkaEventHeaders.EVENT_TYPE)).isEqualTo(KafkaTopics.MEMBER_MEMBER_JOINED);
		assertThat(LocalDateTime.parse(header(record, KafkaEventHeaders.OCCURRED_AT))).isEqualTo(row.getOccurredAt());
		assertThat(header(record, KafkaEventHeaders.AGGREGATE_TYPE)).isEqualTo("Member");
		assertThat(header(record, KafkaEventHeaders.AGGREGATE_ID)).isEqualTo(String.valueOf(member.getId()));
		assertThat(body.eventId()).isEqualTo(header(record, KafkaEventHeaders.EVENT_ID));
		assertThat(body.id()).isEqualTo(member.getId());
	}

	private static String header(ConsumerRecord<String, String> record, String name) {
		Header header = record.headers().lastHeader(name);
		assertThat(header).as("헤더 %s", name).isNotNull();
		return new String(header.value(), StandardCharsets.UTF_8);
	}
}
