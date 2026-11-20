package com.bukang.member.recordcert.out;

import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import com.bukang.common.global.rsdata.RsData;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;

/**
 * 주문 서비스 내부 API로 참가권 하나를 묻는다: GET /internal/v1/order/order-items/{orderItemId}/ticket (BUK-271)
 * 404는 없는 참가권, 그 밖의 실패는 503 (InternalLookups). 등록은 InternalClientConfig가 한다
 */
@RequiredArgsConstructor
public class HttpTicketAdapter implements TicketPort {
	static final String PATH = "/internal/v1/order/order-items/{orderItemId}/ticket";
	private static final ParameterizedTypeReference<RsData<Body>> RESPONSE_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient restClient;

	@Override
	public Optional<Ticket> find(long orderItemId) {
		return InternalLookups.get(restClient, PATH, orderItemId, RESPONSE_TYPE).map(Body::toTicket);
	}

	// order TicketDto. 칸이 비면 판단할 수 없어 503
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Body(Long buyerId, Long raceId, Long raceCourseId, String status) {
		Ticket toTicket() {
			if (buyerId == null || raceId == null || raceCourseId == null || status == null) {
				throw InternalLookups.unavailable();
			}
			return new Ticket(buyerId, raceId, raceCourseId, status);
		}
	}
}
