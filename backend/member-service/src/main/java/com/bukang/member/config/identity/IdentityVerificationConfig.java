package com.bukang.member.config.identity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bukang.member.out.InternalHttpClients;
import com.bukang.member.out.identity.HttpPortOneIdentityClient;
import com.bukang.member.out.identity.PortOneIdentityClient;

/**
 * 포트원 본인인증 조회 클라이언트 (BUK-33). API Secret이 비어도 앱은 뜨고, 본인인증 확인만 503으로 실패한다
 */
@Configuration
public class IdentityVerificationConfig {
	@Bean
	public PortOneIdentityClient portOneIdentityClient(IdentityVerificationProperties properties) {
		IdentityVerificationProperties.PortOne portone = properties.portone();
		return new HttpPortOneIdentityClient(InternalHttpClients.create(portone.baseUrl(), portone.timeout()),
			portone.apiSecret(), portone.storeId());
	}
}
