package com.bukang.payout.out;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.payout.domain.exception.PayoutMemberNotFoundException;
import com.bukang.payout.domain.exception.PayoutMemberUnavailableException;

import lombok.extern.slf4j.Slf4j;

/**
 * 회원 서비스의 관리자 여부 내부 API를 HTTP로 부르는 어댑터 (PRO-265)
 * GET {회원 서비스}/internal/v1/member/members/{memberId}/admin → RsData.data.admin
 *
 * 결과를 셋으로 나눈다
 * - 200: admin 값 그대로 (일반 회원이어도 200 false. 403은 정산이 정한다)
 * - 404: 없거나 탈퇴한 회원 → PayoutMemberNotFoundException (404)
 * - 그 밖(타임아웃 · 연결 실패 · 5xx · 그 밖의 4xx · 빈 응답): 관리자인지 모른다 → PayoutMemberUnavailableException (503)
 * 모르는 경우를 '관리자'로 보면 회원 서비스가 멈춘 동안 일반 회원도 보류를 걸 수 있으므로, 어느 경우든 통과시키지 않는다
 *
 * 테스트 프로파일에서는 FakeMemberAdminAdapter가 대신 뜬다. dev(로컬)와 prod는 이 어댑터로 실제 회원 서비스를 부른다
 */
@Slf4j
@Component
@Profile("!test")
public class MemberAdminAdapter implements MemberAdminPort {
	static final String PATH = "/internal/v1/member/members/{memberId}/admin";
	// 다른 서비스 어댑터(캐시 · 주문 · 상품)와 같은 값. 관리자가 화면에서 기다리는 시간은 최대 8초다
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

	private final RestClient restClient;

	@Autowired
	public MemberAdminAdapter(@Value("${payout.services.member}") String memberServiceUrl) {
		this(RestClient.builder()
			.baseUrl(memberServiceUrl)
			.requestFactory(requestFactory())
			.build());
	}

	// 테스트에서 MockRestServiceServer에 묶은 RestClient를 넣는다
	MemberAdminAdapter(RestClient restClient) {
		this.restClient = restClient;
	}

	@Override
	public boolean isAdmin(long memberId) {
		RsData<AdminResponse> body;
		try {
			body = restClient.get()
				.uri(PATH, memberId)
				.retrieve()
				.body(new ParameterizedTypeReference<>() {
				});
		} catch (HttpClientErrorException.NotFound exception) {
			throw new PayoutMemberNotFoundException();
		} catch (RestClientException exception) {
			throw unavailable(memberId, exception);
		}

		if (body == null || body.data() == null || body.data().admin() == null) {
			throw unavailable(memberId, new IllegalStateException("회원 서비스의 관리자 여부 응답이 비어 있습니다."));
		}
		return body.data().admin();
	}

	// 공통 예외 처리기는 BusinessException을 로그 없이 응답으로만 바꾸므로, 원인은 여기서 남긴다
	private static PayoutMemberUnavailableException unavailable(long memberId, Exception cause) {
		log.warn("회원 서비스에서 관리자 여부를 확인하지 못했습니다. memberId={}, cause={}", memberId, cause.toString());
		return new PayoutMemberUnavailableException(cause);
	}

	private static SimpleClientHttpRequestFactory requestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(CONNECT_TIMEOUT);
		factory.setReadTimeout(READ_TIMEOUT);
		return factory;
	}

	// 회원 서비스 응답(member MemberAdminDto). 빈 응답을 구분하려고 Boolean으로 받는다
	record AdminResponse(Boolean admin) {
	}
}
