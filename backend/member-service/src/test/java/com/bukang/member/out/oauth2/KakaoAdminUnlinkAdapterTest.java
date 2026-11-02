package com.bukang.member.out.oauth2;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bukang.member.domain.SocialProvider;

class KakaoAdminUnlinkAdapterTest {
	private static final String BASE_URL = "https://kapi.kakao.com";

	@Test
	@DisplayName("카카오 회원이면 어드민 키와 회원번호로 연결 끊기를 부른다")
	void unlinkKakao() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(BASE_URL + "/v1/user/unlink"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK admin-key"))
			.andExpect(content().formDataContains(Map.of("target_id_type", "user_id", "target_id", "123")))
			.andRespond(withSuccess());

		new KakaoAdminUnlinkAdapter(builder.build(), "admin-key").unlink(SocialProvider.KAKAO, "123");

		server.verify();
	}

	@Test
	@DisplayName("카카오가 실패해도 예외를 던지지 않고(탈퇴는 이미 커밋됨), 구글이거나 어드민 키가 없으면 부르지 않는다")
	void failuresAndSkips() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(BASE_URL + "/v1/user/unlink")).andRespond(withServerError());

		new KakaoAdminUnlinkAdapter(builder.build(), "admin-key").unlink(SocialProvider.KAKAO, "123");
		new KakaoAdminUnlinkAdapter(builder.build(), "admin-key").unlink(SocialProvider.GOOGLE, "sub");
		new KakaoAdminUnlinkAdapter(builder.build(), "").unlink(SocialProvider.KAKAO, "123");

		server.verify();
	}
}
