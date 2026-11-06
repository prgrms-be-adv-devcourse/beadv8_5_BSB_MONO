package com.bukang.common.shared.notification.event;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import com.bukang.common.shared.notification.NotificationKind;

/**
 * 회원 한 명에게 알림을 띄워 달라는 메시지 (토픽 push.notification.requested, key = 회원 ID)
 * 보내는 서비스가 트랜잭션 안에서 EventPublisher.publish(NotificationRequestedEvent.of(...))로 발행하면,
 * common의 NotificationKafkaSender가 커밋된 뒤에 Kafka로 보낸다. 롤백되면 보내지 않는다.
 * 알림은 잃어도 되는 메시지라 아웃박스를 쓰지 않는다 (Kafka가 멈춘 동안 보낸 알림은 사라진다, 11/04 결정)
 *
 * @param eventId 메시지 ID. 알림 서비스가 같은 메시지를 두 번 받아도 하나만 저장한다 (헤더 message-id와 같은 값)
 * @param title 알림 제목 (예: 환불이 끝났어요)
 * @param body 알림 내용. 줄바꿈(\n)을 그대로 보여 준다
 * @param href 누르면 갈 프론트 경로 (예: /me/deposit)
 * @param occurredAt 일이 생긴 시각 (한국 시간, 서버 시간대와 상관없이 Asia/Seoul로 만든다). 알림 화면의 시각·오늘/이전 묶음이 이 값을 따른다
 */
public record NotificationRequestedEvent(
	String eventId,
	long memberId,
	NotificationKind kind,
	String title,
	String body,
	String href,
	LocalDateTime occurredAt
) {
	public static NotificationRequestedEvent of(long memberId, NotificationKind kind, String title, String body,
		String href) {
		return new NotificationRequestedEvent(UUID.randomUUID().toString(), memberId, kind, title, body, href,
			LocalDateTime.now(ZoneId.of("Asia/Seoul")));
	}
}
