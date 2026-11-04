package com.bukang.member.out.payoutaccount;

import lombok.extern.slf4j.Slf4j;

/**
 * 오픈뱅킹 키가 없는 개발 환경용 가짜 확인 (BUK-50). 항상 통과시켜 팀원이 키 없이도 판매자를 만들 수 있게 한다
 * 운영(prod)에서는 쓰지 않는다 (PayoutAccountConfig)
 */
@Slf4j
public class FakePayoutAccountVerifier implements PayoutAccountVerifier {
	@Override
	public String verify(PayoutAccountInquiry inquiry) {
		log.warn("오픈뱅킹 키가 없어 정산 계좌를 확인하지 않고 통과시킵니다. 개발 환경에서만 이렇게 동작합니다.");
		return inquiry.applicantName();
	}
}
