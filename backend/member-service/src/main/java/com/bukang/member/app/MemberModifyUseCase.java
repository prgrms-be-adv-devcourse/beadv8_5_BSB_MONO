package com.bukang.member.app;

import java.time.LocalDate;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.bukang.common.global.crypto.BlindIndexGenerator;
import com.bukang.member.domain.Member;
import com.bukang.member.domain.exception.DuplicateNicknameException;
import com.bukang.member.domain.exception.DuplicatePhoneException;
import com.bukang.member.domain.exception.MemberNotFoundException;
import com.bukang.member.domain.exception.UnderAgeException;
import com.bukang.member.out.MemberRepository;

import lombok.RequiredArgsConstructor;

// 내 정보(이름·생년월일·휴대폰·닉네임) 수정 (BE-012)
// 4칸 중 하나라도 바뀌어 저장했을 때만 수정 이벤트를 발행하고, 커밋 뒤 Kafka로 나간다 (BE-015)
@Service
@RequiredArgsConstructor
public class MemberModifyUseCase {
	private final MemberRepository memberRepository;
	private final BlindIndexGenerator blindIndexGenerator;

	public Member modify(long memberId, String name, String nickname, String phone, LocalDate birthDate) {
		// 탈퇴한 회원은 없는 회원으로 본다 (BE-022)
		Member member = memberRepository.findActiveById(memberId)
			.orElseThrow(() -> new MemberNotFoundException("존재하지 않는 회원입니다."));

		// 가입 뒤 생년월일을 바꿔 나이 제한을 우회하지 못하게 수정에서도 검사한다
		if (!Member.isJoinableAge(birthDate, LocalDate.now())) {
			throw new UnderAgeException("만 14세 미만의 생년월일로는 수정할 수 없습니다.");
		}

		// 내 행을 빼고 중복을 찾는다. 내 값을 그대로 보내거나 대소문자만 바꿔도
		// (MySQL 기본 정렬은 대소문자를 무시해 내 행이 걸린다) "이미 사용 중"이 뜨지 않게 하기 위해서다
		// 휴대폰은 해시로 찾으므로 하이픈 유무만 다르면 같은 번호로 본다
		if (memberRepository.existsByNicknameAndIdNot(nickname, memberId)) {
			throw new DuplicateNicknameException(MemberDuplicateExceptions.DUPLICATE_NICKNAME_MESSAGE);
		}
		String phoneHash = blindIndexGenerator.generatePhone(phone);
		if (memberRepository.existsByPhoneHashAndIdNot(phoneHash, memberId)) {
			throw new DuplicatePhoneException(MemberDuplicateExceptions.DUPLICATE_PHONE_MESSAGE);
		}

		// 4칸이 전부 그대로면 저장하지 않는다 (수정 시각도 그대로)
		if (!member.modify(name, nickname, phone, phoneHash, birthDate)) {
			return member;
		}
		Member saved;
		try {
			// 유니크 제약 위반을 여기서 잡으려고 바로 반영한다 (가입과 같은 이유)
			saved = memberRepository.saveAndFlush(member);
		} catch (DataIntegrityViolationException exception) {
			// 같은 닉네임·휴대폰으로 동시에 바꾸면 둘 다 위의 중복 검사를 통과하고, 늦은 쪽이 DB 유니크 제약에 걸린다
			throw MemberDuplicateExceptions.from(exception);
		}
		// flush로 수정 시각이 채워진 뒤에 발행해야 이벤트에 새 수정 시각이 담긴다
		saved.publishModifiedEvent();
		return saved;
	}
}
