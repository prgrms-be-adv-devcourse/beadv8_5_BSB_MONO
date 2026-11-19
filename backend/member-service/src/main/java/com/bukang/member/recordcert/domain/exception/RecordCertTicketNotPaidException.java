package com.bukang.member.recordcert.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 결제 완료가 아닌 참가권 (BUK-274)
public class RecordCertTicketNotPaidException extends BusinessException {
	public RecordCertTicketNotPaidException() {
		super(HttpStatus.CONFLICT, "RECORD_CERT_TICKET_NOT_PAID",
			"결제를 마친 참가권으로만 기록증을 제출할 수 있습니다. 결제 대기 · 취소 중 · 취소된 참가권은 낼 수 없습니다.");
	}
}
