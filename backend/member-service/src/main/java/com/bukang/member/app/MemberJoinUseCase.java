package com.bukang.member.app;

import java.time.LocalDate;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.bukang.member.config.crypto.BlindIndexGenerator;
import com.bukang.member.domain.Gender;
import com.bukang.member.domain.Member;
import com.bukang.member.domain.exception.DuplicateEmailException;
import com.bukang.member.domain.exception.DuplicateNicknameException;
import com.bukang.member.domain.exception.DuplicatePhoneException;
import com.bukang.member.domain.exception.UnderAgeException;
import com.bukang.member.out.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemberJoinUseCase {
	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final BlindIndexGenerator blindIndexGenerator;

	// 예외 메시지에는 입력값(개인정보)을 넣지 않는다 (응답과 로그에 평문이 남지 않도록)
	public Member join(
		String email,
		String password,
		String name,
		String nickname,
		String phone,
		LocalDate birthDate,
		Gender gender
	) {
		if (!Member.isJoinableAge(birthDate, LocalDate.now())) {
			throw new UnderAgeException("만 14세 미만은 가입할 수 없습니다.");
		}
		if (memberRepository.existsByEmail(email)) {
			throw new DuplicateEmailException("이미 사용 중인 이메일입니다.");
		}
		if (memberRepository.existsByNickname(nickname)) {
			throw new DuplicateNicknameException("이미 사용 중인 닉네임입니다.");
		}

		String phoneHash = blindIndexGenerator.generatePhone(phone);
		if (memberRepository.existsByPhoneHash(phoneHash)) {
			throw new DuplicatePhoneException("이미 사용 중인 전화번호입니다.");
		}

		Member newMember = new Member(
			email,
			passwordEncoder.encode(password),
			name,
			nickname,
			phone,
			phoneHash,
			birthDate,
			gender
		);
		return memberRepository.save(newMember);
	}
}
