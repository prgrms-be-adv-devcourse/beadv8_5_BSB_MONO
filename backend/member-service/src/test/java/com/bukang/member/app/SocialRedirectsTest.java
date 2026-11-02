package com.bukang.member.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SocialRedirectsTest {
	@Test
	@DisplayName("앱 내부 경로는 쿼리까지 그대로 쓰고, # 뒤는 버린다")
	void keepsInternalPath() {
		assertThat(SocialRedirects.safeNextPath("/races/12?tab=course", "/")).isEqualTo("/races/12?tab=course");
		assertThat(SocialRedirects.safeNextPath("/me#top", "/")).isEqualTo("/me");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"https://evil.com", "javascript:alert(1)", "//evil.com", "/\\evil.com", "/ /evil.com",
		"/\t/evil.com", "races", "/login", "/login?next=/me"})
	@DisplayName("다른 사이트로 갈 수 있는 값, 앱 밖 경로, 로그인 화면은 fallback으로 바꾼다 (프론트 safeNextPath와 같은 규칙)")
	void rejectsUnsafePath(String next) {
		assertThat(SocialRedirects.safeNextPath(next, "/me/accounts")).isEqualTo("/me/accounts");
	}

	@Test
	@DisplayName("쿼리 값은 인코딩해 붙이고, 이미 쿼리가 있으면 &로 잇는다")
	void withQuery() {
		assertThat(SocialRedirects.withQuery("/auth/social/complete", "next", "/races?tab=a&b=1"))
			.isEqualTo("/auth/social/complete?next=%2Fraces%3Ftab%3Da%26b%3D1");
		assertThat(SocialRedirects.withQuery("/me/accounts?x=1", "linked", "kakao"))
			.isEqualTo("/me/accounts?x=1&linked=kakao");
	}

	@Test
	@DisplayName("무작위 ID는 주소·쿠키에 그대로 쓸 수 있는 43글자(32바이트) 값이고 매번 다르다")
	void randomId() {
		String first = SocialRedirects.randomId();
		assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]+");
		assertThat(SocialRedirects.randomId()).isNotEqualTo(first);
	}
}
