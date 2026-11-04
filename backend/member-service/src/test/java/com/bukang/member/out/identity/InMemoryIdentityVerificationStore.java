package com.bukang.member.out.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.bukang.member.domain.Gender;
import com.bukang.member.domain.TermsType;
import com.bukang.member.dto.MemberJoinRequestDto;

/**
 * 테스트용 본인인증 저장소. 테스트에서는 Redis를 띄우지 않으므로 메모리에 둔다 (만료 시간은 확인하지 않는다)
 * @Import(InMemoryIdentityVerificationStore.Config.class)로 바꿔 끼운다
 * 가입 테스트는 verify()로 본인인증을 마친 것처럼 결과를 넣고, 받은 토큰으로 가입한다
 */
public class InMemoryIdentityVerificationStore implements IdentityVerificationStore {
	private final Set<String> pending = ConcurrentHashMap.newKeySet();
	private final Map<String, VerifiedIdentity> verified = new ConcurrentHashMap<>();

	@Override
	public void savePending(String identityVerificationId) {
		pending.add(identityVerificationId);
	}

	@Override
	public boolean isPending(String identityVerificationId) {
		return pending.contains(identityVerificationId);
	}

	@Override
	public void saveVerified(String identityVerificationId, VerifiedIdentity identity) {
		verified.put(identityVerificationId, identity);
		pending.remove(identityVerificationId);
	}

	@Override
	public Optional<VerifiedIdentity> findVerified(String identityVerificationId) {
		return Optional.ofNullable(verified.get(identityVerificationId));
	}

	@Override
	public void deleteVerified(String identityVerificationId) {
		verified.remove(identityVerificationId);
	}

	// 본인인증을 마친 결과를 넣고 인증 토큰을 돌려준다. CI 해시는 휴대폰 번호마다 달라 서로 다른 사람으로 본다
	public String verify(String name, LocalDate birthDate, String phone) {
		return verify(name, birthDate, phone, "ci-hash-" + phone.replaceAll("[^0-9]", ""));
	}

	// ciHash가 null이면 CI를 주지 않는 인증 방법(카카오)으로 본다
	// 이름 + 생년월일 + 휴대폰 번호 해시는 서버처럼 세 값이 같으면 같은 값이 되게 만든다 (테스트라 비밀 키 없이 SHA-256)
	public String verify(String name, LocalDate birthDate, String phone, String ciHash) {
		String token = "crewrun-iv-" + UUID.randomUUID();
		String digits = phone.replaceAll("[^0-9]", "");
		saveVerified(token, new VerifiedIdentity(name, birthDate, phone, ciHash,
			sha256("identity:" + name.strip() + "|" + birthDate + "|" + digits),
			sha256("name-birth:" + name.strip() + "|" + birthDate), LocalDateTime.now()));
		return token;
	}

	// 이름 + 생년월일 + 휴대폰 번호 해시를 직접 정한다. 동시 가입을 흉내 내 DB 유니크 제약만 확인할 때 쓴다
	public String verifyWithIdentityHash(String name, LocalDate birthDate, String phone, String identityHash) {
		String token = "crewrun-iv-" + UUID.randomUUID();
		saveVerified(token, new VerifiedIdentity(name, birthDate, phone, null, identityHash,
			sha256("name-birth:" + name.strip() + "|" + birthDate), LocalDateTime.now()));
		return token;
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	// 예전 가입 요청과 같은 순서로 받아, 이름 · 휴대폰 번호 · 생년월일은 본인인증 결과로 넣고 토큰을 담은 요청을 만든다
	public MemberJoinRequestDto joinRequest(String email, String password, String name, String nickname,
		String phone, LocalDate birthDate, Gender gender, List<TermsType> agreedTerms) {
		return new MemberJoinRequestDto(email, password, nickname, gender, agreedTerms,
			verify(name, birthDate, phone));
	}

	public void clear() {
		pending.clear();
		verified.clear();
	}

	@TestConfiguration
	public static class Config {
		@Bean
		@Primary
		public InMemoryIdentityVerificationStore inMemoryIdentityVerificationStore() {
			return new InMemoryIdentityVerificationStore();
		}
	}
}
