package com.bukang.member.domain;

// 정산 계좌의 명의 (BUK-50). 사업자 명의는 사업자만 고를 수 있다
public enum PayoutAccountHolderType {
	// 사업자등록번호로 조회해 은행이 맞다고 답하면 통과
	BUSINESS,
	// 본인인증 생년월일로 조회하고, 예금주 이름이 본인인증 이름과 같으면 통과
	PERSONAL
}
