package com.bukang.member.app;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.bukang.member.domain.Member;
import com.bukang.member.domain.SocialAccount;
import com.bukang.member.domain.exception.DuplicatePhoneException;
import com.bukang.member.domain.exception.SocialAccountAlreadyLinkedException;
import com.bukang.member.domain.exception.SocialEmailRequiredException;
import com.bukang.member.domain.exception.SocialPhoneTakenException;
import com.bukang.member.domain.exception.SocialSignupExpiredException;
import com.bukang.member.dto.MemberLoginResult;
import com.bukang.member.dto.SocialJoinRequestDto;
import com.bukang.member.dto.SocialSignupInfoDto;
import com.bukang.member.out.SocialAccountRepository;
import com.bukang.member.out.oauth2.PendingSignup;
import com.bukang.member.out.oauth2.SocialLoginStore;

import lombok.RequiredArgsConstructor;

/**
 * 소셜로 처음 온 사람의 가입 (BE-033). 콜백이 Redis에 남긴 가입 대기로 어느 소셜 계정인지 안다
 * 회원과 소셜 연결을 한 트랜잭션에서 만들고, 로그인과 같은 토큰을 발급한다 (BE-036)
 */
@Service
@RequiredArgsConstructor
public class SocialJoinUseCase {
	static final String EXPIRED_MESSAGE = "소셜 가입 시간이 지났습니다. 소셜 로그인부터 다시 해주세요.";
	static final String ALREADY_LINKED_MESSAGE = "이미 다른 회원에 연결된 소셜 계정입니다.";
	static final String PHONE_TAKEN_MESSAGE = "이 휴대폰 번호로 가입한 계정이 있습니다. 원래 쓰던 방법으로 로그인한 뒤 계정 연결에서 연결해주세요.";

	private final MemberJoinUseCase memberJoinUseCase;
	private final SocialAccountRepository socialAccountRepository;
	private final SocialLoginStore socialLoginStore;
	private final TokenIssueUseCase tokenIssueUseCase;

	// 가입 화면에 미리 채울 값. 확인된 이메일이 있으면 고칠 수 없게 보여 준다
	public SocialSignupInfoDto findSignupInfo(String pendingId) {
		PendingSignup pending = findPending(pendingId);
		return new SocialSignupInfoDto(pending.provider(), pending.verifiedEmail(), pending.verifiedEmail() != null,
			pending.name());
	}

	@Transactional
	public MemberLoginResult join(String pendingId, SocialJoinRequestDto request) {
		PendingSignup pending = findPending(pendingId);
		// 제공자가 확인한 이메일이 있으면 그 값을 쓴다. 본문으로 다른 이메일을 보내도 바꿀 수 없다
		String email = pending.verifiedEmail() != null ? pending.verifiedEmail() : request.getEmail();
		if (email == null || email.isBlank()) {
			throw new SocialEmailRequiredException("이메일을 입력해주세요.");
		}
		// 15분 안에 다른 탭에서 같은 소셜 계정으로 먼저 가입했을 수 있다
		if (socialAccountRepository.existsByProviderAndProviderUserId(pending.provider(), pending.providerUserId())) {
			throw new SocialAccountAlreadyLinkedException(ALREADY_LINKED_MESSAGE);
		}

		// 휴대폰 번호는 회원마다 하나라서, 겹치면 이미 가입한 사람이 다른 이메일의 소셜 계정으로 온 것이다
		// 닉네임 중복보다 먼저 확인해야 새 닉네임을 고르게 하지 않고 계정 연결로 안내할 수 있다
		if (memberJoinUseCase.isPhoneTaken(request.getPhone())) {
			throw new SocialPhoneTakenException(PHONE_TAKEN_MESSAGE);
		}

		Member member;
		try {
			member = memberJoinUseCase.joinSocial(email, request.getName(), request.getNickname(),
				request.getPhone(), request.getBirthDate(), request.getGender(), request.getAgreedTerms());
		} catch (DuplicatePhoneException exception) {
			// 위 확인과 저장 사이에 같은 번호로 먼저 가입한 경우
			throw new SocialPhoneTakenException(PHONE_TAKEN_MESSAGE);
		}
		SocialAccount account = member.linkSocial(pending.provider(), pending.providerUserId());
		try {
			// 동시에 같은 소셜 계정으로 가입하면 늦은 쪽이 유니크 제약에 걸린다. 회원 생성도 함께 롤백된다
			socialAccountRepository.saveAndFlush(account);
		} catch (DataIntegrityViolationException exception) {
			throw new SocialAccountAlreadyLinkedException(ALREADY_LINKED_MESSAGE);
		}

		MemberLoginResult result = new MemberLoginResult(member.toDto(), tokenIssueUseCase.issue(member.getId()));
		// 닉네임 중복이나 토큰 발급 실패로 롤백되면 가입 대기를 남겨 다시 시도할 수 있게 하고, 커밋된 뒤에만 지운다
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				socialLoginStore.deletePendingSignup(pendingId);
			}
		});
		return result;
	}

	private PendingSignup findPending(String pendingId) {
		if (pendingId == null) {
			throw new SocialSignupExpiredException(EXPIRED_MESSAGE);
		}
		return socialLoginStore.findPendingSignup(pendingId)
			.orElseThrow(() -> new SocialSignupExpiredException(EXPIRED_MESSAGE));
	}
}
