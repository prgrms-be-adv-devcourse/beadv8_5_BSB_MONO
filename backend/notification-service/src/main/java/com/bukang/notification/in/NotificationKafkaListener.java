package com.bukang.notification.in;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.notification.app.NotificationUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 모든 서비스가 보낸 알림 요청을 받아 저장한다. 알림은 잃어도 되는 메시지라 재시도·DLT 없이,
 * 읽을 수 없는 메시지(깨진 JSON, 모르는 종류)는 로그만 남기고 넘어간다.
 * 같은 메시지 두 번은 message-id 헤더(인박스 규칙, KafkaMessageId)로 거른다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationKafkaListener {
	private final JsonConverter jsonConverter;
	private final NotificationUseCase notificationUseCase;

	// groupId는 바꾸지 않는다. 바꾸면 새 그룹으로 보고 토픽을 처음부터 다시 읽는다 (auto-offset-reset: earliest)
	@KafkaListener(topics = KafkaTopics.NOTIFICATION_REQUESTED,
		groupId = "NotificationKafkaListener__handleRequested__1")
	public void handleRequested(String message,
		@Header(name = KafkaMessageId.HEADER, required = false) String messageId) {
		NotificationRequestedEvent event;
		try {
			event = jsonConverter.fromJson(message, NotificationRequestedEvent.class);
		} catch (IllegalArgumentException broken) {
			log.warn("읽을 수 없는 알림 메시지라 버립니다. message={}", message, broken);
			return;
		}
		// 헤더가 없으면(직접 넣은 메시지 등) 본문의 eventId로 거른다. NotificationKafkaSender는 둘에 같은 값을 넣는다
		notificationUseCase.save(messageId != null ? messageId : event.eventId(), event);
	}
}
