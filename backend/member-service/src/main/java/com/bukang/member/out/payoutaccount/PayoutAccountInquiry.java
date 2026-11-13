package com.bukang.member.out.payoutaccount;

import com.bukang.member.domain.PayoutAccountHolderType;

/**
 * 계좌실명조회 요청 (BUK-50)
 *
 * @param bankCode      은행 표준 코드 3자리
 * @param accountNumber 계좌번호 (숫자만)
 * @param holderType    계좌 명의 구분. 오픈뱅킹 예금주 실명번호 구분 코드를 고르는 데 쓴다
 * @param holderInfo    사업자 명의는 사업자등록번호 10자리, 본인 명의는 생년월일 6자리(yyMMdd)
 * @param applicantName 신청자 이름(본인 명의는 본인인증 이름, 사업자 명의는 상호). 키가 없는 개발 환경의 가짜 확인만 쓴다
 */
public record PayoutAccountInquiry(
	String bankCode,
	String accountNumber,
	PayoutAccountHolderType holderType,
	String holderInfo,
	String applicantName
) {
	// 계좌번호·생년월일이 로그에 찍히지 않게 기본 toString을 막는다
	@Override
	public String toString() {
		return "PayoutAccountInquiry[bankCode=" + bankCode + ", holderType=" + holderType + "]";
	}
}
