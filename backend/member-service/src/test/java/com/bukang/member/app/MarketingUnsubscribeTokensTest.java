package com.bukang.member.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.member.config.MarketingUnsubscribeProperties;
import com.bukang.member.domain.exception.UnsubscribeTokenInvalidException;

// 수신 거부 토큰의 키 검사와 키가 다를 때의 서명 확인 (BUK-75). API 흐름은 ApiV1MarketingUnsubscribeTest가 확인한다
class MarketingUnsubscribeTokensTest {
	private static final String KEY = "k".repeat(MarketingUnsubscribeTokens.MIN_KEY_BYTES);

	@Test
	@DisplayName("서명 키가 없거나 32바이트보다 짧으면 발급하지 않고 서버 설정 오류(IllegalStateException)로 실패한다")
	void rejectsShortKey() {
		for (String key : new String[] {"", "short-key"}) {
			MarketingUnsubscribeTokens tokens = tokens(key);
			assertThatThrownBy(() -> tokens.issue(1L, Instant.now().plusSeconds(60)))
				.isInstanceOf(IllegalStateException.class);
		}
	}

	@Test
	@DisplayName("다른 키로 서명한 토큰은 서명이 맞지 않는다")
	void rejectsTokenSignedWithOtherKey() {
		String token = tokens("o".repeat(MarketingUnsubscribeTokens.MIN_KEY_BYTES)).issue(7L,
			Instant.now().plusSeconds(60));

		assertThatThrownBy(() -> tokens(KEY).verify(token, Instant.now()))
			.isInstanceOf(UnsubscribeTokenInvalidException.class);
	}

	@Test
	@DisplayName("같은 키로 서명한 기한 안의 토큰은 회원 ID를 돌려준다")
	void verifiesOwnToken() {
		MarketingUnsubscribeTokens tokens = tokens(KEY);

		assertThat(tokens.verify(tokens.issue(7L, Instant.now().plusSeconds(60)), Instant.now())).isEqualTo(7L);
	}

	private static MarketingUnsubscribeTokens tokens(String key) {
		return new MarketingUnsubscribeTokens(
			new MarketingUnsubscribeProperties(key, "http://localhost:3000", Duration.ofDays(365)));
	}
}
