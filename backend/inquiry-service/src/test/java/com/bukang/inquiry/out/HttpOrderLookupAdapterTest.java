package com.bukang.inquiry.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bukang.inquiry.domain.exception.InquiryExternalServiceUnavailableException;
import com.bukang.inquiry.out.order.HttpOrderLookupAdapter;
import com.bukang.inquiry.out.order.OrderItemSummary;
import com.bukang.inquiry.out.order.OrderSummary;

/**
 * 주문 서비스 내부 API(GET /internal/v1/order/orders/{orderId}/summary → RsData의 data)와 계약이 맞는지 확인한다.
 * 응답 예시는 주문 서비스 OrderInternalApiExamples와 같은 모양이다. 없는 주문만 빈 값이고, 알 수 없을 때는 503이다
 */
class HttpOrderLookupAdapterTest {
	private static final String URL = "http://order/internal/v1/order/orders/12/summary";

	private MockRestServiceServer server;
	private HttpOrderLookupAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://order");
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new HttpOrderLookupAdapter(builder.build());
	}

	@Test
	@DisplayName("주문 요약을 OrderSummary로 바꾸고, 키트가 없는 항목은 키트 요약·배송 상태가 null이다")
	void found() {
		server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(body("""
			{"memberId": 3, "orderNo": "OD-20261110-3F9A", "paymentAmount": 85000, "status": "PAID", "items": [
				{"raceId": 1, "raceName": "서울 마라톤", "courseName": "10K", "kitSummary": "T-shirt L",
					"deliveryStatus": "PREPARING"},
				{"raceId": 2, "raceName": "부산 마라톤", "courseName": "하프", "kitSummary": null, "deliveryStatus": null}
			]}
			"""), MediaType.APPLICATION_JSON));

		OrderSummary order = adapter.findOrder(12L).orElseThrow();

		assertThat(order.memberId()).isEqualTo(3L);
		assertThat(order.orderNo()).isEqualTo("OD-20261110-3F9A");
		assertThat(order.paymentAmount()).isEqualTo(85_000L);
		assertThat(order.status()).isEqualTo("PAID");
		assertThat(order.items()).containsExactly(
			new OrderItemSummary(1L, "서울 마라톤", "10K", "T-shirt L", "PREPARING"),
			new OrderItemSummary(2L, "부산 마라톤", "하프", null, null));
		assertThat(order.hasRace(2L)).isTrue();
		server.verify();
	}

	@Test
	@DisplayName("없는 주문(404 ORDER_NOT_FOUND)은 빈 값이다")
	void notFound() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
			.body("{\"status\":404,\"code\":\"ORDER_NOT_FOUND\",\"message\":\"존재하지 않는 주문입니다.\",\"data\":null}")
			.contentType(MediaType.APPLICATION_JSON));

		assertThat(adapter.findOrder(12L)).isEmpty();
	}

	@Test
	@DisplayName("5xx·그 밖의 4xx·data나 items가 없는 응답·주문 없음이 아닌 404는 빈 값이 아니라 503으로 던진다")
	void unknownIsUnavailable() {
		server.expect(requestTo(URL)).andRespond(withServerError());
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
		server.expect(requestTo(URL)).andRespond(withSuccess(body("null"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess(body("{\"memberId\": 3}"), MediaType.APPLICATION_JSON));
		// 경로가 바뀌어 기본 404가 오면 계약이 어긋난 것이라, 모든 주문 문의가 조용히 404가 되지 않게 503으로 본다
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> adapter.findOrder(12L))
				.isInstanceOf(InquiryExternalServiceUnavailableException.class);
		}
		server.verify();
	}

	private static String body(String data) {
		return "{\"status\":200,\"message\":\"주문 요약을 조회했습니다.\",\"data\":" + data + "}";
	}
}
