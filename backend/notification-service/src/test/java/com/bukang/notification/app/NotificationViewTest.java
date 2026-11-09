package com.bukang.notification.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.notification.domain.Notification;
import com.bukang.notification.dto.NoticeDto;

class NotificationViewTest {
	private final LocalDateTime now = LocalDateTime.of(2026, 11, 4, 15, 0);

	@Test
	@DisplayName("오늘 알림은 방금·N분 전·N시간 전, 어제 이전은 M/D로 보인다")
	void at() {
		assertThat(NotificationView.at(now.minusSeconds(30), now, true)).isEqualTo("방금");
		assertThat(NotificationView.at(now.plusMinutes(2), now, true)).isEqualTo("방금");
		assertThat(NotificationView.at(now.minusMinutes(59), now, true)).isEqualTo("59분 전");
		assertThat(NotificationView.at(now.minusHours(5), now, true)).isEqualTo("5시간 전");
		assertThat(NotificationView.at(LocalDateTime.of(2026, 10, 5, 23, 0), now, false)).isEqualTo("10/5");
	}

	@Test
	@DisplayName("문의 알림은 받은 문의는 Inbox, 답변은 Bell 아이콘이다")
	void inquiryIcons() {
		assertThat(NotificationView.icon(NotificationKind.INQUIRY_RECEIVED)).isEqualTo("Inbox");
		assertThat(NotificationView.icon(NotificationKind.INQUIRY_ANSWERED)).isEqualTo("Bell");
	}

	@Test
	@DisplayName("종류 이름과 서울 시각(+09:00) 발송 시각을 함께 준다")
	void kindAndSentAt() {
		LocalDateTime occurredAt = LocalDateTime.of(2026, 11, 2, 10, 0);
		Notification notification = Notification.from("m-1", new NotificationRequestedEvent("m-1", 1L,
			NotificationKind.CREW_JOINED, "크루에 가입했어요", "러너크루의 크루원이 됐어요.", "/crews/3", occurredAt));

		NoticeDto dto = NotificationView.toNotice(notification, now);

		assertThat(dto.kind()).isEqualTo("CREW_JOINED");
		assertThat(dto.sentAt().toString()).isEqualTo("2026-11-02T10:00+09:00");
	}
}
