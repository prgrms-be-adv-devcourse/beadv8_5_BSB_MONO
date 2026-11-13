package com.bukang.member.app;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.common.global.crypto.BlindIndexGenerator;
import com.bukang.member.domain.Member;
import com.bukang.member.domain.exception.DuplicatePhoneException;
import com.bukang.member.domain.exception.IdentityMismatchException;
import com.bukang.member.domain.exception.MemberNotFoundException;
import com.bukang.member.dto.MemberDto;
import com.bukang.member.dto.VerifiedIdentityDto;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.identity.VerifiedIdentity;

import lombok.RequiredArgsConstructor;

/**
 * 휴대폰 번호 바꾸기 (BUK-71, BE-037). 로그인한 회원이 본인인증을 다시 해야 바뀐다
 * 인증 확인 주소는 로그인 없이도 부를 수 있어 결과가 어느 회원 것인지 묶여 있지 않다
 * 그래서 확인할 때와 바꿀 때 모두 인증한 사람이 로그인한 회원 본인인지 본다
 * 회원 사본에는 닉네임만 있어 수정 이벤트는 보내지 않는다
 */
@Service
@RequiredArgsConstructor
public class MemberPhoneChangeUseCase {
	static final String MISMATCH_MESSAGE = "가입한 본인의 인증이 아닙니다.";

	private final MemberRepository memberRepository;
	private final IdentityVerificationUseCase identityVerificationUseCase;
	private final BlindIndexGenerator blindIndexGenerator;

	// 회원 전용 확인: 가입 확인과 달리 「이미 가입한 사람」으로 막지 않고, 로그인한 회원 본인인지만 본다
	public VerifiedIdentityDto complete(long memberId, String identityVerificationId) {
		Member member = findMember(memberId);
		return identityVerificationUseCase.complete(identityVerificationId,
			identity -> checkSamePerson(member, identity));
	}

	@Transactional
	public MemberDto change(long memberId, String verificationToken) {
		// 회원 행을 잠가 같은 회원의 닉네임 수정·탈퇴와 겹쳐도 늦은 쪽이 먼저 바뀐 값을 옛 값으로 덮지 않게 한다
		// 잠금이 트랜잭션의 첫 쿼리여야 MySQL 기본 격리 수준에서 앞 요청이 바꾼 값을 본다
		Member member = memberRepository.findByIdForUpdate(memberId)
			.filter(found -> !found.isWithdrawn())
			.orElseThrow(() -> new MemberNotFoundException("존재하지 않는 회원입니다."));
		// 쓴 인증 결과는 커밋 뒤 지워진다. 실패하면 남아 있어 같은 인증으로 다시 시도할 수 있다
		VerifiedIdentity identity = identityVerificationUseCase.takeForJoin(verificationToken);
		checkSamePerson(member, identity);

		// 하이픈 유무와 상관없이 같은 번호면 번호는 그대로 두고 본인인증 해시만 채운다 (BUK-193)
		// 해시가 비어 있는 기존 회원이 판매자 신청 안내대로 같은 번호로 다시 인증하는 경우다
		String phoneHash = blindIndexGenerator.generatePhone(identity.phone());
		if (phoneHash.equals(member.getPhoneHash())) {
			member.refreshIdentityVerification(identity.ciHash(), identity.identityHash(), identity.nameBirthHash(),
				identity.verifiedAt());
		} else {
			if (memberRepository.existsByPhoneHashAndIdNot(phoneHash, memberId)) {
				throw new DuplicatePhoneException(MemberDuplicateExceptions.DUPLICATE_PHONE_MESSAGE);
			}
			member.changePhone(identity.phone(), phoneHash, identity.ciHash(), identity.identityHash(),
				identity.nameBirthHash(), identity.verifiedAt());
		}
		try {
			// 같은 번호로 동시에 바꾸면 둘 다 위의 검사를 통과하고, 늦은 쪽이 DB 유니크 제약에 걸린다
			// 이름 + 생년월일 + 번호 해시의 유니크 제약에 걸려도 같은 방식으로 409가 된다
			return memberRepository.saveAndFlush(member).toDto();
		} catch (DataIntegrityViolationException exception) {
			throw MemberDuplicateExceptions.from(exception);
		}
	}

	private Member findMember(long memberId) {
		// 탈퇴한 회원은 없는 회원으로 본다 (BE-022)
		return memberRepository.findActiveById(memberId)
			.orElseThrow(() -> new MemberNotFoundException("존재하지 않는 회원입니다."));
	}

	private static void checkSamePerson(Member member, VerifiedIdentity identity) {
		if (!member.isSamePerson(identity.name(), identity.birthDate(), identity.ciHash())) {
			throw new IdentityMismatchException(MISMATCH_MESSAGE);
		}
	}
}
