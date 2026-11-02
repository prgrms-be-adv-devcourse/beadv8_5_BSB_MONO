package com.bukang.member.app;

import java.util.stream.Collectors;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.member.domain.Member;
import com.bukang.member.domain.SocialProvider;
import com.bukang.member.domain.exception.MemberNotFoundException;
import com.bukang.member.domain.exception.SocialAccountLoginException;
import com.bukang.member.dto.AuthTokens;
import com.bukang.member.dto.EmailAvailabilityResponseDto;
import com.bukang.member.dto.MemberAdminDto;
import com.bukang.member.dto.MemberDto;
import com.bukang.member.dto.MemberJoinRequestDto;
import com.bukang.member.dto.MemberLoginRequestDto;
import com.bukang.member.dto.MemberLoginResult;
import com.bukang.member.dto.MemberModifyRequestDto;
import com.bukang.member.out.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberFacade {
	private final MemberJoinUseCase memberJoinUseCase;
	private final MemberModifyUseCase memberModifyUseCase;
	private final MemberWithdrawUseCase memberWithdrawUseCase;
	private final MemberRepository memberRepository;
	private final AuthenticationManager authenticationManager;
	private final TokenIssueUseCase tokenIssueUseCase;
	private final TokenReissueUseCase tokenReissueUseCase;
	private final LogoutUseCase logoutUseCase;
	private final AdminVerifyUseCase adminVerifyUseCase;

	@Transactional
	public MemberDto join(MemberJoinRequestDto memberJoinDto) {
		return memberJoinUseCase.join(
			memberJoinDto.getEmail(),
			memberJoinDto.getPassword(),
			memberJoinDto.getName(),
			memberJoinDto.getNickname(),
			memberJoinDto.getPhone(),
			memberJoinDto.getBirthDate(),
			memberJoinDto.getGender(),
			memberJoinDto.getAgreedTerms()
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
		return memberModifyUseCase.modify(
			memberId,
			memberModifyRequestDto.getName(),
			memberModifyRequestDto.getNickname(),
			memberModifyRequestDto.getPhone(),
			memberModifyRequestDto.getBirthDate()
		).toDto();
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
