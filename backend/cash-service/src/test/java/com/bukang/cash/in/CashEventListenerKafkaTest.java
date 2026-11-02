package com.bukang.cash.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.bukang.cash.out.CashMemberRepository;
import com.bukang.common.shared.kafka.KafkaTopics;

// 실제 임베디드 Kafka 브로커로 CashEventListener가 토픽을 올바로 구독하는지 확인한다.
// 토픽 이름이 안 맞으면(생산자-소비자 불일치) 리스너가 메시지를 못 받아 타임아웃으로 실패한다.
// - 다른 테스트의 공유 H2(bukang-cash-test)와 분리된 전용 DB를 쓴다 (create-drop이 다른 테스트 데이터를 건드리지 않게)
// - 전역 auto-startup은 그대로 꺼 두고, 이 테스트에 필요한 리스너 하나만 직접 켠다 (불필요한 컨슈머 리밸런싱 방지)
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {KafkaTopics.MEMBER_MEMBER_JOINED})
@TestPropertySource(properties =
	"spring.datasource.url=jdbc:h2:mem:bukang-cash-kafka-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class CashEventListenerKafkaTest {
	private static final String MEMBER_JOINED_GROUP_ID = "CashEventListener__handleMemberJoined__1";

	@Autowired
	private KafkaListenerEndpointRegistry registry;
	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;
	@Autowired
	private CashMemberRepository cashMemberRepository;

	@BeforeEach
	void startMemberJoinedListener() {
		registry.getListenerContainers().stream()
			.filter(container -> MEMBER_JOINED_GROUP_ID.equals(container.getGroupId()))
			.filter(container -> !container.isRunning())
			.forEach(container -> container.start());
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
}
