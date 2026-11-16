package com.bukang.order.out.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDateTime;
import java.util.OptionalInt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.web.client.RestClient;

import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.out.crew.CrewPort.CrewMembership;

class HttpCrewAdapterTest {
	private static final String BASE_URL = "http://member";
	private static final String MEMBERSHIP_URL = BASE_URL + "/internal/v1/member/crews/7/members/3";
	private static final String COUNT_URL = BASE_URL + "/internal/v1/member/crews/7/member-count";
	private static final String PENALTY_URL = BASE_URL + "/internal/v1/member/crews/7/penalty";
	private static final String CREW_NOT_FOUND =
		"{ \"status\": 404, \"code\": \"CREW_NOT_FOUND\", \"message\": \"존재하지 않는 크루입니다.\", \"data\": null }";

	private MockRestServiceServer server;
	private HttpCrewAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new HttpCrewAdapter(builder.build());
	}

	@Test
	@DisplayName("크루원이면 가입 시각과 함께, 크루원이 아니면 crewMember=false로 돌려준다")
	void membership() {
		server.expect(requestTo(MEMBERSHIP_URL)).andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(data("{ \"crewId\": 7, \"memberId\": 3, \"crewMember\": true, \"role\": \"MEMBER\","
				+ " \"joinedAt\": \"2026-10-01T09:00:00\" }"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(MEMBERSHIP_URL))
			.andRespond(withSuccess(data("{ \"crewId\": 7, \"memberId\": 3, \"crewMember\": false,"
				+ " \"role\": null, \"joinedAt\": null }"), MediaType.APPLICATION_JSON));

		assertThat(adapter.findMembership(7L, 3L))
			.contains(new CrewMembership(true, LocalDateTime.of(2026, 10, 1, 9, 0)));
		assertThat(adapter.findMembership(7L, 3L)).contains(new CrewMembership(false, null));
		server.verify();
	}

	@Test
	@DisplayName("크루원 수를 돌려준다")
	void count() {
		server.expect(requestTo(COUNT_URL))
			.andRespond(withSuccess(data("{ \"crewId\": 7, \"memberCount\": 5 }"), MediaType.APPLICATION_JSON));

		assertThat(adapter.countMembers(7L)).isEqualTo(OptionalInt.of(5));
	}

	@Test
	@DisplayName("크루가 없거나 없앤 크루(404 CREW_NOT_FOUND)면 빈 값")
	void crewNotFound() {
		server.expect(requestTo(MEMBERSHIP_URL)).andRespond(notFound(CREW_NOT_FOUND));
		server.expect(requestTo(COUNT_URL)).andRespond(notFound(CREW_NOT_FOUND));

		assertThat(adapter.findMembership(7L, 3L)).isEmpty();
		assertThat(adapter.countMembers(7L)).isEmpty();
	}

	@Test
	@DisplayName("크루 없음이 아닌 404, 5xx, 빈 응답, 가입 시각 없는 크루원은 크루원 아님으로 보지 않고 503 예외로 알린다")
	void unavailable() {
		server.expect(requestTo(MEMBERSHIP_URL)).andRespond(notFound("{ \"status\": 404, \"error\": \"Not Found\" }"));
		server.expect(requestTo(MEMBERSHIP_URL)).andRespond(withServerError());
		server.expect(requestTo(MEMBERSHIP_URL))
			.andRespond(withSuccess(data("{}"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(MEMBERSHIP_URL))
			.andRespond(withSuccess(data("{ \"crewMember\": true, \"joinedAt\": null }"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(COUNT_URL))
			.andRespond(withSuccess("{ \"status\": 200, \"message\": \"조회\", \"data\": null }",
				MediaType.APPLICATION_JSON));

		for (int i = 0; i < 4; i++) {
			assertThatThrownBy(() -> adapter.findMembership(7L, 3L))
				.isInstanceOf(ExternalServiceUnavailableException.class);
		}
		assertThatThrownBy(() -> adapter.countMembers(7L)).isInstanceOf(ExternalServiceUnavailableException.class);
	}

	@Test
	@DisplayName("파티 만들기 제한 중이면 끝나는 시각을, 아니면 빈 값을 돌려준다")
	void partyRestriction() {
		server.expect(requestTo(PENALTY_URL)).andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(data("{ \"restricted\": true, \"endAt\": \"2026-12-12T04:30:00\" }"),
				MediaType.APPLICATION_JSON));
		server.expect(requestTo(PENALTY_URL))
			.andRespond(withSuccess(data("{ \"restricted\": false, \"endAt\": null }"), MediaType.APPLICATION_JSON));

		assertThat(adapter.findPartyRestrictionEnd(7L)).contains(LocalDateTime.of(2026, 12, 12, 4, 30));
		assertThat(adapter.findPartyRestrictionEnd(7L)).isEmpty();
		server.verify();
	}

	@Test
	@DisplayName("제한을 묻지 못하거나(아직 배포 안 된 404, 5xx) 응답에 값이 없으면 503 대신 제한 없음으로 본다")
	void partyRestrictionUnavailable() {
		server.expect(requestTo(PENALTY_URL)).andRespond(notFound("{ \"status\": 404, \"error\": \"Not Found\" }"));
		server.expect(requestTo(PENALTY_URL)).andRespond(withServerError());
		server.expect(requestTo(PENALTY_URL)).andRespond(withSuccess(data("{}"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(PENALTY_URL))
			.andRespond(withSuccess(data("{ \"restricted\": true, \"endAt\": null }"), MediaType.APPLICATION_JSON));

		for (int i = 0; i < 4; i++) {
			assertThat(adapter.findPartyRestrictionEnd(7L)).isEmpty();
		}
		server.verify();
	}

	private static String data(String body) {
		return "{ \"status\": 200, \"message\": \"조회\", \"data\": " + body + " }";
	}

	private static DefaultResponseCreator notFound(String body) {
		return withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body(body);
	}
}
