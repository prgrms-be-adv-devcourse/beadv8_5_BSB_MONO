package com.bukang.member.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.member.config.MarketingUnsubscribeProperties;
import com.bukang.member.domain.Member;
import com.bukang.member.domain.SocialProvider;
import com.bukang.member.domain.exception.MemberNicknameLookupTooManyException;
import com.bukang.member.domain.exception.MemberNotFoundException;
import com.bukang.member.domain.exception.SocialAccountLoginException;
import com.bukang.member.dto.AuthTokens;
import com.bukang.member.dto.EmailAvailabilityResponseDto;
import com.bukang.member.dto.MarketingConsentDto;
import com.bukang.member.dto.MarketingUnsubscribeLinkDto;
import com.bukang.member.dto.MemberAdminDto;
import com.bukang.member.dto.MemberDto;
import com.bukang.member.dto.MemberJoinRequestDto;
import com.bukang.member.dto.MemberLoginRequestDto;
import com.bukang.member.dto.MemberLoginResult;
import com.bukang.member.dto.MemberModifyRequestDto;
import com.bukang.member.dto.MemberNicknameDto;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.identity.VerifiedIdentity;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberFacade {
	public static final int MAX_NICKNAME_LOOKUP = 100;

	private final MemberJoinUseCase memberJoinUseCase;
	private final IdentityVerificationUseCase identityVerificationUseCase;
	private final MemberModifyUseCase memberModifyUseCase;
	private final MemberWithdrawUseCase memberWithdrawUseCase;
	private final MemberRepository memberRepository;
	private final AuthenticationManager authenticationManager;
	private final TokenIssueUseCase tokenIssueUseCase;
	private final TokenReissueUseCase tokenReissueUseCase;
	private final LogoutUseCase logoutUseCase;
	private final AdminVerifyUseCase adminVerifyUseCase;
	private final MarketingConsentUseCase marketingConsentUseCase;
	private final MarketingUnsubscribeTokens marketingUnsubscribeTokens;
	private final MarketingUnsubscribeProperties marketingUnsubscribeProperties;
	private final Clock clock;

	@Transactional
	public MemberDto join(MemberJoinRequestDto memberJoinDto) {
		VerifiedIdentity identity = identityVerificationUseCase.takeForJoin(memberJoinDto.getVerificationToken());
		return memberJoinUseCase.join(
			memberJoinDto.getEmail(),
			memberJoinDto.getPassword(),
			memberJoinDto.getNickname(),
			memberJoinDto.getGender(),
			memberJoinDto.getAgreedTerms(),
			identity
		).toDto();
	}

	public EmailAvailabilityResponseDto checkEmailAvailability(String email) {
		return new EmailAvailabilityResponseDto(!memberRepository.existsByEmail(email));
	}

	public MemberDto findMe(long memberId) {
		// 탈퇴한 회원은 없는 회원으로 본다 (BE-022)
		return memberRepository.findActiveById(memberId)
			.orElseThrow(() -> new MemberNotFoundException("존재하지 않는 회원입니다."))
			.toDto();
	}

	@Transactional
	public MemberDto modifyMe(long memberId, MemberModifyRequestDto memberModifyRequestDto) {
		return memberModifyUseCase.modify(memberId, memberModifyRequestDto.getNickname()).toDto();
	}

	public MarketingConsentDto findMarketingConsent(long memberId) {
		return marketingConsentUseCase.find(memberId);
	}

	@Transactional
	public MarketingConsentDto changeMarketingConsent(long memberId, boolean agreed) {
		return marketingConsentUseCase.change(memberId, agreed);
	}

	// CRM이 광고를 보낼 때 메시지 끝에 붙일 링크를 받아 간다. 서명 키는 회원 서비스 밖으로 나가지 않는다 (BE-039)
	public MarketingUnsubscribeLinkDto issueUnsubscribeLink(long memberId) {
		memberRepository.findActiveById(memberId)
			.orElseThrow(() -> new MemberNotFoundException("존재하지 않는 회원입니다."));
		Instant expiresAt = Instant.now(clock).plus(marketingUnsubscribeProperties.tokenTtl());
		String token = marketingUnsubscribeTokens.issue(memberId, expiresAt);
		return new MarketingUnsubscribeLinkDto(marketingUnsubscribeProperties.link(token),
			LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault()));
	}

	// 수신 거부는 마케팅 동의 철회로 남긴다. 이미 거부했으면 동의 변경이 아무것도 남기지 않아 두 번 눌러도 같다 (BE-039)
	@Transactional
	public void unsubscribeMarketing(String token) {
		marketingConsentUseCase.change(marketingUnsubscribeTokens.verify(token, Instant.now(clock)), false);
	}

	@Transactional
	public void withdrawMe(long memberId) {
		memberWithdrawUseCase.withdraw(memberId);
	}

	// 관리자 역할을 준다. 관리자 지정 API는 없고 개발용 기본 데이터(MemberDataInit)에서만 부른다
	@Transactional
	public void grantAdmin(String email) {
		memberRepository.findByEmail(email)
			.orElseThrow(() -> new MemberNotFoundException("존재하지 않는 회원입니다."))
			.grantAdmin();
	}

	// 다른 서비스가 회원 ID 목록으로 닉네임을 받아 간다 (BUK-39). IN 쿼리 한 번으로 읽고, 없는 회원 ID는 결과에서 빠진다
	public List<MemberNicknameDto> findNicknames(List<Long> memberIds) {
		List<Long> distinctIds = memberIds.stream().filter(Objects::nonNull).distinct().toList();
		if (distinctIds.size() > MAX_NICKNAME_LOOKUP) {
			throw new MemberNicknameLookupTooManyException(MAX_NICKNAME_LOOKUP);
		}
		if (distinctIds.isEmpty()) {
			return List.of();
		}
		return memberRepository.findNicknamesByIdIn(distinctIds);
	}

	public MemberAdminDto checkAdmin(long memberId) {
		return new MemberAdminDto(adminVerifyUseCase.isAdmin(memberId));
	}

	public boolean existsByEmail(String email) {
		return memberRepository.existsByEmail(email);
	}

	public MemberLoginResult login(MemberLoginRequestDto memberLoginRequestDto) {
		// 비밀번호 없는 소셜 회원이면 가입한 제공자로 로그인하라고 알려 준다 (BE-035)
		// 가입 여부는 이메일 중복 확인 API로도 알 수 있어(BE-023) 새로 드러나는 정보가 없다
		memberRepository.findActiveByEmail(memberLoginRequestDto.getEmail())
			.filter(member -> !member.hasPassword())
			.ifPresent(member -> {
				throw new SocialAccountLoginException(socialAccountMessage(member));
			});

		// 이메일·비밀번호 확인만 한다. 로그인 상태는 세션이 아니라 토큰으로 유지하므로 인증 결과를 저장하지 않는다
		authenticationManager.authenticate(
			new UsernamePasswordAuthenticationToken(
				memberLoginRequestDto.getEmail(),
				memberLoginRequestDto.getPassword()
			)
		);

		Member member = memberRepository.findActiveByEmail(memberLoginRequestDto.getEmail())
			.orElseThrow(() -> new MemberNotFoundException("존재하지 않는 회원입니다."));

		return new MemberLoginResult(member.toDto(), tokenIssueUseCase.issue(member.getId()));
	}

	// 예: "카카오로 가입한 계정입니다. 카카오로 로그인해주세요." 둘 다 연결돼 있으면 "카카오 또는 구글로"
	private static String socialAccountMessage(Member member) {
		String providers = member.socialProviders().stream()
			.map(SocialProvider::getDisplayName)
			.collect(Collectors.joining(" 또는 "));
		if (providers.isEmpty()) {
			return "소셜 로그인으로 가입한 계정입니다. 소셜 로그인으로 로그인해주세요.";
		}
		return providers + "로 가입한 계정입니다. " + providers + "로 로그인해주세요.";
	}

	// Refresh Token도 회전하므로 Access Token과 새 Refresh Token을 함께 돌려준다
	public AuthTokens reissue(String refreshToken) {
		return tokenReissueUseCase.reissue(refreshToken);
	}

	public void logout(String refreshToken) {
		logoutUseCase.logout(refreshToken);
	}
}
