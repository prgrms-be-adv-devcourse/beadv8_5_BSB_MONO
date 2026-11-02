package com.bukang.member.app;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.bukang.common.global.crypto.BlindIndexGenerator;
import com.bukang.member.config.identity.IdentityVerificationProperties;
import com.bukang.member.domain.Gender;
import com.bukang.member.domain.Member;
import com.bukang.member.domain.MemberStatus;
import com.bukang.member.domain.Terms;
import com.bukang.member.domain.TermsAgreement;
import com.bukang.member.domain.TermsType;
import com.bukang.member.domain.exception.DuplicateEmailException;
import com.bukang.member.domain.exception.DuplicateIdentityException;
import com.bukang.member.domain.exception.DuplicateNicknameException;
import com.bukang.member.domain.exception.DuplicatePhoneException;
import com.bukang.member.domain.exception.RequiredTermsNotAgreedException;
import com.bukang.member.domain.exception.UnderAgeException;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.TermsRepository;
import com.bukang.member.out.identity.VerifiedIdentity;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemberJoinUseCase {
	static final String DUPLICATE_IDENTITY_MESSAGE = "이미 가입한 사람입니다. 로그인하거나 계정 연결에서 연결해주세요.";

	private final MemberRepository memberRepository;
	private final TermsRepository termsRepository;
	private final TermsAgreementRepository termsAgreementRepository;
	private final PasswordEncoder passwordEncoder;
	private final BlindIndexGenerator blindIndexGenerator;
	private final IdentityVerificationProperties identityProperties;

	// 예외 메시지에는 입력값(개인정보)을 넣지 않는다 (응답과 로그에 평문이 남지 않도록)
	// 이름 · 휴대폰 번호 · 생년월일은 본인인증으로 확인한 값만 쓴다 (BUK-33)
	public Member join(
		String email,
		String password,
		String nickname,
		Gender gender,
		List<TermsType> agreedTerms,
		VerifiedIdentity identity
	) {
		return create(email, password, nickname, gender, agreedTerms, identity);
	}

	// 소셜 가입: 비밀번호 없이 만든다 (BE-033·035). 나이·약관·중복·본인인증 검사는 이메일 가입과 같아서 우회할 수 없다
	public Member joinSocial(
		String email,
		String nickname,
		Gender gender,
		List<TermsType> agreedTerms,
		VerifiedIdentity identity
	) {
		return create(email, null, nickname, gender, agreedTerms, identity);
	}

	// 이 휴대폰 번호로 가입한 회원이 있는지 본다. 소셜 가입이 다른 중복 검사보다 먼저 확인해 계정 연결을 안내한다 (BE-033)
	public boolean isPhoneTaken(String phone) {
		return memberRepository.existsByPhoneHash(blindIndexGenerator.generatePhone(phone));
	}

	// password가 null이면 비밀번호 없는 회원(소셜)이다. 암호화는 모든 검사를 통과한 뒤에만 한다
	private Member create(
		String email,
		String password,
		String nickname,
		Gender gender,
		List<TermsType> agreedTerms,
		VerifiedIdentity identity
	) {
		String name = identity.name();
		String phone = identity.phone();
		LocalDate birthDate = identity.birthDate();
		if (!Member.isJoinableAge(birthDate, LocalDate.now())) {
			throw new UnderAgeException("만 14세 미만은 가입할 수 없습니다.");
		}

		// 지금 버전의 약관 가운데 필수 약관이 빠졌으면 막는다 (입력값 검사라 중복 검사보다 먼저 한다)
		// 시행 중인 필수 약관이 하나도 없으면 검사가 빈 채로 통과하므로, 서버 설정 문제로 보고 가입을 막는다
		List<Terms> currentTerms = termsRepository.findCurrent(LocalDate.now());
		if (currentTerms.stream().noneMatch(Terms::isRequired)) {
			throw new IllegalStateException("시행 중인 필수 약관이 없어 가입을 받을 수 없습니다.");
		}
		boolean requiredMissing = currentTerms.stream()
			.anyMatch(terms -> terms.isRequired() && !agreedTerms.contains(terms.getType()));
		if (requiredMissing) {
			throw new RequiredTermsNotAgreedException("필수 약관에 동의해야 가입할 수 있습니다.");
		}

		// 같은 사람은 계정 하나만 만든다 (1인 1계정). 탈퇴한 회원은 이메일 · 번호처럼 다시 가입할 수 있게 뺀다
		// 테스트 채널 CI가 모두 같은 값이면 설정(member.identity.unique-ci-check)으로 끈다
		if (identityProperties.uniqueCiCheck() && identity.ciHash() != null
			&& memberRepository.existsByCiHashAndStatusNot(identity.ciHash(), MemberStatus.WITHDRAWN)) {
			throw new DuplicateIdentityException(DUPLICATE_IDENTITY_MESSAGE);
		}

		if (memberRepository.existsByEmail(email)) {
			throw new DuplicateEmailException(MemberDuplicateExceptions.DUPLICATE_EMAIL_MESSAGE);
		}
		if (memberRepository.existsByNickname(nickname)) {
			throw new DuplicateNicknameException(MemberDuplicateExceptions.DUPLICATE_NICKNAME_MESSAGE);
		}

		String phoneHash = blindIndexGenerator.generatePhone(phone);
		if (memberRepository.existsByPhoneHash(phoneHash)) {
			throw new DuplicatePhoneException(MemberDuplicateExceptions.DUPLICATE_PHONE_MESSAGE);
		}

		Member newMember = new Member(
			email,
			password == null ? null : passwordEncoder.encode(password),
			name,
			nickname,
			phone,
			phoneHash,
			birthDate,
			gender
		);
		newMember.recordIdentityVerification(identity.ciHash(), identity.verifiedAt());
		Member savedMember;
		try {
			// 유니크 제약 위반을 여기서 잡으려고 바로 반영한다.
			// save만 하면 Facade의 트랜잭션이 커밋될 때 에러가 나서 이 메서드에서 잡을 수 없다
			savedMember = memberRepository.saveAndFlush(newMember);
		} catch (DataIntegrityViolationException exception) {
			// 같은 값으로 동시에 가입하면 둘 다 위의 중복 검사를 통과하고, 늦은 쪽이 DB 유니크 제약에 걸린다
			throw MemberDuplicateExceptions.from(exception);
		}

		// 지금 버전의 모든 약관에 대해 동의 여부를 한 줄씩 남긴다 (목록에 있으면 동의, 없으면 미동의)
		LocalDateTime decidedAt = LocalDateTime.now();
		termsAgreementRepository.saveAll(currentTerms.stream()
			.map(terms -> new TermsAgreement(savedMember, terms, agreedTerms.contains(terms.getType()), decidedAt))
			.toList());

		savedMember.publishJoinedEvent();
		return savedMember;
	}
}
