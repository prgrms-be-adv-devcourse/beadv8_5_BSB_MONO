package com.bukang.member.domain;

import static lombok.AccessLevel.*;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
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
 * 회원과 소셜 계정의 연결 (BE-035). 제공자 토큰은 저장하지 않고, 누구인지 알아보는 회원번호만 둔다
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

	SocialAccount(Member member, SocialProvider provider, String providerUserId) {
		this.member = member;
		this.provider = provider;
		this.providerUserId = providerUserId;
	}
}
