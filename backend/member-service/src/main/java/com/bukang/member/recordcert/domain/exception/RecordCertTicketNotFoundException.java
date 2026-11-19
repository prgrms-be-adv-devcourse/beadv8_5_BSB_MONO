package com.bukang.member.recordcert.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// order에 없는 참가권 (BUK-274)
public class RecordCertTicketNotFoundException extends BusinessException {
	public RecordCertTicketNotFoundException() {
		super(HttpStatus.NOT_FOUND, "RECORD_CERT_TICKET_NOT_FOUND", "참가권을 찾을 수 없습니다.");
	}
}
