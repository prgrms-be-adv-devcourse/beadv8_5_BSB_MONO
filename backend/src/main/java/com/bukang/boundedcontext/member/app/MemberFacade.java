package com.bukang.boundedcontext.member.app;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.boundedcontext.member.domain.Member;
import com.bukang.boundedcontext.member.out.MemberRepository;
import com.bukang.shared.member.dto.MemberDto;
import com.bukang.shared.member.dto.MemberJoinRequestDto;
import com.bukang.shared.member.dto.MemberLoginRequestDto;
import com.bukang.shared.member.exception.MemberNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberFacade {
	private final MemberJoinUseCase memberJoinUseCase;
	private final MemberRepository memberRepository;
	private final AuthenticationManager authenticationManager;

	@Transactional
	public MemberDto join(MemberJoinRequestDto memberJoinDto) {
		return memberJoinUseCase.join(
			memberJoinDto.getUsername(),
			memberJoinDto.getEmail(),
			memberJoinDto.getPassword(),
			memberJoinDto.getNickname(),
			memberJoinDto.getPhone()
		).toDto();
	}

	public boolean existsByUsername(String username) {
		return memberRepository.existsByUsername(username);
	}

	public MemberDto login(MemberLoginRequestDto memberLoginRequestDto) {
		Authentication authentication = authenticationManager.authenticate(
			new UsernamePasswordAuthenticationToken(
				memberLoginRequestDto.getEmail(),
				memberLoginRequestDto.getPassword()
			)
		);
		SecurityContextHolder.getContext().setAuthentication(authentication);

		Member member = memberRepository.findByEmail(memberLoginRequestDto.getEmail())
			.orElseThrow(() -> new MemberNotFoundException("존재하지 않는 회원입니다."));

		return member.toDto();
	}
}
