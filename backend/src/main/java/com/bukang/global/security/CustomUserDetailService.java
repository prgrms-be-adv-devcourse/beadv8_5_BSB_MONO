package com.bukang.global.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.bukang.boundedcontext.member.domain.Member;
import com.bukang.boundedcontext.member.out.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomUserDetailService implements UserDetailsService {
	private final MemberRepository memberRepository;

	// UsernameNotFoundException은 Spring Security가 BadCredentialsException으로 바꿔 주므로
	// "가입되지 않은 이메일"인지 "비밀번호가 틀렸는지"가 외부에 드러나지 않는다
	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		Member member = memberRepository.findByEmail(email)
			.orElseThrow(() -> new UsernameNotFoundException("존재하지 않는 회원입니다."));

		return new CustomUserDetails(member);
	}
}
