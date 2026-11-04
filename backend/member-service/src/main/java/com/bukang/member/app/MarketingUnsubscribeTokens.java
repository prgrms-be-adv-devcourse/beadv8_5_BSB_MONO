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

/**
 * CRM 광고 수신 거부 토큰 (BUK-75, BE-039). 모양은 「회원 ID.만료 시각(초).서명」이고 DB에 저장하지 않는다
 * 서명은 HMAC-SHA256(서버 비밀 키, "회원 ID.만료 시각")이라, 회원 ID나 만료 시각을 바꾸면 서명이 맞지 않는다
 * 회원 ID 말고는 개인정보를 넣지 않는다
 */
@Component
public class MarketingUnsubscribeTokens {
	private static final String ALGORITHM = "HmacSHA256";
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

	private final byte[] key;

	public MarketingUnsubscribeTokens(MarketingUnsubscribeProperties properties) {
		this.key = properties.secretKey().getBytes(StandardCharsets.UTF_8);
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
		// 키가 없으면 누구나 같은 서명을 만들 수 있어 막는다. 서버 설정 문제라 500으로 둔다
		if (key.length == 0) {
			throw new IllegalStateException("수신 거부 서명 키(MARKETING_UNSUBSCRIBE_KEY)가 설정되지 않았습니다.");
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
