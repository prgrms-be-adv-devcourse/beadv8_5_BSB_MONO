package com.bukang.member.app;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.bukang.member.config.MarketingUnsubscribeProperties;
import com.bukang.member.domain.exception.UnsubscribeTokenExpiredException;
import com.bukang.member.domain.exception.UnsubscribeTokenInvalidException;
import com.bukang.member.domain.exception.UnsubscribeTokenMalformedException;

import lombok.extern.slf4j.Slf4j;

/**
 * CRM 광고 수신 거부 토큰 (BUK-75, BE-039). 모양은 「회원 ID.만료 시각(초).서명」이고 DB에 저장하지 않는다
 * 서명은 HMAC-SHA256(서버 비밀 키, "회원 ID.만료 시각")이라, 회원 ID나 만료 시각을 바꾸면 서명이 맞지 않는다
 * 회원 ID 말고는 개인정보를 넣지 않는다
 */
@Slf4j
@Component
public class MarketingUnsubscribeTokens {
	private static final String ALGORITHM = "HmacSHA256";
	// HMAC-SHA256 출력 길이. 이보다 짧은 키는 발급된 토큰 하나로 오프라인에서 맞혀 볼 수 있다
	static final int MIN_KEY_BYTES = 32;
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

	private final byte[] key;

	public MarketingUnsubscribeTokens(MarketingUnsubscribeProperties properties) {
		this.key = properties.secretKey().getBytes(StandardCharsets.UTF_8);
		// 서버는 띄우되 설정이 빠졌음을 바로 알린다. 서버가 안 뜨면 이 기능과 상관없는 로그인·가입까지 멈춘다
		if (key.length < MIN_KEY_BYTES || properties.frontBaseUrl().isBlank()) {
			log.error("수신 거부 링크 설정이 없습니다. MARKETING_UNSUBSCRIBE_KEY({}바이트 이상)와 FRONT_PUBLIC_BASE_URL을 넣어 주세요."
				+ " 넣기 전에는 CRM 링크 발급과 수신 거부가 500으로 실패합니다", MIN_KEY_BYTES);
		}
	}

	public String issue(long memberId, Instant expiresAt) {
		String payload = memberId + "." + expiresAt.getEpochSecond();
		return payload + "." + ENCODER.encodeToString(sign(payload));
	}

	// 서명을 먼저 확인하고 만료를 본다. 서명이 틀린 토큰의 만료 시각은 믿을 수 없기 때문이다
	public long verify(String token, Instant now) {
		String[] parts = token.split("\\.", -1);
		if (parts.length != 3) {
			throw malformed();
		}
		long memberId;
		long expiresAt;
		byte[] signature;
		try {
			memberId = Long.parseLong(parts[0]);
			expiresAt = Long.parseLong(parts[1]);
			signature = DECODER.decode(parts[2]);
		} catch (IllegalArgumentException exception) {
			throw malformed();
		}
		// 패딩(=)을 붙이거나 마지막 글자의 남는 비트를 바꾼 다른 표기를 막아, 한 토큰의 표기가 하나뿐이게 한다
		if (signature.length == 0 || !ENCODER.encodeToString(signature).equals(parts[2])) {
			throw malformed();
		}
		// 시간이 일정한 비교로, 맞춘 글자 수를 응답 시간으로 알아낼 수 없게 한다
		if (!MessageDigest.isEqual(sign(parts[0] + "." + parts[1]), signature)) {
			throw new UnsubscribeTokenInvalidException("수신 거부 링크가 올바르지 않습니다.");
		}
		if (now.getEpochSecond() >= expiresAt) {
			throw new UnsubscribeTokenExpiredException("수신 거부 링크의 사용 기간이 지났습니다. 마이페이지에서 수신 동의를 바꿔주세요.");
		}
		return memberId;
	}

	private byte[] sign(String payload) {
		// 키가 없거나 짧으면 서명을 위조할 수 있어 막는다. 서버 설정 문제라 500으로 둔다
		if (key.length < MIN_KEY_BYTES) {
			throw new IllegalStateException(
				"수신 거부 서명 키(MARKETING_UNSUBSCRIBE_KEY)가 없거나 " + MIN_KEY_BYTES + "바이트보다 짧습니다.");
		}
		try {
			// Mac은 스레드 안전하지 않으므로 호출마다 새로 만든다
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(key, ALGORITHM));
			return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("수신 거부 서명을 만들지 못했습니다.", exception);
		}
	}

	private static UnsubscribeTokenMalformedException malformed() {
		return new UnsubscribeTokenMalformedException("수신 거부 링크의 형식이 올바르지 않습니다.");
	}
}
