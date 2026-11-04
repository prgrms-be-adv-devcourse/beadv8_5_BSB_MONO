package com.bukang.member.app;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.bukang.member.config.identity.IdentityVerificationProperties;

/**
 * CI를 HMAC-SHA256(서버 비밀 키, CI)의 16진수 64자로 바꾼다 (BUK-33)
 * CI는 주민등록번호로 만든 평생 바뀌지 않는 값이라 원문을 저장하지 않는다. 비밀 키를 섞어 DB가 새도 되살릴 수 없게 한다
 */
@Component
public class IdentityCiHasher {
	private static final String ALGORITHM = "HmacSHA256";

	private final byte[] key;

	public IdentityCiHasher(IdentityVerificationProperties properties) {
		this.key = properties.ciHashKey().getBytes(StandardCharsets.UTF_8);
	}

	public String hash(String ci) {
		// 키가 없으면 누구나 같은 해시를 만들 수 있어 저장하지 않는다. 서버 설정 문제라 500으로 둔다
		if (key.length == 0) {
			throw new IllegalStateException("CI 해시 키(IDENTITY_CI_HASH_KEY)가 설정되지 않았습니다.");
		}
		try {
			// Mac은 스레드 안전하지 않으므로 호출마다 새로 만든다
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(key, ALGORITHM));
			return HexFormat.of().formatHex(mac.doFinal(ci.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("CI 해시를 만들지 못했습니다.", exception);
		}
	}
}
