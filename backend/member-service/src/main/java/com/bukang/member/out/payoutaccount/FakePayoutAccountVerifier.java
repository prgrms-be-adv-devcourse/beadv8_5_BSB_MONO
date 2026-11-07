package com.bukang.member.out.payoutaccount;

import lombok.extern.slf4j.Slf4j;

/**
 * 오픈뱅킹 키가 없을 때 쓰는 가짜 계좌 확인(목업) (BUK-50). 은행에 묻지 않고 항상 통과시킨다
 * 오픈뱅킹 이용 승인이 거절되어 운영에서도 이 목업으로 확인한다 (PayoutAccountConfig)
 * 예금주를 신청자 이름으로 돌려주므로 본인 명의 확인이 항상 통과한다. 그래서 남의 계좌를 막지 못한다
 */
@Slf4j
public class FakePayoutAccountVerifier implements PayoutAccountVerifier {
	@Override
	public String verify(PayoutAccountInquiry inquiry) {
		log.warn("오픈뱅킹 키가 없어 정산 계좌를 은행에 확인하지 않고 통과시킵니다(목업).");
		return inquiry.applicantName();
	}
}
