package com.bukang.member.config.identity;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 가입 본인인증 설정 (member.identity.*, BUK-33). 키 값은 .env에만 두고 yaml은 환경 변수 이름만 적는다
 *
 * @param uniqueCiCheck 켜면 같은 사람(CI 해시)이 두 번째 계정을 만들 수 없다. 테스트 채널 CI가 모두 같은 값이면 끈다
 * @param ciHashKey CI를 해시할 때 섞는 비밀 키. 비어 있으면 본인인증 확인이 실패한다
 * @param portone 포트원 V2 본인인증 조회 설정
 */
@ConfigurationProperties("member.identity")
public record IdentityVerificationProperties(
	@DefaultValue("true") boolean uniqueCiCheck,
	@DefaultValue("") String ciHashKey,
	@DefaultValue PortOne portone
) {
	/**
	 * @param baseUrl 포트원 REST API 주소
	 * @param apiSecret V2 API Secret. Authorization: PortOne {apiSecret} 헤더로 보낸다
	 * @param storeId 조회할 상점 아이디. 비우면 API Secret의 상점으로 조회한다
	 * @param timeout 조회 연결·응답 시간 제한
	 */
	public record PortOne(
		@DefaultValue("https://api.portone.io") String baseUrl,
		@DefaultValue("") String apiSecret,
		@DefaultValue("") String storeId,
		@DefaultValue("3s") Duration timeout
	) {
	}
}
