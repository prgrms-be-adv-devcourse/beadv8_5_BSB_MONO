package com.bukang.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.member.domain.exception.LastLoginMethodException;
import com.bukang.member.domain.exception.SocialAccountNotFoundException;
import com.bukang.member.domain.exception.SocialProviderAlreadyLinkedException;

class MemberSocialTest {
	@Test
	@DisplayName("제공자마다 하나만 연결되고, 연결된 제공자는 카카오·구글 순서로 보인다")
	void linkSocial() {
		Member member = member("hash");
		member.linkSocial(SocialProvider.GOOGLE, "google-1");
		member.linkSocial(SocialProvider.KAKAO, "kakao-1");

		assertThat(member.socialProviders()).containsExactly(SocialProvider.KAKAO, SocialProvider.GOOGLE);
		assertThatThrownBy(() -> member.linkSocial(SocialProvider.KAKAO, "kakao-2"))
			.isInstanceOf(SocialProviderAlreadyLinkedException.class);
	}

	@Test
	@DisplayName("비밀번호가 있으면 마지막 소셜 연결도 해제할 수 있다")
	void unlinkWithPassword() {
		Member member = member("hash");
		member.linkSocial(SocialProvider.KAKAO, "kakao-1");

		SocialAccount removed = member.unlinkSocial(SocialProvider.KAKAO);

		assertThat(removed.getProviderUserId()).isEqualTo("kakao-1");
		assertThat(member.socialProviders()).isEmpty();
	}

	@Test
	@DisplayName("비밀번호가 없으면 다른 소셜 연결이 남을 때만 해제할 수 있고, 마지막 하나는 막는다")
	void unlinkWithoutPassword() {
		Member member = member(null);
		member.linkSocial(SocialProvider.KAKAO, "kakao-1");
		member.linkSocial(SocialProvider.GOOGLE, "google-1");

		member.unlinkSocial(SocialProvider.GOOGLE);

		assertThatThrownBy(() -> member.unlinkSocial(SocialProvider.KAKAO))
			.isInstanceOf(LastLoginMethodException.class);
		assertThat(member.socialProviders()).containsExactly(SocialProvider.KAKAO);
	}

	@Test
	@DisplayName("연결되지 않은 제공자는 해제할 수 없다")
	void unlinkMissing() {
		Member member = member("hash");

		assertThatThrownBy(() -> member.unlinkSocial(SocialProvider.GOOGLE))
			.isInstanceOf(SocialAccountNotFoundException.class);
	}

	@Test
	@DisplayName("탈퇴할 때 모든 소셜 연결을 지우고, 지운 연결을 돌려준다")
	void withdrawRemovesSocialAccounts() {
		Member member = member(null);
		member.linkSocial(SocialProvider.KAKAO, "kakao-1");

		member.withdraw(LocalDateTime.now());

		assertThat(member.unlinkAllSocial())
			.extracting(SocialAccount::getProviderUserId)
			.containsExactly("kakao-1");
		assertThat(member.socialProviders()).isEmpty();
	}

	@Test
	@DisplayName("내 정보에 연결된 제공자와 비밀번호 여부가 들어간다")
	void toDto() {
		Member member = member(null);
		member.linkSocial(SocialProvider.KAKAO, "kakao-1");

		assertThat(member.toDto().getSocialProviders()).containsExactly(SocialProvider.KAKAO);
		assertThat(member.toDto().isPasswordSet()).isFalse();
		assertThat(member("hash").toDto().isPasswordSet()).isTrue();
	}

	private static Member member(String passwordHash) {
		return new Member("runner1@crewrun.com", passwordHash, "김러너", "러너1", "010-1234-5678", "phone-hash",
			LocalDate.of(1995, 4, 12), null);
	}
}
