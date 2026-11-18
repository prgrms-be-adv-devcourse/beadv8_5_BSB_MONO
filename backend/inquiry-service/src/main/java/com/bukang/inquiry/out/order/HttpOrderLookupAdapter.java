package com.bukang.inquiry.out.order;

import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.inquiry.domain.exception.InquiryExternalServiceUnavailableException;
import com.bukang.inquiry.out.InternalHttpClients;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주문 서비스의 문의용 주문 요약 내부 API를 부른다 (GET /internal/v1/order/orders/{orderId}/summary → RsData.data)
 * data의 필드 이름이 OrderSummary·OrderItemSummary와 같아 그대로 받는다 (주문 서비스 OrderLookupDto)
 * - 200: 주문 요약 (남의 주문인지·대회가 맞는지는 InquiryCreateFacade가 본다)
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

	private static final InternalHttpClients.Endpoint ENDPOINT = new InternalHttpClients.Endpoint("주문 서비스",
		"주문 요약 요청", ORDER_NOT_FOUND_CODE, UNAVAILABLE_MESSAGE);

	private final RestClient restClient;

	@Override
	public Optional<OrderSummary> findOrder(long orderId) {
		return InternalHttpClients.getData(restClient, ENDPOINT, RESPONSE_TYPE, PATH, orderId)
			.map(order -> {
				if (order.items() == null) {
					log.error("주문 서비스의 주문 요약 응답에 값이 없습니다. orderId={}, order={}", orderId, order);
					throw new InquiryExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
				}
				return order;
			});
	}
}
