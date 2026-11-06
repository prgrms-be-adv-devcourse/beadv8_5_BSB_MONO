package com.bukang.notification.domain;

import java.time.LocalDateTime;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;
import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 한 명에게 보여 줄 알림 하나. 받은 메시지 하나가 알림 하나다
 * message_id 유니크가 인박스 역할을 한다: 같은 메시지를 두 번 받으면 두 번째 저장이 실패해 알림은 하나만 남는다
 */
@Entity
@Table(name = "notification_notification",
	uniqueConstraints = @UniqueConstraint(name = "uk_notification_message", columnNames = "message_id"),
	indexes = @Index(name = "idx_notification_member_occurred", columnList = "member_id, occurred_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseIdAndTime {
	@Column(name = "message_id", nullable = false, length = 36)
	private String messageId;

	@Column(name = "member_id", nullable = false)
	private long memberId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private NotificationKind kind;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false, length = 500)
	private String body;

	@Column(nullable = false, length = 300)
	private String href;

	// 일이 생긴 시각 (보낸 서비스 기준). 화면의 시각·오늘/이전 묶음·7일 정리가 이 값을 따른다
	@Column(name = "occurred_at", nullable = false)
	private LocalDateTime occurredAt;

	// 읽은 시각. 비어 있으면 안 읽은 알림이다
	@Column(name = "read_at")
	private LocalDateTime readAt;

	public static Notification from(String messageId, NotificationRequestedEvent event) {
		Notification notification = new Notification();
		notification.messageId = messageId;
		notification.memberId = event.memberId();
		notification.kind = event.kind();
		notification.title = event.title();
		notification.body = event.body();
		notification.href = event.href();
		notification.occurredAt = event.occurredAt();
		return notification;
	}

	// 이미 읽었으면 처음 읽은 시각을 그대로 둔다
	public void markRead(LocalDateTime now) {
		if (readAt == null) {
			readAt = now;
		}
	}

	public boolean isUnread() {
		return readAt == null;
	}
}
