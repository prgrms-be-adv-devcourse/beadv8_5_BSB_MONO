package com.bukang.notification.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.common.shared.notification.NotificationKind;

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
}
