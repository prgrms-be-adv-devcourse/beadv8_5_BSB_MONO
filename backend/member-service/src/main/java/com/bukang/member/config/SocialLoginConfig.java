package com.bukang.member.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import com.bukang.member.config.auth.SocialLoginProperties;
import com.bukang.member.domain.SocialProvider;
import com.bukang.member.out.InternalHttpClients;
import com.bukang.member.out.kakao.KakaoTalkClient;
import com.bukang.member.out.kakao.RestKakaoTalkClient;
import com.bukang.member.out.oauth2.KakaoAdminUnlinkAdapter;
import com.bukang.member.out.oauth2.SocialOAuth2Client;
import com.bukang.member.out.oauth2.SocialUnlinkPort;
import com.bukang.member.out.oauth2.SpringSocialOAuth2Client;

import lombok.extern.slf4j.Slf4j;

/**
 * 카카오·구글 로그인 등록 (BE-031). 키가 설정된 제공자만 등록해, 키가 없어도 앱은 뜬다
 * Spring Boot의 OAuth2 자동 설정(spring.security.oauth2.client.*)은 키가 비면 앱이 뜨지 않아 쓰지 않는다
 */
@Slf4j
@Configuration
public class SocialLoginConfig {
	// 카카오 연결 끊기 응답을 기다리는 시간. 탈퇴 응답이 이만큼 늦어질 수 있다
	private static final Duration KAKAO_TIMEOUT = Duration.ofSeconds(2);

	@Bean
	public SocialOAuth2Client socialOAuth2Client(SocialLoginProperties properties) {
		List<ClientRegistration> registrations = new ArrayList<>();
		if (properties.kakao().isConfigured()) {
			registrations.add(kakao(properties));
			registrations.add(kakaoMessage(properties));
		} else {
			log.info("카카오 로그인 키가 없어 카카오 로그인을 등록하지 않습니다.");
		}
		if (properties.google().isConfigured()) {
			registrations.add(google(properties));
		} else {
			log.info("구글 로그인 키가 없어 구글 로그인을 등록하지 않습니다.");
		}
		return new SpringSocialOAuth2Client(registrations);
	}

	@Bean
	public SocialUnlinkPort socialUnlinkPort(SocialLoginProperties properties) {
		return new KakaoAdminUnlinkAdapter(InternalHttpClients.create("https://kapi.kakao.com", KAKAO_TIMEOUT),
			properties.kakao().adminKey());
	}

	// 카카오 친구 목록·메시지 (kakao-crew-invite). 사용자 토큰으로 부르므로 어드민 키는 쓰지 않는다
	@Bean
	public KakaoTalkClient kakaoTalkClient(SocialLoginProperties properties) {
		return new RestKakaoTalkClient(InternalHttpClients.create("https://kapi.kakao.com", KAKAO_TIMEOUT),
			InternalHttpClients.create("https://kauth.kakao.com", KAKAO_TIMEOUT),
			properties.kakao().clientId(), properties.kakao().clientSecret());
	}

	// 카카오 메시지 추가 동의 (kakao-crew-invite 결정 2). 키·콜백 주소는 로그인과 같고, 아직 동의하지 않은 범위만 요청한다
	// 동의항목은 콘솔에서 「이용 중 동의」로 켠다: 카카오톡 메시지 전송(talk_message), 카카오 서비스 내 친구목록(friends)
	private static ClientRegistration kakaoMessage(SocialLoginProperties properties) {
		return ClientRegistration.withClientRegistration(kakao(properties))
			.registrationId(SpringSocialOAuth2Client.KAKAO_MESSAGE_REGISTRATION_ID)
			.scope("talk_message", "friends")
			.build();
	}

	// 카카오는 Spring에 기본 설정이 없어 주소를 직접 적는다. 시크릿은 본문(client_secret_post)으로 보낸다
	// 동의 항목: 닉네임(필수), 카카오계정 이메일(선택). 이름은 비즈 앱 심사 뒤에만 받을 수 있어 요청하지 않는다
	private static ClientRegistration kakao(SocialLoginProperties properties) {
		String id = SocialProvider.KAKAO.getPathValue();
		return ClientRegistration.withRegistrationId(id)
			.clientId(properties.kakao().clientId())
			.clientSecret(properties.kakao().clientSecret())
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri(properties.callbackUri(id))
			.scope("profile_nickname", "account_email")
			.authorizationUri("https://kauth.kakao.com/oauth/authorize")
			.tokenUri("https://kauth.kakao.com/oauth/token")
			.userInfoUri("https://kapi.kakao.com/v2/user/me")
			.userNameAttributeName("id")
			.clientName("Kakao")
			.build();
	}

	// 구글은 Spring 기본 설정(openid, profile, email 범위, ID 토큰 서명 키 주소)을 쓴다
	private static ClientRegistration google(SocialLoginProperties properties) {
		String id = SocialProvider.GOOGLE.getPathValue();
		return CommonOAuth2Provider.GOOGLE.getBuilder(id)
			.clientId(properties.google().clientId())
			.clientSecret(properties.google().clientSecret())
			.redirectUri(properties.callbackUri(id))
			.build();
	}
}
