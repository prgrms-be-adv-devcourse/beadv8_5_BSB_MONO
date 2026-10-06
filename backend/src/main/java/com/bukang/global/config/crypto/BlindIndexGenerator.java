package com.bukang.global.config.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 암호화된 컬럼을 검색하기 위한 블라인드 인덱스(HMAC-SHA256) 생성기
 */
@Component
public class BlindIndexGenerator {
	private static final String ALGORITHM = "HmacSHA256";

	private final SecretKeySpec key;

	public BlindIndexGenerator(@Value("${crypto.hmac-secret}") String hmacSecret) {
		this.key = new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
	}

	// 010-1234-5678, 010 1234 5678, 01012345678을 같은 번호로 판단하도록 숫자만 남긴다
	public String generatePhone(String phone) {
		return hmac("phone:" + phone.replaceAll("[^0-9]", ""));
	}

	// 필드 이름을 앞에 붙여, 다른 필드에 같은 값이 들어와도 해시가 겹치지 않게 한다
	private String hmac(String value) {
		try {
			// Mac은 스레드 안전하지 않으므로 호출마다 새로 만든다
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(key);
			byte[] hash = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("블라인드 인덱스 생성에 실패했습니다.", e);
		}
	}
}
