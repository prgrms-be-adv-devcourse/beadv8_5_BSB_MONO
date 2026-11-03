package com.bukang.member.config.payoutaccount;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 판매자 정산 계좌 확인 설정 (member.payout-account.*, BE-031). 키 값은 .env에만 두고 yaml은 환경 변수 이름만 적는다
 *
 * @param dailyLimit  회원 한 명이 기간 안에 할 수 있는 계좌 확인 횟수
 * @param window      횟수를 세는 기간. 첫 확인부터 센다
 * @param openbanking 금융결제원 오픈뱅킹 설정
 */
@ConfigurationProperties("member.payout-account")
public record PayoutAccountProperties(
	@DefaultValue("5") int dailyLimit,
	@DefaultValue("24h") Duration window,
	@DefaultValue OpenBanking openbanking
) {
	/**
	 * 공식 문서에서 확인하지 못한 값(이용기관 코드, 예금주 실명번호 구분 코드)은 기본값을 두지 않는다. 비어 있으면 확인이 503으로 실패한다
	 *
	 * @param baseUrl            오픈뱅킹 서버 주소 (테스트베드 또는 운영)
	 * @param clientId           2자 인증 클라이언트 아이디. 비어 있으면 키가 없는 것으로 본다
	 * @param clientSecret       2자 인증 클라이언트 비밀번호
	 * @param useCode            이용기관 코드 10자리. 은행거래고유번호 앞부분이다
	 * @param birthHolderType    생년월일로 조회할 때의 예금주 실명번호 구분 코드
	 * @param businessHolderType 사업자등록번호로 조회할 때의 예금주 실명번호 구분 코드
	 * @param timeout            연결·응답 시간 제한
	 */
	public record OpenBanking(
		@DefaultValue("https://testapi.openbanking.or.kr") String baseUrl,
		@DefaultValue("") String clientId,
		@DefaultValue("") String clientSecret,
		@DefaultValue("") String useCode,
		@DefaultValue("") String birthHolderType,
		@DefaultValue("") String businessHolderType,
		@DefaultValue("3s") Duration timeout
	) {
		public boolean hasKeys() {
			return !clientId.isBlank() && !clientSecret.isBlank();
		}

		// 키가 들어간 기본 toString이 로그에 찍히지 않게 막는다
		@Override
		public String toString() {
			return "OpenBanking[baseUrl=" + baseUrl + "]";
		}
	}
}
