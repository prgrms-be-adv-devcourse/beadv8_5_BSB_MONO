package com.bukang.queue.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
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

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.common.shared.race.event.RaceChangedEvent;
import com.bukang.common.shared.race.event.RaceCourseChangedEvent;
import com.bukang.queue.domain.QueueInboxKafkaEvent;
import com.bukang.queue.domain.QueueRace;
import com.bukang.queue.domain.QueueRaceCourse;
import com.bukang.queue.out.QueueInboxKafkaEventRepository;
import com.bukang.queue.out.QueueRaceCourseRepository;
import com.bukang.queue.out.QueueRaceRepository;

/**
 * 실제 임베디드 Kafka 브로커로, 상품이 붙이는 message-id 헤더가 리스너까지 오고 인박스가 같은 메시지를 한 번만 남기는지 확인한다.
 * 같은 메시지를 두 번 보낸 뒤 마지막 메시지를 하나 더 보내고, 마지막 메시지가 반영될 때까지 기다린다.
 * 파티션이 1개라 보낸 순서대로 처리하므로, 마지막 메시지가 반영됐으면 앞의 중복 메시지도 이미 처리된 것이다.
 * 최종 값은 인박스가 없어도(수정 일시 비교만으로도) 같으므로, 인박스에 남은 메시지 ID까지 확인한다
 * (헤더가 리스너에 오지 않았으면 인박스는 0행이다).
 * - 다른 테스트의 공유 H2(bukang-queue-test)와 분리된 전용 DB를 쓴다 (새 컨텍스트의 create-drop이 다른 테스트 테이블을 건드리지 않게)
 * - Redis는 처음 명령을 보낼 때 연결하고 이 테스트는 Redis를 쓰지 않으므로 컨테이너를 띄우지 않는다
 * - 전역 auto-startup은 그대로 꺼 두고, 대회·종목 본 리스너(재시도 리스너 제외)만 직접 켠다
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {KafkaTopics.RACE_CHANGED, KafkaTopics.RACE_COURSE_CHANGED})
@TestPropertySource(properties =
	"spring.datasource.url=jdbc:h2:mem:bukang-queue-kafka-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class QueueKafkaInboxTest {
	// QueueKafkaConsumer의 groupId (재시도 리스너는 그룹 이름에 재시도 접미사가 붙어 걸러진다)
	private static final String GROUP_ID = "queue-service";
	private static final long RACE_ID = 1;
	private static final long RACE_COURSE_ID = 11;
	private static final LocalDateTime INDIVIDUAL_OPEN_AT = LocalDateTime.of(2026, 10, 20, 10, 0);
	private static final LocalDateTime APPLICATION_CLOSE_AT = LocalDateTime.of(2026, 11, 1, 18, 0);
	private static final Duration WAIT = Duration.ofSeconds(10);
	// 상품 아웃박스가 붙이는 message-id ({서비스}-outbox-{UUID}). 서로 다른 메시지 A·B
	private static final String MESSAGE_ID_A = "product-outbox-7c9e6679-7425-40de-944b-e07fc1f90a01";
	private static final String MESSAGE_ID_B = "product-outbox-7c9e6679-7425-40de-944b-e07fc1f90b02";

	@Autowired
	private KafkaListenerEndpointRegistry registry;
	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;
	@Autowired
	private JsonConverter jsonConverter;
	@Autowired
	private QueueRaceRepository queueRaceRepository;
	@Autowired
	private QueueRaceCourseRepository queueRaceCourseRepository;
	@Autowired
	private QueueInboxKafkaEventRepository queueInboxKafkaEventRepository;

	@BeforeEach
	void startQueueListeners() {
		registry.getListenerContainers().stream()
			.filter(container -> GROUP_ID.equals(container.getGroupId()))
			.filter(container -> !container.isRunning())
			.forEach(container -> container.start());
	}

	@AfterEach
	void cleanUp() {
		queueRaceRepository.deleteAll();
		queueRaceCourseRepository.deleteAll();
		queueInboxKafkaEventRepository.deleteAll();
	}

	@Test
	@DisplayName("대회: 판매 예정(10시) 메시지를 같은 message-id(A)로 두 번 보낸 뒤 판매 중(11시) 메시지를 B로 보내면, "
		+ "대회는 판매 중·11시가 되고 인박스에는 A, B 두 행만 남는다")
	void raceMessageWithSameIdIsRecordedOnce() {
		String scheduled = raceMessage(RaceSaleStatus.SCHEDULED, at(10));
		String onSale = raceMessage(RaceSaleStatus.ON_SALE, at(11));

		send(KafkaTopics.RACE_CHANGED, scheduled, MESSAGE_ID_A);
		send(KafkaTopics.RACE_CHANGED, scheduled, MESSAGE_ID_A);   // 재전달 (인박스가 건너뛴다)
		send(KafkaTopics.RACE_CHANGED, onSale, MESSAGE_ID_B);

		// 마지막 메시지(11시)가 반영될 때까지 기다린다
		await().atMost(WAIT).untilAsserted(() ->
			assertThat(queueRaceRepository.findById(RACE_ID)).get().usingRecursiveComparison().isEqualTo(
				new QueueRace(RACE_ID, RaceSaleStatus.ON_SALE, INDIVIDUAL_OPEN_AT, APPLICATION_CLOSE_AT, at(11))));
		assertThat(queueInboxKafkaEventRepository.findAll())
			.extracting(QueueInboxKafkaEvent::getMessageId)
			.containsExactlyInAnyOrder(MESSAGE_ID_A, MESSAGE_ID_B);
	}

	@Test
	@DisplayName("종목: 등록(10시) 메시지를 같은 message-id(A)로 두 번 보낸 뒤 삭제(12시) 메시지를 B로 보내면, "
		+ "종목은 deleted=true·12시가 되고 인박스에는 A, B 두 행만 남는다")
	void raceCourseMessageWithSameIdIsRecordedOnce() {
		String registered = raceCourseMessage(at(10), false);
		String deleted = raceCourseMessage(at(12), true);

		send(KafkaTopics.RACE_COURSE_CHANGED, registered, MESSAGE_ID_A);
		send(KafkaTopics.RACE_COURSE_CHANGED, registered, MESSAGE_ID_A);   // 재전달 (인박스가 건너뛴다)
		send(KafkaTopics.RACE_COURSE_CHANGED, deleted, MESSAGE_ID_B);

		// 마지막 메시지(12시 삭제)가 반영될 때까지 기다린다
		await().atMost(WAIT).untilAsserted(() ->
			assertThat(queueRaceCourseRepository.findById(RACE_COURSE_ID)).get().usingRecursiveComparison().isEqualTo(
				new QueueRaceCourse(RACE_COURSE_ID, RACE_ID, at(12), true)));
		assertThat(queueInboxKafkaEventRepository.findAll())
			.extracting(QueueInboxKafkaEvent::getMessageId)
			.containsExactlyInAnyOrder(MESSAGE_ID_A, MESSAGE_ID_B);
	}

	// 상품 릴레이처럼 message-id 헤더를 붙여 보낸다 (key는 대회 ID)
	private void send(String topic, String payload, String messageId) {
		kafkaTemplate.send(KafkaMessageId.record(topic, String.valueOf(RACE_ID), payload, messageId));
	}

	private String raceMessage(RaceSaleStatus saleStatus, LocalDateTime updatedAt) {
		return jsonConverter.toJson(
			new RaceChangedEvent(RACE_ID, saleStatus, INDIVIDUAL_OPEN_AT, APPLICATION_CLOSE_AT, updatedAt));
	}

	private String raceCourseMessage(LocalDateTime updatedAt, boolean deleted) {
		return jsonConverter.toJson(new RaceCourseChangedEvent(RACE_COURSE_ID, RACE_ID, updatedAt, deleted));
	}

	// 메시지의 수정 일시. 같은 날(2026-10-15)의 시각만 바꿔 어느 메시지가 먼저인지 나타낸다
	private static LocalDateTime at(int hour) {
		return LocalDateTime.of(2026, 10, 15, hour, 0);
	}
}
