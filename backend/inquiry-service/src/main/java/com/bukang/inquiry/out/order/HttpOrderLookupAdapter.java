package com.bukang.inquiry.out.order;

import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.inquiry.domain.exception.InquiryExternalServiceUnavailableException;
import com.bukang.inquiry.out.InternalHttpClients;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주문 서비스의 문의용 주문 요약 내부 API를 부른다 (GET /internal/v1/order/orders/{orderId}/summary → RsData.data)
 * data의 필드 이름이 OrderSummary·OrderItemSummary와 같아 그대로 받는다 (주문 서비스 OrderLookupDto)
 * - 200: 주문 요약 (남의 주문인지·대회가 맞는지는 InquiryFacade가 본다)
 * - 404 ORDER_NOT_FOUND: 없는 주문 → 빈 값 (문의를 만들 때는 InquiryOrderNotFoundException으로 404가 된다)
 * - 그 밖(시간 초과 · 연결 실패 · 5xx · 코드가 다른 404 · 그 밖의 4xx · data나 items가 없는 응답): 503
 * 등록은 OrderClientConfig가 한다 (inquiry.clients.order.mode=http)
 */
@Slf4j
@RequiredArgsConstructor
public class HttpOrderLookupAdapter implements OrderLookupPort {
	static final String PATH = "/internal/v1/order/orders/{orderId}/summary";
	static final String ORDER_NOT_FOUND_CODE = "ORDER_NOT_FOUND";
	static final String UNAVAILABLE_MESSAGE = "주문 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.";
	private static final ParameterizedTypeReference<RsData<OrderSummary>> RESPONSE_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient restClient;

	@Override
	public Optional<OrderSummary> findOrder(long orderId) {
		RsData<OrderSummary> response;
		try {
			response = restClient.get().uri(PATH, orderId).retrieve().body(RESPONSE_TYPE);
		} catch (HttpClientErrorException.NotFound exception) {
			if (InternalHttpClients.hasErrorCode(exception, ORDER_NOT_FOUND_CODE)) {
				return Optional.empty();
			}
			// 경로가 바뀐 기본 404를 「없는 주문」으로 보면 모든 주문 문의가 조용히 404가 되므로 503으로 본다
			log.error("주문 서비스가 주문 요약 요청에 주문 없음이 아닌 404로 답했습니다. 계약(경로)을 확인해야 합니다. orderId={}, body={}",
				orderId, InternalHttpClients.abbreviate(exception.getResponseBodyAsString()), exception);
			throw new InquiryExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		} catch (HttpClientErrorException exception) {
			log.error("주문 서비스가 주문 요약 요청을 거절했습니다. 계약(경로)을 확인해야 합니다. status={}, orderId={}",
				exception.getStatusCode(), orderId, exception);
			throw new InquiryExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		} catch (RestClientException exception) {
			log.error("주문 서비스에 주문 요약을 묻지 못했습니다. orderId={}", orderId, exception);
			throw new InquiryExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		if (response == null || response.data() == null || response.data().items() == null) {
			log.error("주문 서비스의 주문 요약 응답에 값이 없습니다. orderId={}, response={}", orderId, response);
			throw new InquiryExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		return Optional.of(response.data());
	}
}
