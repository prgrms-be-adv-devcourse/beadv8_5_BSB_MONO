package com.bukang.member.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bukang.member.out.InternalHttpClients;
import com.bukang.member.out.cash.CashBalancePort;
import com.bukang.member.out.cash.HttpCashBalanceAdapter;
import com.bukang.member.out.order.ActiveTicketPort;
import com.bukang.member.out.order.CrewPartyStatusPort;
import com.bukang.member.out.order.HttpActiveTicketAdapter;
import com.bukang.member.out.order.HttpCrewPartyStatusAdapter;
import com.bukang.member.out.product.HttpLowPaymentCrewAdapter;
import com.bukang.member.out.product.LowPaymentCrewPort;

/**
 * 다른 서비스 내부 API 클라이언트. 주소는 프로파일별 yaml(member.clients.*)에 있다
 * 회원 탈퇴 확인(BE-022): 예치금, 참가권. 크루 파티 만들기 제한(BUK-187): 결제율 미달 크루
 * 가짜 어댑터는 두지 않는다. 확인 없이 탈퇴되면 안 되므로, 상대 서비스가 없으면 탈퇴가 503으로 막힌다
 * RestClient는 부를 때 연결하므로, 상대 서비스가 없어도 앱은 뜬다 (테스트는 포트를 목으로 바꾼다)
 */
@Configuration
public class InternalClientConfig {
	@Bean
	public CashBalancePort cashBalancePort(
		@Value("${member.clients.cash.base-url}") String baseUrl,
		@Value("${member.clients.timeout}") Duration timeout
	) {
		return new HttpCashBalanceAdapter(InternalHttpClients.create(baseUrl, timeout));
	}

	@Bean
	public ActiveTicketPort activeTicketPort(
		@Value("${member.clients.order.base-url}") String baseUrl,
		@Value("${member.clients.timeout}") Duration timeout
	) {
		return new HttpActiveTicketAdapter(InternalHttpClients.create(baseUrl, timeout));
	}

	// 크루 나가기·내보내기 전 파티 확인 (BUK-102). 가짜 어댑터는 두지 않는다
	@Bean
	public CrewPartyStatusPort crewPartyStatusPort(
		@Value("${member.clients.order.base-url}") String baseUrl,
		@Value("${member.clients.timeout}") Duration timeout
	) {
		return new HttpCrewPartyStatusAdapter(InternalHttpClients.create(baseUrl, timeout));
	}

	@Bean
	public LowPaymentCrewPort lowPaymentCrewPort(
		@Value("${member.clients.product.base-url}") String baseUrl,
		@Value("${member.clients.timeout}") Duration timeout
	) {
		return new HttpLowPaymentCrewAdapter(InternalHttpClients.create(baseUrl, timeout));
	}
}
