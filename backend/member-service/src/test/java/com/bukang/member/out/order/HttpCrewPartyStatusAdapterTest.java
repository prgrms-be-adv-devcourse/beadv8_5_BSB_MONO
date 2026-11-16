package com.bukang.member.out.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bukang.member.crew.domain.exception.CrewPartyCheckFailedException;

// 주문 서비스 응답 계약: OrderService CrewMemberPartyStatusDto {canLeave, canKick, blockingParties}
class HttpCrewPartyStatusAdapterTest {
	private static final String BASE_URL = "http://order";
	private static final String URL = BASE_URL + "/internal/v1/order/crews/2/members/3/party-status";

	private MockRestServiceServer server;
	private HttpCrewPartyStatusAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new HttpCrewPartyStatusAdapter(builder.build());
	}

	@Test
	@DisplayName("크루 ID와 회원 ID로 묻고, canLeave·canKick 값을 돌려준다. 모르는 필드(blockingParties)는 무시한다")
	void status() {
		server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("""
				{ "status": 200, "message": "조회", "data": { "canLeave": false, "canKick": true, "blockingParties": [
					{ "partyId": 7, "status": "RECRUITING" } ] } }
				""", MediaType.APPLICATION_JSON));

		CrewPartyStatusPort.PartyStatus result = adapter.findStatus(2, 3);

		assertThat(result.canLeave()).isFalse();
		assertThat(result.canKick()).isTrue();
		server.verify();
	}

	@Test
	@DisplayName("연결 실패·시간 초과·4xx·5xx는 확인하지 못한 것이라 CrewPartyCheckFailedException으로 알린다")
	void unavailable() {
		server.expect(requestTo(URL)).andRespond(withServerError());
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
		server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("read timed out")));

		for (int attempt = 0; attempt < 4; attempt++) {
			assertThatThrownBy(() -> adapter.findStatus(2, 3))
				.isInstanceOf(CrewPartyCheckFailedException.class)
				.hasMessage(HttpCrewPartyStatusAdapter.UNAVAILABLE_MESSAGE);
		}
		server.verify();
	}

	@Test
	@DisplayName("빈 응답·data 없음·필드 하나라도 빠진 응답은 모르는 것이라 막는다 (빠진 값을 false나 true로 짐작하지 않는다)")
	void incompleteResponse() {
		server.expect(requestTo(URL)).andRespond(withSuccess("", MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess("""
			{ "status": 200, "message": "조회", "data": null }
			""", MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess("""
			{ "status": 200, "message": "조회", "data": { "canLeave": true } }
			""", MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess("""
			{ "status": 200, "message": "조회", "data": { "canKick": true } }
			""", MediaType.APPLICATION_JSON));

		for (int attempt = 0; attempt < 4; attempt++) {
			assertThatThrownBy(() -> adapter.findStatus(2, 3))
				.isInstanceOf(CrewPartyCheckFailedException.class);
		}
		server.verify();
	}
}
