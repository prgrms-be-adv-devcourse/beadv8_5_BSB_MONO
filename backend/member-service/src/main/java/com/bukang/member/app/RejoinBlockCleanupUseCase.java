package com.bukang.member.app;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.member.config.identity.IdentityVerificationProperties;
import com.bukang.member.out.MemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * 재가입 제한 기간(기본 30일)이 지난 탈퇴 회원의 본인인증 해시를 비운다 (BUK-33, 개인정보 최소화)
 * 조회도 기간이 지난 값을 보지 않으므로, 정리가 하루 늦어져도 재가입은 막히지 않는다
 */
@Service
@RequiredArgsConstructor
public class RejoinBlockCleanupUseCase {
	private final MemberRepository memberRepository;
	private final IdentityVerificationProperties properties;

	@Transactional
	public int clearExpired(LocalDate today) {
		return memberRepository.clearRejoinBlockHashes(
			IdentityDuplicateChecker.rejoinBlockStart(today, properties.rejoinBlockDays()));
	}
}
