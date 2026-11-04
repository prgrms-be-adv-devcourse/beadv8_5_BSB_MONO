package com.bukang.notification.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 없는 알림이거나 다른 회원의 알림이다. 남의 알림이 있는지도 알려 주지 않도록 둘 다 404로 답한다
public class NotificationNotFoundException extends BusinessException {
	public NotificationNotFoundException() {
		super(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다.");
	}
}
