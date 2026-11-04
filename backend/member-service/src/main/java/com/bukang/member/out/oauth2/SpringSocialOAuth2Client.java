package com.bukang.member.out.oauth2;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationProvider;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.oidc.authentication.OidcAuthorizationCodeAuthenticationProvider;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.client.RestClientException;

import com.bukang.member.domain.SocialProvider;
import com.bukang.member.domain.exception.SocialLoginFailedException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Security OAuth2 Client 부품으로 카카오·구글 로그인을 처리한다 (BE-031)
 * 세션을 쓰지 않으므로 로그인 필터(oauth2Login)는 켜지 않고, 컨트롤러가 이 클래스를 직접 부른다
 * - 시작: DefaultOAuth2AuthorizationRequestResolver가 state, PKCE(code_challenge), 구글 nonce를 만든다
 * - 콜백: 라이브러리의 인증 처리기가 state·redirect_uri가 시작 때와 같은지 확인하고, PKCE 검증값을 보내 코드를 토큰으로 바꾼다
 *   구글은 ID 토큰의 서명·발급자·대상·만료·nonce도 확인한다. 카카오는 사용자 정보 API로 회원번호·이메일을 받는다
 * 등록은 SocialLoginConfig가 한다. 키가 설정된 제공자만 등록돼 있다
 */
@Slf4j
public class SpringSocialOAuth2Client implements SocialOAuth2Client {
	static final String FAILED_MESSAGE = "소셜 로그인에 실패했습니다.";

	private final Map<String, ClientRegistration> registrations;
	private final DefaultOAuth2AuthorizationRequestResolver resolver;
	private final OAuth2LoginAuthenticationProvider kakaoAuthentication;
	private final OidcAuthorizationCodeAuthenticationProvider googleAuthentication;

	// 키가 하나도 없으면 목록이 비어 있다. 라이브러리의 InMemoryClientRegistrationRepository는 빈 목록을 받지 않아 Map으로 둔다
	public SpringSocialOAuth2Client(List<ClientRegistration> registrations) {
		this.registrations = registrations.stream()
			.collect(Collectors.toUnmodifiableMap(ClientRegistration::getRegistrationId, Function.identity()));
		ClientRegistrationRepository repository = this.registrations::get;
		// 기본 주소(/oauth2/authorization)는 쓰지 않는다. resolve(request, registrationId)로 제공자를 직접 고른다
		this.resolver = new DefaultOAuth2AuthorizationRequestResolver(repository, "/oauth2/authorization");
		// 시크릿이 있는 서버라도 PKCE를 같이 쓴다. 인가 코드가 새어도 검증값 없이는 토큰으로 바꿀 수 없다
		this.resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
		RestClientAuthorizationCodeTokenResponseClient tokenClient =
			new RestClientAuthorizationCodeTokenResponseClient();
		this.kakaoAuthentication = new OAuth2LoginAuthenticationProvider(tokenClient, new DefaultOAuth2UserService());
		this.googleAuthentication = new OidcAuthorizationCodeAuthenticationProvider(tokenClient, new OidcUserService());
	}

	@Override
	public boolean isConfigured(SocialProvider provider) {
		return registrations.containsKey(provider.getPathValue());
	}

	@Override
	public AuthorizationStart begin(SocialProvider provider, HttpServletRequest servletRequest) {
		ClientRegistration registration = registration(provider);
		// 콜백 주소를 절대 주소로 고정했으므로(BE-031 결정 4) 리졸버는 요청의 호스트로 주소를 만들지 않는다
		OAuth2AuthorizationRequest request = resolver.resolve(servletRequest, registration.getRegistrationId());
		Map<String, String> attributes = new HashMap<>();
		request.getAttributes().forEach((name, value) -> {
			if (value instanceof String text) {
				attributes.put(name, text);
			}
		});
		StoredAuthorization authorization = new StoredAuthorization(provider, request.getState(),
			request.getRedirectUri(), new LinkedHashSet<>(request.getScopes()), attributes, null, null);
		return new AuthorizationStart(request.getAuthorizationRequestUri(), authorization);
	}

	@Override
	public SocialIdentity complete(StoredAuthorization authorization, String code, String state) {
		ClientRegistration registration = registration(authorization.provider());
		OAuth2AuthorizationExchange exchange = new OAuth2AuthorizationExchange(
			restore(registration, authorization),
			OAuth2AuthorizationResponse.success(code).redirectUri(authorization.redirectUri()).state(state).build());
		OAuth2LoginAuthenticationToken token = new OAuth2LoginAuthenticationToken(registration, exchange);
		try {
			return switch (authorization.provider()) {
				case KAKAO -> kakaoIdentity(principal(kakaoAuthentication.authenticate(token)));
				case GOOGLE -> googleIdentity((OidcUser)principal(googleAuthentication.authenticate(token)));
			};
		} catch (OAuth2AuthenticationException | RestClientException exception) {
			// 코드·토큰 값은 남기지 않는다
			log.warn("소셜 로그인 코드 교환에 실패했습니다. provider={}, cause={}", authorization.provider(), exception.toString());
			throw new SocialLoginFailedException(FAILED_MESSAGE);
		}
	}

	private static OAuth2User principal(Authentication authentication) {
		return ((OAuth2LoginAuthenticationToken)authentication).getPrincipal();
	}

	// 콜백에서 시작 때의 인가 요청을 다시 만든다. 라이브러리가 state·redirect_uri 비교, PKCE 검증값, nonce를 여기서 꺼낸다
	private OAuth2AuthorizationRequest restore(ClientRegistration registration, StoredAuthorization authorization) {
		return OAuth2AuthorizationRequest.authorizationCode()
			.authorizationUri(registration.getProviderDetails().getAuthorizationUri())
			.clientId(registration.getClientId())
			.redirectUri(authorization.redirectUri())
			.scopes(authorization.scopes())
			.state(authorization.state())
			.attributes(attributes -> attributes.putAll(authorization.attributes()))
			.build();
	}

	// 카카오 사용자 정보: id(회원번호), kakao_account.email·is_email_valid·is_email_verified, kakao_account.name(심사 뒤)
	// 이메일은 유효하고 인증된 것만 쓴다
	private static SocialIdentity kakaoIdentity(OAuth2User user) {
		Object id = user.getAttribute("id");
		if (id == null) {
			throw new SocialLoginFailedException(FAILED_MESSAGE);
		}
		Map<?, ?> account = user.getAttribute("kakao_account") instanceof Map<?, ?> map ? map : Map.of();
		boolean emailVerified = Boolean.TRUE.equals(account.get("is_email_valid"))
			&& Boolean.TRUE.equals(account.get("is_email_verified"));
		String email = emailVerified && account.get("email") instanceof String text ? text : null;
		String name = account.get("name") instanceof String text ? text : null;
		return new SocialIdentity(SocialProvider.KAKAO, String.valueOf(id), email, name);
	}

	// 구글 ID 토큰: sub(회원번호), email, email_verified, name
	private static SocialIdentity googleIdentity(OidcUser user) {
		String email = Boolean.TRUE.equals(user.getEmailVerified()) ? user.getEmail() : null;
		return new SocialIdentity(SocialProvider.GOOGLE, user.getSubject(), email, user.getFullName());
	}

	private ClientRegistration registration(SocialProvider provider) {
		ClientRegistration registration = registrations.get(provider.getPathValue());
		if (registration == null) {
			throw new SocialLoginFailedException(FAILED_MESSAGE);
		}
		return registration;
	}
}
