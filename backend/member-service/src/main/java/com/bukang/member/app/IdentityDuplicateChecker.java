package com.bukang.member.app;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.bukang.member.config.identity.IdentityVerificationProperties;
import com.bukang.member.domain.MemberStatus;
import com.bukang.member.domain.exception.DuplicateIdentityException;
import com.bukang.member.domain.exception.RejoinTooSoonException;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.identity.VerifiedIdentity;

import lombok.RequiredArgsConstructor;

/**
 * 본인인증한 사람이 이미 가입했는지 본다 (BUK-33, 1인 1계정). 본인인증 확인 직후와 가입 요청 두 곳에서 같은 기준으로 쓴다
 * CI 해시가 같거나, 이름 + 생년월일 + 휴대폰 번호 해시가 같으면 같은 사람이다
 * 탈퇴하고 30일이 지나지 않은 같은 사람도 막는다(재가입 제한)
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
		checkRejoinAllowed(identity);
	}

	/**
	 * 탈퇴하고 정한 날수(기본 30일)가 지나지 않은 같은 사람은 다시 가입하지 못한다 (11/2 지훈 님 결정)
	 * 날짜 단위로 센다. 11월 2일에 탈퇴했으면 30일 뒤인 12월 2일 0시부터 가입할 수 있다
	 * CI 검사를 끈 환경(테스트 채널 CI가 모두 같음)에서는 CI로 막지 않는다. 막으면 누가 탈퇴할 때마다 모두가 30일 동안 막힌다
	 */
	private void checkRejoinAllowed(VerifiedIdentity identity) {
		if (identity.identityHash() == null) {
			return;
		}
		int days = properties.rejoinBlockDays();
		String ciHash = properties.uniqueCiCheck() ? identity.ciHash() : null;
		LocalDateTime withdrawnAt = memberRepository.findLatestWithdrawnAt(identity.identityHash(), ciHash,
			rejoinBlockStart(LocalDate.now(), days));
		if (withdrawnAt != null) {
			throw new RejoinTooSoonException(withdrawnAt.toLocalDate().plusDays(days), days);
		}
	}

	// 이 시각 뒤에 탈퇴한 회원은 아직 재가입 제한 기간이다 (오늘 포함 days일 안에 탈퇴)
	static LocalDateTime rejoinBlockStart(LocalDate today, int days) {
		return today.minusDays(days - 1L).atStartOfDay();
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
