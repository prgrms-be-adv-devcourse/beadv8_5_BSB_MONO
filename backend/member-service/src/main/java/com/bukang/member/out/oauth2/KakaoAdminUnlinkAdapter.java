package com.bukang.member.out.oauth2;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.member.domain.SocialProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 카카오 연결 끊기 (BE-034): POST https://kapi.kakao.com/v1/user/unlink
 * 사용자 토큰 없이 어드민 키(Authorization: KakaoAK {키})와 회원번호로 부른다. 구글은 토큰을 저장하지 않아 부르지 않는다
 * 어드민 키가 없으면(로컬·키 발급 전) 아무것도 하지 않는다. 등록은 SocialLoginConfig가 한다
 */
@Slf4j
@RequiredArgsConstructor
public class KakaoAdminUnlinkAdapter implements SocialUnlinkPort {
	static final String PATH = "/v1/user/unlink";

	private final RestClient restClient;
	private final String adminKey;

	@Override
	public void unlink(SocialProvider provider, String providerUserId) {
		if (provider != SocialProvider.KAKAO) {
			return;
		}
		if (adminKey == null || adminKey.isBlank()) {
			log.info("카카오 어드민 키가 없어 연결 끊기를 건너뜁니다.");
			return;
		}
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("target_id_type", "user_id");
		form.add("target_id", providerUserId);
		try {
			restClient.post()
				.uri(PATH)
				.header(HttpHeaders.AUTHORIZATION, "KakaoAK " + adminKey)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.toBodilessEntity();
		} catch (RestClientException exception) {
			// 우리 쪽 탈퇴·해제는 이미 커밋됐다. 사용자의 카카오 「연결된 서비스」에 크루런이 남을 뿐이다
			// 다시 시도하지 않는다. 재시도·아웃박스로 옮길지는 남은 일로 둔다
			// 카카오 회원번호는 사람을 알아보는 값이라 탈퇴 뒤 로그에 남지 않게 적지 않는다
			log.warn("카카오 연결 끊기에 실패했습니다. cause={}", exception.toString());
		}
	}
}
