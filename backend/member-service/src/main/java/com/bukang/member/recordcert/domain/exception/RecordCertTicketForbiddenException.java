package com.bukang.member.recordcert.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 남의 참가권으로 낸 제출. 남의 자격을 대신 증명하지 못하게 막는다 (BUK-274)
public class RecordCertTicketForbiddenException extends BusinessException {
	public RecordCertTicketForbiddenException() {
		super(HttpStatus.FORBIDDEN, "RECORD_CERT_TICKET_FORBIDDEN", "내 참가권으로만 기록증을 제출할 수 있습니다.");
	}
}
