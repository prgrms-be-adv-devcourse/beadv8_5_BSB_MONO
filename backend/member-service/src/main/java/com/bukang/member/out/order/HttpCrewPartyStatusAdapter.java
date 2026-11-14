package com.bukang.member.out.order;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.member.crew.domain.exception.CrewPartyCheckFailedException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주문 서비스 내부 API로 크루원의 파티 상태를 묻는다:
 * GET /internal/v1/order/crews/{crewId}/members/{memberId}/party-status → {canLeave, canKick, blockingParties}
 * 연결 실패, 시간 초과, 4xx·5xx, 값이 빠진 응답은 모두 "모름"이라 503으로 알린다 (막는 쪽으로 실패)
 * 등록은 InternalClientConfig가 한다
 */
@Slf4j
@RequiredArgsConstructor
public class HttpCrewPartyStatusAdapter implements CrewPartyStatusPort {
	static final String PATH = "/internal/v1/order/crews/{crewId}/members/{memberId}/party-status";
	public static final String UNAVAILABLE_MESSAGE = "파티 상태를 확인하지 못했습니다. 잠시 뒤 다시 시도해주세요.";
	private static final ParameterizedTypeReference<RsData<Status>> RESPONSE_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient restClient;

	@Override
	public PartyStatus findStatus(long crewId, long memberId) {
		RsData<Status> response;
		try {
			response = restClient.get()
				.uri(PATH, crewId, memberId)
				.retrieve()
				.body(RESPONSE_TYPE);
		} catch (RestClientException exception) {
			log.warn("주문 서비스에 파티 상태를 묻지 못했습니다. crewId={}, memberId={}, cause={}",
				crewId, memberId, exception.toString());
			throw new CrewPartyCheckFailedException(UNAVAILABLE_MESSAGE);
		}
		Status data = response == null ? null : response.data();
		if (data == null || data.canLeave() == null || data.canKick() == null) {
			log.error("주문 서비스의 파티 상태 응답에 값이 없습니다. crewId={}, memberId={}, response={}",
				crewId, memberId, response);
			throw new CrewPartyCheckFailedException(UNAVAILABLE_MESSAGE);
		}
		return new PartyStatus(data.canLeave(), data.canKick());
	}

	// 주문 서비스 응답의 data (order CrewMemberPartyStatusDto). 판정에 쓰는 값만 받는다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Status(Boolean canLeave, Boolean canKick) {
	}
}
