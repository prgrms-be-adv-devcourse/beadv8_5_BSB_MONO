package com.bukang.order.out.seat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.domain.exception.ParticipationNotPayableException;
import com.bukang.order.domain.exception.RaceCourseNotFoundException;
import com.bukang.order.out.InternalHttpClients;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 상품 서비스(B)의 내부 API로 파티 크루 자리를 한꺼번에 잡는다 (BUK-105, 상품 PR #356)
 * POST /internal/v1/product/crew-holds {partyId, crewId, raceId, expiresAt, seats:[{participationId, raceCourseId}]}
 * → {held, expiresAt, shortages:[{raceCourseId, needed, remaining}]}
 * 자리가 모자란 것도 오류가 아니라 200과 held=false로 온다. 같은 파티 ID로 멱등이라(CrewSeatHoldPort 계약),
 * 응답을 못 받으면(시간 초과·5xx) 주문을 바꾸지 않고 503으로 끝내 다시 누르게 한다
 * 등록은 ProductClientConfig가 한다 (order.clients.product.mode=http)
 */
@Slf4j
@RequiredArgsConstructor
public class HttpCrewSeatHoldAdapter implements CrewSeatHoldPort {
	static final String PATH = "/internal/v1/product/crew-holds";
	static final String REJECTED_MESSAGE = "크루 자리를 확보할 수 없는 신청입니다.";

	private static final ParameterizedTypeReference<RsData<HoldBody>> RESPONSE_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient restClient;

	@Override
	public CrewSeatHoldResult holdAll(long partyId, long crewId, long raceId, LocalDateTime expiresAt,
		List<Seat> seats) {
		RsData<HoldBody> response;
		try {
			response = restClient.post()
				.uri(PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new HoldRequest(partyId, crewId, raceId, expiresAt, seats))
				.retrieve()
				.body(RESPONSE_TYPE);
		} catch (HttpClientErrorException exception) {
			if (exception.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
				throw new RaceCourseNotFoundException("존재하지 않는 종목입니다.");
			}
			// 409(크루 예매 기간 아님·판매 중지, 다른 자리 목록으로 이미 잡은 파티 등)는 다시 해도 안 되므로 B의 이유를 그대로 알린다
			if (exception.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
				throw new ParticipationNotPayableException(messageOf(exception));
			}
			// 400(끝 시각이 상품 시계로 이미 지남, 같은 신청 두 번 등)은 주문이 보낸 값이 계약과 어긋났다
			log.error("상품 서비스가 크루 자리 확보 요청을 거절했습니다. 계약을 확인해야 합니다. status={}, partyId={}, body={}",
				exception.getStatusCode(), partyId,
				InternalHttpClients.abbreviate(exception.getResponseBodyAsString()));
			throw new ExternalServiceUnavailableException(HttpSeatHoldAdapter.UNAVAILABLE_MESSAGE);
		} catch (RestClientException exception) {
			log.warn("상품 서비스에 크루 자리 확보를 요청하지 못했습니다. partyId={}, cause={}", partyId, exception.toString());
			throw new ExternalServiceUnavailableException(HttpSeatHoldAdapter.UNAVAILABLE_MESSAGE);
		}
		HoldBody body = response == null ? null : response.data();
		if (body == null || body.held() == null || (body.held() && body.expiresAt() == null)) {
			log.error("상품 서비스의 크루 자리 확보 응답 형식이 계약과 다릅니다. partyId={}, response={}", partyId, response);
			throw new ExternalServiceUnavailableException(HttpSeatHoldAdapter.UNAVAILABLE_MESSAGE);
		}
		if (body.held()) {
			return CrewSeatHoldResult.held(body.expiresAt());
		}
		List<ShortageBody> shortages = body.shortages() == null ? List.of() : body.shortages();
		return CrewSeatHoldResult.shortOf(shortages.stream().collect(Collectors.toMap(ShortageBody::raceCourseId,
			shortage -> new Shortage(shortage.needed(), shortage.remaining()), (first, second) -> first)));
	}

	// B 오류 응답(RsData)의 message. 읽지 못하면 기본 문구를 쓴다
	private static String messageOf(HttpClientErrorException exception) {
		try {
			ErrorBody body = exception.getResponseBodyAs(ErrorBody.class);
			return body == null || body.message() == null || body.message().isBlank() ? REJECTED_MESSAGE
				: body.message();
		} catch (RuntimeException ignored) {
			return REJECTED_MESSAGE;
		}
	}

	record HoldRequest(long partyId, long crewId, long raceId, LocalDateTime expiresAt, List<Seat> seats) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record HoldBody(Boolean held, LocalDateTime expiresAt, List<ShortageBody> shortages) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ShortageBody(long raceCourseId, int needed, int remaining) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ErrorBody(String message) {
	}
}
