package com.bukang.board.out.member;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import com.bukang.common.global.rsdata.RsData;

import lombok.RequiredArgsConstructor;

/**
 * 회원 서비스 내부 API로 회원 복사본 재료를 받는다. 오류·제한 시간 초과는 RestClientException 그대로 던지고,
 * 응답에 data나 members가 없으면 빈 목록으로 착각하지 않도록 IllegalStateException을 던진다
 */
@RequiredArgsConstructor
public class HttpMemberReplicaAdapter implements MemberReplicaPort {
	static final String PATH = "/internal/v1/member/members/replicas";
	private static final ParameterizedTypeReference<RsData<MemberReplicaPage>> RESPONSE_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient restClient;

	@Override
	public MemberReplicaPage fetch(long afterId, int size) {
		RsData<MemberReplicaPage> response = restClient.get()
			.uri(uriBuilder -> uriBuilder.path(PATH).queryParam("afterId", afterId).queryParam("size", size).build())
			.retrieve()
			.body(RESPONSE_TYPE);
		if (response == null || response.data() == null || response.data().members() == null) {
			throw new IllegalStateException("회원 서비스의 회원 복사본 응답에 값이 없습니다. afterId=" + afterId);
		}
		return response.data();
	}
}
