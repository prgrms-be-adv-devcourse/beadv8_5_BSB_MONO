package com.bukang.notification.app;

import java.time.Duration;
import java.time.LocalDateTime;

import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.notification.domain.Notification;
import com.bukang.notification.dto.NoticeDto;

/**
 * 저장한 알림을 화면 모양(Notice)으로 바꾼다. 시각과 묶음은 한국 시간 now를 받아 서버가 정한다
 */
final class NotificationView {
	private NotificationView() {
	}

	static NoticeDto toNotice(Notification notification, LocalDateTime now) {
		LocalDateTime at = notification.getOccurredAt();
		boolean today = at.toLocalDate().equals(now.toLocalDate());
		return new NoticeDto(String.valueOf(notification.getId()), icon(notification.getKind()),
			notification.getTitle(), notification.getBody(), at(at, now, today), today ? "today" : "earlier",
			notification.getHref(), notification.isUnread());
	}

	// 오늘이면 방금·N분 전·N시간 전, 어제 이전이면 M/D (시안 알림 화면)
	static String at(LocalDateTime at, LocalDateTime now, boolean today) {
		if (!today) {
			return at.getMonthValue() + "/" + at.getDayOfMonth();
		}
		// 보낸 서버 시계가 조금 빠르면 미래 시각일 수 있어 0으로 본다
		long minutes = Math.max(0, Duration.between(at, now).toMinutes());
		if (minutes < 1) {
			return "방금";
		}
		if (minutes < 60) {
			return minutes + "분 전";
		}
		return minutes / 60 + "시간 전";
	}

	// 프론트 shared/ui/icon의 이름. 종류를 더하면 switch가 컴파일 오류로 알려 준다
	static String icon(NotificationKind kind) {
		return switch (kind) {
			case REFUND_COMPLETED, WITHDRAWAL_COMPLETED -> "CircleCheck";
			case RACE_CANCELED -> "CircleAlert";
			case RACE_SCHEDULE_CHANGED -> "Calendar";
			case CREW_JOINED -> "Users";
			case PARTY_PAYMENT_REQUESTED -> "Bell";
			case PARTY_PAYMENT_DEADLINE -> "TriangleAlert";
		};
	}
}
