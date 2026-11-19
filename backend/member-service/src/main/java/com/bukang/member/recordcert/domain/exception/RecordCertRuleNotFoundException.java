package com.bukang.member.recordcert.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 판매자가 기록증 규칙을 두지 않은 종목. 서버 확인까지 가지 않는다 (BUK-274)
public class RecordCertRuleNotFoundException extends BusinessException {
	public RecordCertRuleNotFoundException() {
		super(HttpStatus.CONFLICT, "RECORD_CERT_RULE_NOT_FOUND", "기록증이 필요 없는 종목입니다.");
	}
}
