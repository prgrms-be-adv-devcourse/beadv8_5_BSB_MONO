package com.bukang.member.app;

import org.springframework.stereotype.Component;

import com.bukang.member.config.identity.IdentityVerificationProperties;
import com.bukang.member.domain.MemberStatus;
import com.bukang.member.domain.exception.DuplicateIdentityException;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.identity.VerifiedIdentity;

import lombok.RequiredArgsConstructor;

/**
 * 본인인증한 사람이 이미 가입했는지 본다 (BUK-33, 1인 1계정). 본인인증 확인 직후와 가입 요청 두 곳에서 같은 기준으로 쓴다
 * CI 해시가 같거나, 이름 + 생년월일 + 휴대폰 번호 해시가 같으면 같은 사람이다
 */
@Component
@RequiredArgsConstructor
class IdentityDuplicateChecker {
	private final MemberRepository memberRepository;
	private final IdentityVerificationProperties properties;

	void checkNotJoined(VerifiedIdentity identity) {
		if (isCiTaken(identity.ciHash()) || isIdentityTaken(identity.identityHash())) {
			throw new DuplicateIdentityException(MemberDuplicateExceptions.DUPLICATE_IDENTITY_MESSAGE);
		}
	}

	// CI는 인증사가 줄 때만 있다. 탈퇴한 회원은 이메일 · 번호처럼 다시 가입할 수 있게 뺀다
	// 테스트 채널 CI가 모두 같은 값이면 설정(member.identity.unique-ci-check)으로 끈다
	private boolean isCiTaken(String ciHash) {
		return ciHash != null && properties.uniqueCiCheck()
			&& memberRepository.existsByCiHashAndStatusNot(ciHash, MemberStatus.WITHDRAWN);
	}

	// 탈퇴하면 이 해시를 비우므로 탈퇴한 회원은 걸리지 않는다
	private boolean isIdentityTaken(String identityHash) {
		return identityHash != null && memberRepository.existsByIdentityHash(identityHash);
	}
}
