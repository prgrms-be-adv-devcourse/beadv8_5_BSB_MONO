package com.bukang.order.out.kit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.out.kit.KitOptionPort.KitOption;

class HttpKitOptionAdapterTest {
	private static final String BASE_URL = "http://product";
	private static final String URL = BASE_URL + "/api/v1/product/races/2/kit-options";

	private MockRestServiceServer server;
	private HttpKitOptionAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new HttpKitOptionAdapter(builder.build());
	}

	@Test
	@DisplayName("이름별로 묶인 옵션을 옵션 하나씩 펼친다 (준비 수량 null은 그대로)")
	void findKitOptions() {
		server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("""
				{ "status": 200, "message": "키트 옵션 목록을 조회했습니다.", "data": { "raceId": 2, "kitOptions": [
					{ "name": "T-shirt", "values": [
						{ "kitOptionId": 11, "value": "M", "stock": 30 },
						{ "kitOptionId": 12, "value": "L", "stock": null } ] } ] } }
				""", MediaType.APPLICATION_JSON));

		assertThat(adapter.findKitOptions(2)).contains(List.of(
			new KitOption(11, "T-shirt", "M", 30), new KitOption(12, "T-shirt", "L", null)));
	}

	@Test
	@DisplayName("옵션이 없는 대회는 빈 목록, 상품이 대회를 찾지 못하면(404) 빈 값이다")
	void emptyOrNotFound() {
		server.expect(requestTo(URL)).andRespond(withSuccess("""
			{ "status": 200, "data": { "raceId": 2, "kitOptions": [] } }
			""", MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThat(adapter.findKitOptions(2)).contains(List.of());
		assertThat(adapter.findKitOptions(2)).isEmpty();
	}

	@Test
	@DisplayName("404 말고 4xx·5xx·형식이 다른 응답은 503으로 다시 시도하게 한다")
	void unavailable() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
		server.expect(requestTo(URL)).andRespond(withSuccess("{ \"status\": 200, \"data\": { \"raceId\": 2 } }",
			MediaType.APPLICATION_JSON));

		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> adapter.findKitOptions(2)).isInstanceOf(ExternalServiceUnavailableException.class);
		}
	}
}
