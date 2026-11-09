package com.bukang.member.domain;

import static lombok.AccessLevel.*;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.TreeSet;

import com.bukang.common.global.crypto.EncryptedStringConverter;
import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원과 소셜 계정의 연결 (BE-035). 누구인지 알아보는 회원번호를 두고, 카카오 메시지 동의를 마친 회원만 토큰을 암호화해 둔다
 * 같은 소셜 계정은 한 회원에만, 한 회원에는 제공자마다 하나만 연결된다 (두 유니크 제약)
 * 만들고 지우는 일은 Member가 한다 (linkSocial, unlinkSocial)
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(name = "MEMBER_SOCIAL_ACCOUNT", uniqueConstraints = {
	@UniqueConstraint(name = "uk_social_account_provider_user", columnNames = {"provider", "provider_user_id"}),
	@UniqueConstraint(name = "uk_social_account_member_provider", columnNames = {"member_id", "provider"})
})
public class SocialAccount extends BaseIdAndTime {
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SocialProvider provider;

	// 카카오 회원번호, 구글 sub. 제공자 안에서 바뀌지 않는 값이다
	@Column(name = "provider_user_id", nullable = false)
	private String providerUserId;

	// 카카오 메시지 추가 동의를 마친 회원만 채운다. 동의 전·다시 동의가 필요하면 모두 null (kakao-crew-invite 결정 1)
	// 토큰은 AES로 암호화해 저장하고, Lombok getter의 값은 로그·응답에 싣지 않는다
	@Convert(converter = EncryptedStringConverter.class)
	@Column(name = "access_token", length = 512)
	private String accessToken;

	@Column(name = "access_token_expires_at")
	private LocalDateTime accessTokenExpiresAt;

	@Convert(converter = EncryptedStringConverter.class)
	@Column(name = "refresh_token", length = 512)
	private String refreshToken;

	// 카카오가 준 동의 범위, 공백으로 구분 (예: profile_nickname talk_message friends)
	@Column(name = "scopes")
	private String scopes;

	SocialAccount(Member member, SocialProvider provider, String providerUserId) {
		this.member = member;
		this.provider = provider;
		this.providerUserId = providerUserId;
	}

	public void saveTokens(String accessToken, LocalDateTime accessTokenExpiresAt, String refreshToken,
		Set<String> scopes) {
		this.accessToken = accessToken;
		this.accessTokenExpiresAt = accessTokenExpiresAt;
		this.refreshToken = refreshToken;
		this.scopes = String.join(" ", new TreeSet<>(scopes));
	}

	// 카카오가 리프레시 토큰을 바꿔 주지 않았으면(null) 기존 값을 그대로 둔다
	public void refreshTokens(String accessToken, LocalDateTime accessTokenExpiresAt, String newRefreshToken) {
		this.accessToken = accessToken;
		this.accessTokenExpiresAt = accessTokenExpiresAt;
		if (newRefreshToken != null) {
			this.refreshToken = newRefreshToken;
		}
	}

	public void clearTokens() {
		this.accessToken = null;
		this.accessTokenExpiresAt = null;
		this.refreshToken = null;
		this.scopes = null;
	}

	public boolean hasScopes(Set<String> required) {
		return accessToken != null && scopes != null && Set.of(scopes.split(" ")).containsAll(required);
	}
}
