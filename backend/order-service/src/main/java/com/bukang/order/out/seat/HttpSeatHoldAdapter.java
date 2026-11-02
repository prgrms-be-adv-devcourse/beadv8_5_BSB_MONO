package com.bukang.order.out.seat;

import java.time.LocalDateTime;

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
 * 상품 서비스(B)의 내부 API로 자리를 확보·판매한다 (PRO-165, B PRO-96)
 * POST /internal/v1/product/holds {raceCourseId, participationId, expiresAt} → {held, expiresAt}
 * POST /internal/v1/product/holds/{participationId}/sell → {result: SOLD | EXPIRED}
 * 매진·기한 지남은 B가 오류가 아니라 200과 결과로 준다. 두 요청 모두 참가 신청 ID로 멱등이라,
 * 응답을 못 받으면(시간 초과·5xx) 주문을 바꾸지 않고 503으로 끝내 같은 요청을 다시 하게 한다
 * 판매 자리 복구와 결제 안 한 자리 해제는 API가 아니라 주문 아웃박스 → Kafka로 알린다 (PRO-166, PRO-270)
 * 등록은 ProductClientConfig가 한다 (order.clients.product.mode=http)
 */
@Slf4j
@RequiredArgsConstructor
public class HttpSeatHoldAdapter implements SeatHoldPort {
	static final String HOLD_PATH = "/internal/v1/product/holds";
	static final String SELL_PATH = "/internal/v1/product/holds/{participationId}/sell";
	static final String UNAVAILABLE_MESSAGE = "상품 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.";
	static final String HOLD_REJECTED_MESSAGE = "자리를 확보할 수 없는 신청입니다.";

	private static final ParameterizedTypeReference<RsData<HoldBody>> HOLD_TYPE = new ParameterizedTypeReference<>() {
	};
	private static final ParameterizedTypeReference<RsData<SellBody>> SELL_TYPE = new ParameterizedTypeReference<>() {
	};

	private final RestClient restClient;

	@Override
	public SeatHoldResult hold(long raceCourseId, long participationId, LocalDateTime expiresAt) {
		RsData<HoldBody> response;
		try {
			response = restClient.post()
				.uri(HOLD_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new HoldRequest(raceCourseId, participationId, expiresAt))
				.retrieve()
				.body(HOLD_TYPE);
		} catch (HttpClientErrorException exception) {
			if (exception.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
				throw new RaceCourseNotFoundException("존재하지 않는 종목입니다.");
			}
			// 409(접수 기간 아님, 이미 끝난 신청)는 다시 해도 안 되므로 B의 이유를 그대로 알린다
			if (exception.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
				throw new ParticipationNotPayableException(messageOf(exception, HOLD_REJECTED_MESSAGE));
			}
			throw contractError("자리 확보", participationId, exception);
		} catch (RestClientException exception) {
			throw unavailable("자리 확보", participationId, exception);
		}
		HoldBody body = response == null ? null : response.data();
		if (body == null || body.held() == null || (body.held() && body.expiresAt() == null)) {
			log.error("상품 서비스의 자리 확보 응답 형식이 계약과 다릅니다. participationId={}, response={}",
				participationId, response);
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		return body.held() ? SeatHoldResult.held(body.expiresAt()) : SeatHoldResult.soldOut();
	}

	@Override
	public SeatSellResult sell(long participationId) {
		RsData<SellBody> response;
		try {
			response = restClient.post()
				.uri(SELL_PATH, participationId)
				.retrieve()
				.body(SELL_TYPE);
		} catch (HttpClientErrorException exception) {
			throw contractError("자리 판매", participationId, exception);
		} catch (RestClientException exception) {
			throw unavailable("자리 판매", participationId, exception);
		}
		SellBody body = response == null ? null : response.data();
		if (body == null || body.result() == null) {
			log.error("상품 서비스의 자리 판매 응답 형식이 계약과 다릅니다. participationId={}, response={}",
				participationId, response);
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		return body.result();
	}

	private ExternalServiceUnavailableException unavailable(String action, long participationId,
		RestClientException exception) {
		log.warn("상품 서비스에 {}를 요청하지 못했습니다. participationId={}, cause={}",
			action, participationId, exception.toString());
		return new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
	}

	// 4xx는 다시 시도해도 풀리지 않는다. 경로·요청 계약이 어긋났을 수 있어 장애와 구분해 남긴다
	private ExternalServiceUnavailableException contractError(String action, long participationId,
		HttpClientErrorException exception) {
		log.error("상품 서비스가 {} 요청을 거절했습니다. 계약(경로·요청)을 확인해야 합니다. status={}, participationId={}, body={}",
			action, exception.getStatusCode(), participationId,
			InternalHttpClients.abbreviate(exception.getResponseBodyAsString()));
		return new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
	}

	// B 오류 응답(RsData)의 message. 읽지 못하면 기본 문구를 쓴다
	private static String messageOf(HttpClientErrorException exception, String fallback) {
		try {
			ErrorBody body = exception.getResponseBodyAs(ErrorBody.class);
			return body == null || body.message() == null || body.message().isBlank() ? fallback : body.message();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	record HoldRequest(long raceCourseId, long participationId, LocalDateTime expiresAt) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record HoldBody(Boolean held, LocalDateTime expiresAt) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SellBody(SeatSellResult result) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ErrorBody(String message) {
	}
}
