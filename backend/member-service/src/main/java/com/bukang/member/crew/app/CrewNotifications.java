package com.bukang.member.crew.app;

import com.bukang.common.shared.notification.NotificationKind;
import com.bukang.common.shared.notification.event.NotificationRequestedEvent;
import com.bukang.member.crew.domain.Crew;

// 크루 알림 문구·링크
final class CrewNotifications {
	private CrewNotifications() {
	}

	static NotificationRequestedEvent joined(long memberId, Crew crew) {
		return NotificationRequestedEvent.of(memberId, NotificationKind.CREW_JOINED,
			"크루에 가입했어요", crew.getName() + "의 크루원이 됐어요.", "/crews/" + crew.getId());
	}
}
