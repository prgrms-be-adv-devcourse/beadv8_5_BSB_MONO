package com.bukang.member.out.oauth2;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationProvider;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.oidc.authentication.OidcAuthorizationCodeAuthenticationProvider;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import com.bukang.member.domain.SocialProvider;
import com.bukang.member.domain.exception.SocialLoginFailedException;
import com.bukang.member.out.InternalHttpClients;

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
	// 카카오 메시지 추가 동의용 등록. 키·콜백 주소는 카카오 로그인과 같고 범위만 다르다 (SocialLoginConfig)
	public static final String KAKAO_MESSAGE_REGISTRATION_ID = "kakao-message";

	// 카카오·구글 응답을 기다리는 시간. 연결 끊기·카카오톡 보내기와 같은 2초다 (SocialLoginConfig.KAKAO_TIMEOUT)
	private static final Duration TIMEOUT = Duration.ofSeconds(2);

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
		// 라이브러리 기본 클라이언트와 같은 응답 변환기·오류 처리기에 시간 제한만 더한다. 빠지면 제공자의 오류 응답을 못 읽는다
		RestClientAuthorizationCodeTokenResponseClient tokenClient =
			new RestClientAuthorizationCodeTokenResponseClient();
		tokenClient.setRestClient(RestClient.builder()
			.requestFactory(InternalHttpClients.requestFactory(TIMEOUT))
			.configureMessageConverters(converters -> converters
				.addCustomConverter(new FormHttpMessageConverter())
				.addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter()))
			.defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
			.build());
		RestTemplate userInfoClient = new RestTemplate(InternalHttpClients.requestFactory(TIMEOUT));
		userInfoClient.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
		DefaultOAuth2UserService userService = new DefaultOAuth2UserService();
		userService.setRestOperations(userInfoClient);
		OidcUserService oidcUserService = new OidcUserService();
		oidcUserService.setOauth2UserService(userService);
		this.kakaoAuthentication = new OAuth2LoginAuthenticationProvider(tokenClient, userService);
		this.googleAuthentication = new OidcAuthorizationCodeAuthenticationProvider(tokenClient, oidcUserService);
		this.googleAuthentication.setJwtDecoderFactory(
			idTokenDecoderFactory(new RestTemplate(InternalHttpClients.requestFactory(TIMEOUT))));
	}

	// 기본 OidcIdTokenDecoderFactory는 공개키(JWK)를 받아 올 클라이언트를 바꿀 수 없다. 같은 검증(서명·만료·발급자·대상·nonce)에
	// 시간 제한이 있는 클라이언트만 끼운다. 기본 팩토리처럼 제공자별로 디코더를 한 번만 만들어 공개키 캐시를 유지한다
	private static JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(RestOperations jwkClient) {
		Map<String, JwtDecoder> decoders = new ConcurrentHashMap<>();
		return registration -> decoders.computeIfAbsent(registration.getRegistrationId(), id -> {
			String jwkSetUri = registration.getProviderDetails().getJwkSetUri();
			if (!StringUtils.hasText(jwkSetUri)) {
				throw new OAuth2AuthenticationException(new OAuth2Error("missing_signature_verifier"));
			}
			NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
				.jwsAlgorithm(SignatureAlgorithm.RS256)
				.restOperations(jwkClient)
				.build();
			decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(),
				new OidcIdTokenValidator(registration)));
			decoder.setClaimSetConverter(OidcIdTokenDecoderFactory.createDefaultClaimTypeConverter());
			return decoder;
		});
	}

	@Override
	public boolean isConfigured(SocialProvider provider) {
		return registrations.containsKey(provider.getPathValue());
	}

	@Override
	public AuthorizationStart begin(SocialProvider provider, HttpServletRequest servletRequest) {
		return begin(provider, registration(provider), servletRequest);
	}

	@Override
	public AuthorizationStart beginMessageConsent(HttpServletRequest servletRequest) {
		ClientRegistration registration = registrations.get(KAKAO_MESSAGE_REGISTRATION_ID);
		if (registration == null) {
			throw new SocialLoginFailedException(FAILED_MESSAGE);
		}
		return begin(SocialProvider.KAKAO, registration, servletRequest);
	}

	private AuthorizationStart begin(SocialProvider provider, ClientRegistration registration,
		HttpServletRequest servletRequest) {
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
				case KAKAO -> kakaoIdentity((OAuth2LoginAuthenticationToken)kakaoAuthentication.authenticate(token));
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
	// 이메일은 유효하고 인증된 것만 쓴다. 받은 토큰도 담는다(메시지 동의 때만 저장, kakao-crew-invite 결정 1)
	private static SocialIdentity kakaoIdentity(OAuth2LoginAuthenticationToken authenticated) {
		OAuth2User user = authenticated.getPrincipal();
		Object id = user.getAttribute("id");
		if (id == null) {
			throw new SocialLoginFailedException(FAILED_MESSAGE);
		}
		Map<?, ?> account = user.getAttribute("kakao_account") instanceof Map<?, ?> map ? map : Map.of();
		boolean emailVerified = Boolean.TRUE.equals(account.get("is_email_valid"))
			&& Boolean.TRUE.equals(account.get("is_email_verified"));
		String email = emailVerified && account.get("email") instanceof String text ? text : null;
		String name = account.get("name") instanceof String text ? text : null;
		OAuth2AccessToken accessToken = authenticated.getAccessToken();
		SocialTokens tokens = new SocialTokens(accessToken.getTokenValue(), accessToken.getExpiresAt(),
			authenticated.getRefreshToken() == null ? null : authenticated.getRefreshToken().getTokenValue(),
			Set.copyOf(accessToken.getScopes()));
		return new SocialIdentity(SocialProvider.KAKAO, String.valueOf(id), email, name, tokens);
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
