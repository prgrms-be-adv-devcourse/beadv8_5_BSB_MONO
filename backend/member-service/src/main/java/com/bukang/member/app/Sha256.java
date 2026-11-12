package com.bukang.member.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 값을 SHA-256 16진수 문자열로 만든다. 비밀번호 재설정과 이메일 확인 제한이 Redis 키에 쓴다
 * 토큰은 32바이트 무작위 값이라 해시에서 원문을 찾을 수 없다. 그래서 느린 해시(BCrypt) 대신 SHA-256을 쓴다
 */
final class Sha256 {
	private Sha256() {
	}

	static String hex(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
		}
	}
}
