package com.bukang.member.crew.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 초대 토큰 만들기와 해시 (BE-028)
 * 토큰은 SecureRandom 16바이트(128비트)를 URL에 쓸 수 있는 Base64(패딩 없음, 22자)로 만든다
 * DB에는 SHA-256 값(16진수 64자)만 저장하고, 들어온 토큰도 같은 방식으로 해시해서 찾는다
 */
final class CrewInviteToken {
	private static final int TOKEN_BYTES = 16;
	private static final SecureRandom RANDOM = new SecureRandom();

	private CrewInviteToken() {
	}

	static String generate() {
		byte[] bytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			// 모든 자바 구현이 SHA-256을 제공한다
			throw new IllegalStateException(exception);
		}
	}
}
