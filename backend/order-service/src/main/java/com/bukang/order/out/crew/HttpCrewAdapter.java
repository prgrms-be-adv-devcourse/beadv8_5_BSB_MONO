package com.bukang.order.out.crew;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.out.InternalHttpClients;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원 서비스의 크루 내부 API를 부른다 (BUK-35, member InternalV1CrewController)
 * - 200: 응답 값 그대로 (크루원이 아니어도 200, crewMember=false)
 * - 404 CREW_NOT_FOUND: 크루가 없거나 없앤 크루 → 빈 값
 *   코드가 다른 404(경로가 바뀌어 기본 404)는 계약이 어긋난 것이라 503으로 본다
 * - 그 밖(시간 초과 · 연결 실패 · 5xx · 그 밖의 4xx · 빈 응답): 503으로 다시 시도하게 한다
 * 등록은 CrewClientConfig가 한다 (order.clients.crew.mode=http)
 */
@Slf4j
@RequiredArgsConstructor
public class HttpCrewAdapter implements CrewPort {
	static final String MEMBERSHIP_PATH = "/internal/v1/member/crews/{crewId}/members/{memberId}";
	static final String COUNT_PATH = "/internal/v1/member/crews/{crewId}/member-count";
	static final String CREW_NOT_FOUND_CODE = "CREW_NOT_FOUND";
	static final String UNAVAILABLE_MESSAGE = "회원 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.";
	private static final ParameterizedTypeReference<RsData<MembershipResponse>> MEMBERSHIP_TYPE =
		new ParameterizedTypeReference<>() {
		};
	private static final ParameterizedTypeReference<RsData<CountResponse>> COUNT_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient restClient;

	@Override
	public Optional<CrewMembership> findMembership(long crewId, long memberId) {
		Optional<MembershipResponse> data = call(() -> restClient.get()
			.uri(MEMBERSHIP_PATH, crewId, memberId)
			.retrieve()
			.body(MEMBERSHIP_TYPE), "crewId=" + crewId + ", memberId=" + memberId);
		return data.map(body -> {
			// 크루원인데 가입 시각이 없으면 "예매 시작 7일 전 가입" 판단을 할 수 없다
			if (body.crewMember() == null || body.crewMember() && body.joinedAt() == null) {
				log.error("회원 서비스의 크루원 여부 응답에 값이 없습니다. crewId={}, memberId={}", crewId, memberId);
				throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
			}
			return new CrewMembership(body.crewMember(), body.joinedAt());
		});
	}

	@Override
	public OptionalInt countMembers(long crewId) {
		Optional<CountResponse> data = call(() -> restClient.get()
			.uri(COUNT_PATH, crewId)
			.retrieve()
			.body(COUNT_TYPE), "crewId=" + crewId);
		if (data.isEmpty()) {
			return OptionalInt.empty();
		}
		if (data.get().memberCount() == null) {
			log.error("회원 서비스의 크루원 수 응답에 값이 없습니다. crewId={}", crewId);
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		return OptionalInt.of(data.get().memberCount());
	}

	// 크루 없음(404 CREW_NOT_FOUND)은 빈 값, 나머지 실패는 503
	private <T> Optional<T> call(Supplier<RsData<T>> request, String target) {
		RsData<T> response;
		try {
			response = request.get();
		} catch (HttpClientErrorException.NotFound exception) {
			if (isCrewNotFound(exception)) {
				return Optional.empty();
			}
			log.error("회원 서비스가 크루 요청에 크루 없음이 아닌 404로 답했습니다. 계약(경로)을 확인해야 합니다. {}, body={}",
				target, InternalHttpClients.abbreviate(exception.getResponseBodyAsString()), exception);
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		} catch (HttpClientErrorException exception) {
			log.error("회원 서비스가 크루 요청을 거절했습니다. 계약(경로)을 확인해야 합니다. status={}, {}",
				exception.getStatusCode(), target, exception);
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		} catch (RestClientException exception) {
			log.error("회원 서비스에 크루를 묻지 못했습니다. {}", target, exception);
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		if (response == null || response.data() == null) {
			log.error("회원 서비스의 크루 응답이 비어 있습니다. {}, response={}", target, response);
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		return Optional.of(response.data());
	}

	private static boolean isCrewNotFound(HttpClientErrorException exception) {
		try {
			ErrorResponse body = exception.getResponseBodyAs(ErrorResponse.class);
			return body != null && CREW_NOT_FOUND_CODE.equals(body.code());
		} catch (RuntimeException parseFailure) {
			return false;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ErrorResponse(String code) {
	}

	// member CrewMemberStatusDto. 빈 응답을 구분하려고 Boolean으로 받는다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record MembershipResponse(Boolean crewMember, LocalDateTime joinedAt) {
	}

	// member CrewMemberCountDto
	@JsonIgnoreProperties(ignoreUnknown = true)
	record CountResponse(Integer memberCount) {
	}
}
