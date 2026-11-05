package com.bukang.member.app;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.bukang.member.config.identity.IdentityVerificationProperties;

/**
 * 본인인증 값을 HMAC-SHA256(서버 비밀 키, 값)의 16진수 64자로 바꾼다 (BUK-33)
 * 원문을 저장하지 않고 비밀 키를 섞어, DB가 새도 CI나 이름 · 생년월일 · 휴대폰 번호를 되살릴 수 없게 한다
 * 이름 + 생년월일 + 휴대폰 번호와 이름 + 생년월일은 입력 앞에 용도 글자를 붙여 CI 해시와 값이 섞이지 않게 한다
 */
@Component
public class IdentityHasher {
	private static final String ALGORITHM = "HmacSHA256";
	private static final String IDENTITY_PREFIX = "identity:";
	private static final String NAME_BIRTH_PREFIX = "name-birth:";

	private final byte[] key;

	public IdentityHasher(IdentityVerificationProperties properties) {
		this.key = properties.ciHashKey().getBytes(StandardCharsets.UTF_8);
	}

	public String ci(String ci) {
		return hmac(ci);
	}

	// 이름은 앞뒤 공백을 빼고, 생년월일은 yyyy-MM-dd, 휴대폰 번호는 숫자만 쓴다. 같은 사람이면 표기가 달라도 같은 값이 된다
	public String identity(String name, LocalDate birthDate, String phone) {
		return hmac(IDENTITY_PREFIX + name.strip() + "|" + birthDate + "|" + phone.replaceAll("[^0-9]", ""));
	}

	public String nameBirth(String name, LocalDate birthDate) {
		return hmac(NAME_BIRTH_PREFIX + name.strip() + "|" + birthDate);
	}

	private String hmac(String value) {
		// 키가 없으면 누구나 같은 해시를 만들 수 있어 저장하지 않는다. 서버 설정 문제라 500으로 둔다
		if (key.length == 0) {
			throw new IllegalStateException("본인인증 해시 키(IDENTITY_CI_HASH_KEY)가 설정되지 않았습니다.");
		}
		try {
			// Mac은 스레드 안전하지 않으므로 호출마다 새로 만든다
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(key, ALGORITHM));
			return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("본인인증 해시를 만들지 못했습니다.", exception);
		}
	}
}
