package com.bukang.common.shared.member.event;

import java.time.LocalDateTime;

/**
 * 회원의 마케팅 수신 동의 변경 이벤트
 */
public record MarketingConsentChangedEvent(
	String eventId,
	long memberId,
	boolean agreed,
	LocalDateTime changedAt
) {
}
