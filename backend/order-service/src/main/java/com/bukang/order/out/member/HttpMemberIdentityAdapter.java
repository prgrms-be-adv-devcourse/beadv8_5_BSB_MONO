package com.bukang.order.out.member;

import java.time.LocalDate;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.member.web.MemberIdHeader;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원 서비스의 내 정보 API를 회원 ID 헤더를 넣어 부른다 (GET /api/v1/member/members/me, CRU-59)
 * 회원 내부 API에 본인인증 값을 주는 길이 없어 게이트웨이가 넣는 것과 같은 헤더로 부른다.
 * shortcut: 공개 API 응답 모양에 기댄다. 회원 서비스에 내부 API가 생기면 PATH만 바꾼다
 * 200이 아니거나 연결하지 못하면 신청서를 만들 수 없으므로 503으로 다시 시도하게 한다
 * 등록은 MemberClientConfig가 한다 (order.clients.member.mode=http)
 */
@Slf4j
@RequiredArgsConstructor
public class HttpMemberIdentityAdapter implements MemberIdentityPort {
	static final String PATH = "/api/v1/member/members/me";
	static final String UNAVAILABLE_MESSAGE = "회원 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.";
	private static final ParameterizedTypeReference<RsData<MeResponse>> RESPONSE_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient restClient;

	@Override
	public MemberIdentity find(long memberId) {
		RsData<MeResponse> response;
		try {
			response = restClient.get()
				.uri(PATH)
				.header(MemberIdHeader.NAME, String.valueOf(memberId))
				.retrieve()
				.body(RESPONSE_TYPE);
		} catch (RestClientException exception) {
			log.error("회원 서비스에 본인인증 값을 묻지 못했습니다. memberId={}", memberId, exception);
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		if (response == null || response.data() == null) {
			log.error("회원 서비스의 내 정보 응답에 값이 없습니다. memberId={}", memberId);
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		MeResponse me = response.data();
		return new MemberIdentity(me.name(), me.birthDate(), me.phone());
	}

	// 회원 서비스 응답의 data (member MemberDto) 중 신청서에 넣는 칸만 받는다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record MeResponse(String name, LocalDate birthDate, String phone) {
	}
}
