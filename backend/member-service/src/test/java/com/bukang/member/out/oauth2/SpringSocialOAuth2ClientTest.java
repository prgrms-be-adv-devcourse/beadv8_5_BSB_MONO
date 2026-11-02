package com.bukang.member.out.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.util.UriComponentsBuilder;

import com.bukang.member.config.SocialLoginConfig;
import com.bukang.member.config.auth.SocialLoginProperties;
import com.bukang.member.domain.SocialProvider;
import com.bukang.member.domain.exception.SocialLoginFailedException;
import com.bukang.member.out.oauth2.SocialOAuth2Client.AuthorizationStart;

// 제공자에 실제로 요청하지 않는 시작 단계만 확인한다. 코드 교환은 키가 있어야 해서 직접 로그인해 확인한다
class SpringSocialOAuth2ClientTest {
	private static final SocialLoginProperties PROPERTIES = new SocialLoginProperties("https://crewrun.example/",
		"", new SocialLoginProperties.Kakao("kakao-client", "kakao-secret", ""),
		new SocialLoginProperties.Google("", ""));

	private final SocialOAuth2Client client = new SocialLoginConfig().socialOAuth2Client(PROPERTIES);

	@Test
	@DisplayName("키가 있는 제공자만 등록되고, 없는 제공자로 시작하면 실패한다")
	void onlyConfiguredProviders() {
		assertThat(client.isConfigured(SocialProvider.KAKAO)).isTrue();
		assertThat(client.isConfigured(SocialProvider.GOOGLE)).isFalse();
		assertThatThrownBy(() -> client.begin(SocialProvider.GOOGLE, new MockHttpServletRequest()))
			.isInstanceOf(SocialLoginFailedException.class);
	}

	@Test
	@DisplayName("카카오 인가 주소에 state, PKCE(S256), 고정한 콜백 주소가 들어가고, 콜백에서 쓸 PKCE 검증값을 저장할 값에 담는다")
	void beginBuildsAuthorizationRequest() {
		AuthorizationStart start = client.begin(SocialProvider.KAKAO, new MockHttpServletRequest());

		Map<String, String> query = UriComponentsBuilder.fromUriString(start.authorizationUri()).build()
			.getQueryParams().toSingleValueMap();
		assertThat(start.authorizationUri()).startsWith("https://kauth.kakao.com/oauth/authorize?");
		assertThat(query).containsEntry("client_id", "kakao-client")
			.containsEntry("response_type", "code")
			.containsEntry("code_challenge_method", "S256")
			.containsKey("code_challenge")
			.containsKey("state");
		assertThat(start.authorization().redirectUri())
			.isEqualTo("https://crewrun.example/api/v1/auth/oauth2/callback/kakao");
		assertThat(start.authorization().state()).isNotBlank();
		assertThat(start.authorization().attributes()).containsKey("code_verifier");
		assertThat(client.begin(SocialProvider.KAKAO, new MockHttpServletRequest()).authorization().state())
			.isNotEqualTo(start.authorization().state());
	}
}
