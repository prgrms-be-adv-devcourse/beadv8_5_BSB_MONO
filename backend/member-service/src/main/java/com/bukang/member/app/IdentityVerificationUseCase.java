package com.bukang.member.app;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.bukang.member.domain.exception.IdentityVerificationFailedException;
import com.bukang.member.domain.exception.IdentityVerificationRequiredException;
import com.bukang.member.dto.VerifiedIdentityDto;
import com.bukang.member.out.identity.IdentityVerificationStore;
import com.bukang.member.out.identity.PortOneIdentityClient;
import com.bukang.member.out.identity.PortOneIdentityVerification;
import com.bukang.member.out.identity.VerifiedIdentity;

import lombok.RequiredArgsConstructor;

/**
 * 가입 본인인증 (BUK-33). 서버가 인증 ID를 만들고, 화면이 포트원 창을 연 뒤, 서버가 포트원에서 결과를 직접 확인한다
 * 화면이 보낸 인증 결과는 믿지 않는다. 가입은 여기서 확인해 저장한 값으로만 회원을 만든다
 */
@Service
@RequiredArgsConstructor
public class IdentityVerificationUseCase {
	static final String ID_PREFIX = "crewrun-iv-";
	static final String REQUIRED_MESSAGE = "본인인증을 다시 해주세요.";
	static final String FAILED_MESSAGE = "본인인증을 마치지 못했습니다. 다시 시도해주세요.";

	private final IdentityVerificationStore store;
	private final PortOneIdentityClient portOneIdentityClient;
	private final IdentityHasher hasher;
	private final IdentityDuplicateChecker identityDuplicateChecker;

	// 포트원 창을 열 인증 ID. 이 ID로 만든 결과만 가입에 쓸 수 있다
	public String start() {
		String identityVerificationId = ID_PREFIX + UUID.randomUUID();
		store.savePending(identityVerificationId);
		return identityVerificationId;
	}

	public VerifiedIdentityDto complete(String identityVerificationId) {
		return complete(identityVerificationId, identityDuplicateChecker::checkNotJoined);
	}

	/**
	 * 포트원 결과를 확인하고, check를 통과하면 저장한다. check가 예외를 던지면 저장하지 않는다
	 * 가입은 「이미 가입한 사람인지」, 휴대폰 변경은 「로그인한 회원 본인인지」를 본다 (BE-037)
	 */
	VerifiedIdentityDto complete(String identityVerificationId, Consumer<VerifiedIdentity> check) {
		// 새로고침 · 다시 누르기로 같은 ID를 또 확인하면 저장한 결과를 그대로 돌려준다
		// 검사는 다시 한다. 가입용으로 확인한 결과를 회원 전용 확인으로 받아 남의 정보를 보는 일을 막는다
		VerifiedIdentity saved = store.findVerified(identityVerificationId).orElse(null);
		if (saved != null) {
			check.accept(saved);
			return toDto(identityVerificationId, saved);
		}
		if (!store.isPending(identityVerificationId)) {
			throw new IdentityVerificationRequiredException(REQUIRED_MESSAGE);
		}

		PortOneIdentityVerification result = portOneIdentityClient.find(identityVerificationId);
		// 포트원 결과의 ID가 요청한 ID와 같은지도 본다. 대기가 남아 있으면 같은 ID로 다시 시도할 수 있다
		if (!result.isVerified() || !identityVerificationId.equals(result.id())) {
			throw new IdentityVerificationFailedException(FAILED_MESSAGE);
		}
		// 가입에 꼭 필요한 이름 · 생년월일 · 휴대폰 번호가 없으면 인증을 마치지 못한 것으로 본다
		if (result.name() == null || result.birthDate() == null || result.phoneNumber() == null) {
			throw new IdentityVerificationFailedException(FAILED_MESSAGE);
		}
		// 모든 인증 방법을 받는다. CI는 인증사가 줄 때만 있다(카카오 인증은 주지 않는다)
		String phone = formatPhone(result.phoneNumber());
		VerifiedIdentity identity = new VerifiedIdentity(
			result.name(),
			result.birthDate(),
			phone,
			result.ci() == null || result.ci().isBlank() ? null : hasher.ci(result.ci()),
			hasher.identity(result.name(), result.birthDate(), phone),
			hasher.nameBirth(result.name(), result.birthDate()),
			result.verifiedAt() == null
				? LocalDateTime.now()
				: LocalDateTime.ofInstant(result.verifiedAt(), ZoneId.systemDefault())
		);
		// 가입이면 이미 가입한 사람일 때 인증 직후 알려 준다(시안: 「이미 가입한 사람이에요」). 결과는 저장하지 않는다
		check.accept(identity);
		store.saveVerified(identityVerificationId, identity);
		return toDto(identityVerificationId, identity);
	}

	/**
	 * 가입 · 휴대폰 변경에 쓸 본인 정보를 꺼낸다. 가입이 커밋되면 지운다(실패하면 남아 있어 같은 인증으로 다시 시도할 수 있다)
	 * 트랜잭션 안에서 불러야 한다
	 */
	public VerifiedIdentity takeForJoin(String verificationToken) {
		if (verificationToken == null) {
			throw new IdentityVerificationRequiredException(REQUIRED_MESSAGE);
		}
		VerifiedIdentity identity = store.findVerified(verificationToken)
			.orElseThrow(() -> new IdentityVerificationRequiredException(REQUIRED_MESSAGE));
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				store.deleteVerified(verificationToken);
			}
		});
		return identity;
	}

	// 포트원은 숫자만 준다. 다른 회원과 같은 모양(010-1234-5678)으로 저장한다
	static String formatPhone(String digits) {
		if (digits == null) {
			return null;
		}
		String number = digits.replaceAll("[^0-9]", "");
		int middleEnd = number.length() - 4;
		if (number.length() < 10 || number.length() > 11) {
			return number;
		}
		return number.substring(0, 3) + "-" + number.substring(3, middleEnd) + "-" + number.substring(middleEnd);
	}

	private static VerifiedIdentityDto toDto(String token, VerifiedIdentity identity) {
		return new VerifiedIdentityDto(token, identity.name(), identity.birthDate(), identity.phone());
	}
}
