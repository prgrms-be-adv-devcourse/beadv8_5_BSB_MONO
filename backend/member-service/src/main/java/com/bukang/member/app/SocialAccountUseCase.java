package com.bukang.member.app;

import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.member.app.SocialAccountsUnlinkedEvent.Target;
import com.bukang.member.domain.Member;
import com.bukang.member.domain.SocialAccount;
import com.bukang.member.domain.SocialProvider;
import com.bukang.member.domain.exception.MemberNotFoundException;
import com.bukang.member.domain.exception.SocialAccountAlreadyLinkedException;
import com.bukang.member.domain.exception.SocialProviderAlreadyLinkedException;
import com.bukang.member.dto.MemberDto;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.SocialAccountRepository;
import com.bukang.member.out.oauth2.LinkTicket;
import com.bukang.member.out.oauth2.SocialIdentity;
import com.bukang.member.out.oauth2.SocialLoginStore;

import lombok.RequiredArgsConstructor;

/**
 * 로그인한 회원의 소셜 계정 연결·해제 (BE-032)
 * 연결·해제는 회원 행을 잠그고 처리해, 같은 회원의 해제가 겹쳐 로그인 방법이 하나도 남지 않는 일을 막는다
 */
@Service
@RequiredArgsConstructor
public class SocialAccountUseCase {
	static final String NOT_FOUND_MESSAGE = "존재하지 않는 회원입니다.";
	static final String ALREADY_LINKED_MESSAGE = "이미 다른 회원에 연결된 소셜 계정입니다.";

	private final MemberRepository memberRepository;
	private final SocialAccountRepository socialAccountRepository;
	private final SocialLoginStore socialLoginStore;
	private final ApplicationEventPublisher eventPublisher;

	// 연결 표를 만든다. Access Token은 화면 이동에 실을 수 없어서, 토큰으로 인증된 이 API가 2분짜리 1회용 표를 준다 (결정 3)
	// 표는 컨트롤러가 httpOnly 쿠키로 심는다. 주소에 실으면 남이 만든 링크로 피해자 소셜 계정이 연결될 수 있다
	@Transactional(readOnly = true)
	public String issueLinkTicket(long memberId, SocialProvider provider) {
		Member member = findActive(memberId);
		if (member.hasSocial(provider)) {
			throw new SocialProviderAlreadyLinkedException("이미 연결된 소셜 로그인입니다.");
		}
		String ticket = SocialRedirects.randomId();
		socialLoginStore.saveLinkTicket(ticket, new LinkTicket(memberId, provider));
		return ticket;
	}

	// 콜백에서 받은 소셜 계정을 연결한다. 본인이 로그인해 시작했으므로 이메일은 비교하지 않는다 (결정 4)
	// 이미 이 회원에 연결된 같은 계정이면 그대로 성공으로 본다 (두 번 누른 경우)
	@Transactional
	public void link(long memberId, SocialIdentity identity) {
		Member member = memberRepository.findByIdForUpdate(memberId)
			.filter(found -> !found.isWithdrawn())
			.orElseThrow(() -> new MemberNotFoundException(NOT_FOUND_MESSAGE));
		Optional<SocialAccount> existing = socialAccountRepository.findByProviderAndProviderUserId(
			identity.provider(), identity.providerUserId());
		if (existing.isPresent()) {
			if (existing.get().getMember().getId() == memberId) {
				return;
			}
			throw new SocialAccountAlreadyLinkedException(ALREADY_LINKED_MESSAGE);
		}
		SocialAccount account = member.linkSocial(identity.provider(), identity.providerUserId());
		try {
			// 같은 소셜 계정을 두 회원이 동시에 연결하면 늦은 쪽이 유니크 제약에 걸린다. 여기서 잡으려고 바로 반영한다
			socialAccountRepository.saveAndFlush(account);
		} catch (DataIntegrityViolationException exception) {
			throw new SocialAccountAlreadyLinkedException(ALREADY_LINKED_MESSAGE);
		}
	}

	// 연결을 해제한다. 로그인 방법이 남을 때만 허용하는 규칙은 Member가 확인한다 (결정 6)
	// 카카오면 커밋 뒤 카카오 연결 끊기를 부른다 (BE-034, SocialUnlinkEventListener)
	@Transactional
	public MemberDto unlink(long memberId, SocialProvider provider) {
		Member member = memberRepository.findByIdForUpdate(memberId)
			.filter(found -> !found.isWithdrawn())
			.orElseThrow(() -> new MemberNotFoundException(NOT_FOUND_MESSAGE));
		SocialAccount removed = member.unlinkSocial(provider);
		memberRepository.flush();
		eventPublisher.publishEvent(new SocialAccountsUnlinkedEvent(
			List.of(new Target(removed.getProvider(), removed.getProviderUserId()))));
		return member.toDto();
	}

	/**
	 * 이 이메일로 가입한 회원이 지금 로그인할 수 있는 방법 (BE-032 결정 1). 없으면 빈 값
	 * 비밀번호가 있으면 email, 없으면 연결된 첫 제공자(kakao, google)다
	 */
	@Transactional(readOnly = true)
	public Optional<String> findLoginMethod(String email) {
		return memberRepository.findActiveByEmail(email).map(member -> member.hasPassword()
			? "email"
			: member.socialProviders().stream().findFirst().map(SocialProvider::getPathValue).orElse("email"));
	}

	private Member findActive(long memberId) {
		return memberRepository.findActiveById(memberId)
			.orElseThrow(() -> new MemberNotFoundException(NOT_FOUND_MESSAGE));
	}
}
