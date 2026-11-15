package com.bukang.member.domain;

import java.time.LocalDateTime;

/**
 * 계좌실명조회를 통과한 정산 계좌 (BUK-50)
 *
 * @param bankCode      은행 표준 코드 3자리
 * @param accountNumber 계좌번호 (숫자만). 판매자 신청 행에 암호화해 저장한다
 * @param holderName    은행이 돌려준 예금주 이름
 * @param holderType    계좌 명의 구분
 * @param verifiedAt    확인 시각
 */
public record VerifiedPayoutAccount(
	String bankCode,
	String accountNumber,
	String holderName,
	PayoutAccountHolderType holderType,
	LocalDateTime verifiedAt
) {
	// 계좌번호·예금주 이름이 로그에 찍히지 않게 기본 toString을 막는다
	@Override
	public String toString() {
		return "VerifiedPayoutAccount[bankCode=" + bankCode + ", holderType=" + holderType + "]";
	}
}
