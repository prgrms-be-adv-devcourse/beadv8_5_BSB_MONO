package com.bukang.member.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.bukang.member.config.auth.PasswordResetProperties;
import com.bukang.member.domain.Member;
import com.bukang.member.domain.exception.PasswordResetTokenInvalidException;
import com.bukang.member.domain.exception.PasswordResetTooManyRequestsException;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.RefreshTokenRepository;
import com.bukang.member.out.mail.MailDispatcher;
import com.bukang.member.out.passwordreset.PasswordResetStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 비밀번호 재설정 (BUK-40). 메일 링크의 토큰으로 본인을 확인하고 새 비밀번호를 저장한다
 * 요청 응답은 가입 여부와 상관없이 같다. 메일은 MailDispatcher가 요청 스레드 밖에서 보내 응답 시간도 같다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetUseCase {
	static final String TOO_MANY_MESSAGE = "재설정 메일을 너무 자주 요청했습니다. 잠시 뒤 다시 요청해주세요.";
	static final String TOKEN_INVALID_MESSAGE = "재설정 링크가 만료됐거나 이미 사용됐습니다. 다시 요청해주세요.";
	private static final int TOKEN_BYTES = 32;

	private final SecureRandom secureRandom = new SecureRandom();

	private final PasswordResetStore store;
	private final MailDispatcher mailDispatcher;
	private final MemberRepository memberRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final PasswordResetProperties properties;

	/**
	 * 가입하지 않은 이메일, 탈퇴한 회원이면 메일을 보내지 않고 똑같이 끝낸다
	 * 비밀번호가 없는 소셜 가입 회원에게는 링크 대신 소셜 로그인 안내 메일을 보낸다
	 * 횟수 제한은 회원을 찾기 전에 걸어, 가입하지 않은 이메일도 같은 429를 받는다
	 * IP당 제한은 여러 이메일로 Gmail 하루 발송 한도를 다 써서 진짜 회원이 메일을 못 받는 일을 막는다
	 */
	@Transactional(readOnly = true)
	public void request(String rawEmail, String clientIp) {
		String email = rawEmail.trim();
		if (!store.tryAcquireIpRequest(sha256(clientIp), properties.ipDailyLimit(), properties.dailyWindow())
			|| !store.tryAcquireRequest(sha256(email.toLowerCase(Locale.ROOT)), properties.requestCooldown(),
			properties.dailyLimit(), properties.dailyWindow())) {
			throw new PasswordResetTooManyRequestsException(TOO_MANY_MESSAGE);
		}

		memberRepository.findActiveByEmail(email).ifPresent(this::sendMail);
	}

	// 새 비밀번호 화면을 열 때 링크가 아직 쓸 수 있는지 미리 본다. 토큰을 지우지 않는다
	public void verify(String token) {
		store.findMemberId(sha256(token))
			.orElseThrow(() -> new PasswordResetTokenInvalidException(TOKEN_INVALID_MESSAGE));
	}

	/**
	 * 토큰을 꺼내면서 지우므로 같은 링크로는 한 번만 바꾼다
	 * 커밋된 뒤에 모든 기기의 Refresh Token을 지우고 변경 알림 메일을 보낸다
	 * 저장이 실패하면 로그인은 그대로 남지만 토큰은 이미 지워져 다시 요청해야 한다
	 */
	@Transactional
	public void confirm(String token, String newPassword) {
		long memberId = store.takeMemberId(sha256(token))
			.orElseThrow(() -> new PasswordResetTokenInvalidException(TOKEN_INVALID_MESSAGE));
		// 링크를 받은 뒤 탈퇴했거나 소셜 전용 회원이 됐으면 바꾸지 않는다
		Member member = memberRepository.findActiveById(memberId)
			.filter(Member::hasPassword)
			.orElseThrow(() -> new PasswordResetTokenInvalidException(TOKEN_INVALID_MESSAGE));

		member.changePassword(passwordEncoder.encode(newPassword));

		String email = member.getEmail();
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				afterPasswordChanged(memberId, email);
			}
		});
	}

	private void sendMail(Member member) {
		if (!member.hasPassword()) {
			mailDispatcher.dispatch(
				PasswordResetMails.socialAccount(member.getEmail(), member.socialProviders(), properties.loginLink()));
			return;
		}

		String token = newToken();
		String tokenHash = sha256(token);
		long memberId = member.getId();
		store.saveToken(memberId, tokenHash, properties.tokenTtl());
		// 다시 보내기 전에 그사이 새 링크를 요청했는지 본다. 무효가 된 링크는 보내지 않는다
		mailDispatcher.dispatch(
			PasswordResetMails.resetLink(member.getEmail(), properties.resetLink(token), properties.tokenTtl()),
			() -> store.isLatestToken(memberId, tokenHash));
	}

	// 비밀번호는 이미 커밋됐으므로 여기서 실패해도 응답을 실패로 바꾸지 않는다
	// 로그인 정리가 실패하면 알림 메일에 "모두 풀렸다"고 쓰지 않는다. 남은 로그인이 있다고 믿게 해야 직접 로그아웃한다
	private void afterPasswordChanged(long memberId, String email) {
		boolean loggedOut = false;
		try {
			refreshTokenRepository.deleteAll(memberId);
			loggedOut = true;
			store.deleteLatestToken(memberId);
		} catch (RuntimeException e) {
			log.warn("비밀번호를 바꾼 뒤 로그인 정리에 실패했습니다. memberId={}", memberId, e);
		}
		mailDispatcher.dispatch(PasswordResetMails.passwordChanged(email, properties.requestPageLink(), loggedOut));
	}

	private String newToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	// 토큰은 32바이트 무작위 값이라 해시에서 원문을 찾을 수 없다. 그래서 느린 해시(BCrypt) 대신 SHA-256을 쓴다
	static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
		}
	}
}
