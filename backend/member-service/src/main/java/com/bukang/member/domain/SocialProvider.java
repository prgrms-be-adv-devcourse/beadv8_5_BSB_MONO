package com.bukang.member.domain;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import com.bukang.member.domain.exception.SocialProviderNotFoundException;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 소셜 로그인 제공자 (BE-031). DB에는 이름(KAKAO)을, 주소에는 소문자(kakao)를 쓴다
 */
@Getter
@RequiredArgsConstructor
public enum SocialProvider {
	KAKAO("kakao", "카카오"),
	GOOGLE("google", "구글");

	// 주소 경로(/oauth2/authorize/kakao)와 Spring OAuth2 ClientRegistration ID에 쓰는 값
	private final String pathValue;
	// 안내 문구에 쓰는 이름
	private final String displayName;

	// 안내 문구용: "카카오 또는 구글". 비어 있으면 빈 문자열
	public static String joinDisplayNames(Collection<SocialProvider> providers) {
		return providers.stream().map(SocialProvider::getDisplayName).collect(Collectors.joining(" 또는 "));
	}

	public static SocialProvider fromPath(String pathValue) {
		return Arrays.stream(values())
			.filter(provider -> provider.pathValue.equals(pathValue))
			.findFirst()
			.orElseThrow(() -> new SocialProviderNotFoundException("지원하지 않는 소셜 로그인입니다."));
	}
}
