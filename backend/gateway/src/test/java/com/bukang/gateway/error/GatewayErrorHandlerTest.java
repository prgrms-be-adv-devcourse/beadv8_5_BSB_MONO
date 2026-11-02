package com.bukang.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.UnknownHostException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

// 서비스 이름을 찾지 못하는 경우는 실제 DNS로 재현하면 DNS가 느린 환경에서 응답 시간 제한(504)에 먼저 걸리므로 예외를 직접 넘긴다
class GatewayErrorHandlerTest {

	private final GatewayErrorHandler handler = new GatewayErrorHandler();

	@Test
	@DisplayName("서비스 이름을 찾지 못하면(멈춘 컨테이너) 503과 SERVICE_UNAVAILABLE 코드를 JSON으로 응답한다")
	void respondsServiceUnavailableWhenServiceHostIsUnknown() {
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/queue/1"));

		handler.handle(exchange, new RuntimeException(new UnknownHostException("bukang-queue-service"))).block();

		assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(503);
		assertThat(exchange.getResponse().getHeaders().getContentType()).hasToString("application/json");
		assertThat(exchange.getResponse().getBodyAsString().block()).isEqualTo("{\"status\":503,"
			+ "\"code\":\"SERVICE_UNAVAILABLE\",\"message\":\"서버에 연결할 수 없어요. 잠시 뒤 다시 시도해 주세요.\",\"data\":null}");
	}
}
