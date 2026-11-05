package com.bukang.member.out.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bukang.member.domain.exception.KakaoUnavailableException;

// 카카오 친구 목록·메시지·토큰 갱신 호출과 오류 코드 해석을 확인한다. 실제 카카오는 부르지 않는다
class RestKakaoTalkClientTest {
	private static final String KAPI = "https://kapi.kakao.com";
	private static final String KAUTH = "https://kauth.kakao.com";

	private MockRestServiceServer kapiServer;
	private MockRestServiceServer kauthServer;
	private RestKakaoTalkClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder kapi = RestClient.builder().baseUrl(KAPI);
		RestClient.Builder kauth = RestClient.builder().baseUrl(KAUTH);
		kapiServer = MockRestServiceServer.bindTo(kapi).build();
		kauthServer = MockRestServiceServer.bindTo(kauth).build();
		client = new RestKakaoTalkClient(kapi.build(), kauth.build(), "kakao-client", "kakao-secret");
	}

	@Test
	@DisplayName("친구 목록은 사용자 토큰으로 100명까지 받고, 회원번호·uuid·닉네임·썸네일을 꺼낸다")
	void friends() {
		kapiServer.expect(requestTo(KAPI + "/v1/api/talk/friends?limit=100"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-1"))
			.andRespond(withSuccess("""
				{"elements":[{"id":1234567,"uuid":"abcdefg0001","favorite":true,"profile_nickname":"이수민",
				"profile_thumbnail_image":"https://k.kakaocdn.net/a.jpg"},{"id":1234568,"uuid":"abcdefg0002"}],
				"total_count":2,"after_url":null}
				""", MediaType.APPLICATION_JSON));

		List<KakaoFriend> friends = client.friends("access-1");

		assertThat(friends).containsExactly(
			new KakaoFriend("1234567", "abcdefg0001", "이수민", "https://k.kakaocdn.net/a.jpg"),
			new KakaoFriend("1234568", "abcdefg0002", null, null));
		kapiServer.verify();
	}

	@Test
	@DisplayName("메시지는 받는 사람 한 명의 uuid 배열과 기본 템플릿 JSON을 폼으로 보낸다")
	void send() {
		kapiServer.expect(requestTo(KAPI + "/v1/api/talk/friends/message/default/send"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-1"))
			.andExpect(content().formDataContains(Map.of(
				"receiver_uuids", "[\"abcdefg0001\"]",
				"template_object", "{\"object_type\":\"feed\"}")))
			.andRespond(withSuccess("{\"successful_receiver_uuids\":[\"abcdefg0001\"]}", MediaType.APPLICATION_JSON));

		client.send("access-1", "abcdefg0001", "{\"object_type\":\"feed\"}");

		kapiServer.verify();
	}

	@Test
	@DisplayName("카카오가 오류 코드로 답하면 그 코드를 담아 던진다 (하루 한도 -532, 토큰 -401, 동의 -402)")
	void kakaoErrorCodes() {
		kapiServer.expect(requestTo(KAPI + "/v1/api/talk/friends/message/default/send"))
			.andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
				.body("{\"code\":-532,\"msg\":\"daily message limit per sender has been exceeded.\"}"));
		kapiServer.expect(requestTo(KAPI + "/v1/api/talk/friends?limit=100"))
			.andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
				.body("{\"code\":-401,\"msg\":\"InvalidTokenException\"}"));
		kapiServer.expect(requestTo(KAPI + "/v1/api/talk/friends?limit=100"))
			.andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
				.body("{\"code\":-402,\"msg\":\"insufficient scopes.\"}"));

		assertThatThrownBy(() -> client.send("access-1", "abcdefg0001", "{}"))
			.isInstanceOfSatisfying(KakaoApiException.class,
				exception -> assertThat(exception.getCode()).isEqualTo(-532));
		assertThatThrownBy(() -> client.friends("access-1"))
			.isInstanceOfSatisfying(KakaoApiException.class,
				exception -> assertThat(exception.getCode()).isEqualTo(-401));
		assertThatThrownBy(() -> client.friends("access-1"))
			.isInstanceOfSatisfying(KakaoApiException.class,
				exception -> assertThat(exception.getCode()).isEqualTo(-402));
	}

	@Test
	@DisplayName("카카오 서버 오류나 응답 본문을 읽을 수 없으면 카카오 장애(502)로 던진다")
	void kakaoUnavailable() {
		kapiServer.expect(requestTo(KAPI + "/v1/api/talk/friends?limit=100")).andRespond(withServerError());
		kapiServer.expect(requestTo(KAPI + "/v1/api/talk/friends/message/default/send"))
			.andRespond(withBadRequest().body("not json"));

		assertThatThrownBy(() -> client.friends("access-1")).isInstanceOf(KakaoUnavailableException.class);
		assertThatThrownBy(() -> client.send("access-1", "u", "{}")).isInstanceOf(KakaoUnavailableException.class);
	}

	@Test
	@DisplayName("토큰 갱신은 앱 키·시크릿과 리프레시 토큰을 보내고, 새 액세스 토큰·만료·새 리프레시 토큰(있을 때만)을 받는다")
	void refresh() {
		kauthServer.expect(requestTo(KAUTH + "/oauth/token"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().formDataContains(Map.of(
				"grant_type", "refresh_token",
				"client_id", "kakao-client",
				"client_secret", "kakao-secret",
				"refresh_token", "refresh-1")))
			.andRespond(withSuccess("""
				{"token_type":"bearer","access_token":"access-2","expires_in":21599,
				"refresh_token":"refresh-2","refresh_token_expires_in":5183999}
				""", MediaType.APPLICATION_JSON));
		kauthServer.expect(requestTo(KAUTH + "/oauth/token"))
			.andRespond(withSuccess("{\"token_type\":\"bearer\",\"access_token\":\"access-3\",\"expires_in\":21599}",
				MediaType.APPLICATION_JSON));

		assertThat(client.refresh("refresh-1")).isEqualTo(new KakaoRefreshedToken("access-2", 21599, "refresh-2"));
		assertThat(client.refresh("refresh-2")).isEqualTo(new KakaoRefreshedToken("access-3", 21599, null));
		kauthServer.verify();
	}

	@Test
	@DisplayName("리프레시 토큰이 지났거나 폐기돼 갱신이 거절되면 토큰 오류(-401)로 던진다")
	void refreshRejected() {
		kauthServer.expect(requestTo(KAUTH + "/oauth/token"))
			.andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":\"invalid_grant\",\"error_code\":\"KOE322\"}"));

		assertThatThrownBy(() -> client.refresh("refresh-1"))
			.isInstanceOfSatisfying(KakaoApiException.class,
				exception -> assertThat(exception.getCode()).isEqualTo(-401));
	}

	@Test
	@DisplayName("토큰을 담은 값은 문자열로 바꿔도 토큰이 나오지 않는다 (로그에 찍혀도 새지 않게)")
	void tokensHiddenInToString() {
		assertThat(new KakaoRefreshedToken("access-secret", 10, "refresh-secret").toString())
			.doesNotContain("access-secret", "refresh-secret");
	}

	@Test
	@DisplayName("200으로 답해도 받는 사람이 성공 목록에 없으면 실패 정보의 오류 코드로 던진다")
	void sendFailureInsideOkResponse() {
		kapiServer.expect(requestTo(KAPI + "/v1/api/talk/friends/message/default/send"))
			.andRespond(withSuccess("""
				{"successful_receiver_uuids":[],"failure_info":[{"code":-532,"msg":"daily limit",
				"receiver_uuids":["abcdefg0001"]}]}
				""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.send("access-1", "abcdefg0001", "{}"))
			.isInstanceOfSatisfying(KakaoApiException.class,
				exception -> assertThat(exception.getCode()).isEqualTo(-532));
	}

	@Test
	@DisplayName("갱신이 리프레시 토큰 문제가 아닌 이유(앱 키 오류 등)로 거절되면 토큰 오류가 아니라 카카오 장애로 던진다")
	void refreshRejectedForOtherReason() {
		kauthServer.expect(requestTo(KAUTH + "/oauth/token"))
			.andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":\"invalid_client\",\"error_code\":\"KOE010\"}"));

		assertThatThrownBy(() -> client.refresh("refresh-1")).isInstanceOf(KakaoUnavailableException.class);
	}
}
