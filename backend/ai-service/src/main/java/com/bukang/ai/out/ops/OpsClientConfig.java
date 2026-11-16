package com.bukang.ai.out.ops;

import static com.bukang.ai.out.member.MemberClientConfig.restClient;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 도구가 부르는 서비스 주소. 가짜 모드는 없다: 서비스가 꺼져 있으면 도구가 실패하고 답은 화면에서 확인하라고 안내한다
 */
@Configuration
public class OpsClientConfig {
	@Bean
	public HttpOpsAdapter httpOpsAdapter(
		@Value("${ai.clients.order.base-url}") String orderUrl,
		@Value("${ai.clients.payout.base-url}") String payoutUrl,
		@Value("${ai.clients.inquiry.base-url}") String inquiryUrl,
		@Value("${ai.clients.member.base-url}") String memberUrl,
		@Value("${ai.clients.timeout}") Duration timeout
	) {
		return new HttpOpsAdapter(restClient(orderUrl, timeout), restClient(payoutUrl, timeout),
			restClient(inquiryUrl, timeout), restClient(memberUrl, timeout));
	}
}
