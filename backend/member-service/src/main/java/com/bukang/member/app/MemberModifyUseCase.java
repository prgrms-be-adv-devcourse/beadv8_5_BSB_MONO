package com.bukang.member.app;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.bukang.member.domain.Member;
import com.bukang.member.domain.exception.DuplicateNicknameException;
import com.bukang.member.domain.exception.MemberNotFoundException;
import com.bukang.member.out.MemberRepository;

import lombok.RequiredArgsConstructor;

// 내 정보 수정: 닉네임만 바꾼다 (BE-012, BUK-33)
// 이름 · 생년월일 · 휴대폰 번호는 본인인증으로 받은 값이라 바꿀 수 없다. 휴대폰 번호 재인증은 따로 만든다
// 닉네임이 바뀌어 저장했을 때만 수정 이벤트를 발행하고, 커밋 뒤 Kafka로 나간다 (BE-015)
@Service
@RequiredArgsConstructor
public class MemberModifyUseCase {
	private final MemberRepository memberRepository;

	public Member modify(long memberId, String nickname) {
		// 탈퇴한 회원은 없는 회원으로 본다 (BE-022)
		Member member = memberRepository.findActiveById(memberId)
			.orElseThrow(() -> new MemberNotFoundException("존재하지 않는 회원입니다."));

		// 내 행을 빼고 중복을 찾는다. 내 값을 그대로 보내거나 대소문자만 바꿔도
		// (MySQL 기본 정렬은 대소문자를 무시해 내 행이 걸린다) "이미 사용 중"이 뜨지 않게 하기 위해서다
		if (memberRepository.existsByNicknameAndIdNot(nickname, memberId)) {
			throw new DuplicateNicknameException(MemberDuplicateExceptions.DUPLICATE_NICKNAME_MESSAGE);
		}

		// 닉네임이 그대로면 저장하지 않는다 (수정 시각도 그대로)
		if (!member.changeNickname(nickname)) {
			return member;
		}
		Member saved;
		try {
			// 유니크 제약 위반을 여기서 잡으려고 바로 반영한다 (가입과 같은 이유)
			saved = memberRepository.saveAndFlush(member);
		} catch (DataIntegrityViolationException exception) {
			// 같은 닉네임으로 동시에 바꾸면 둘 다 위의 중복 검사를 통과하고, 늦은 쪽이 DB 유니크 제약에 걸린다
			throw MemberDuplicateExceptions.from(exception);
		}
		// flush로 수정 시각이 채워진 뒤에 발행해야 이벤트에 새 수정 시각이 담긴다
		saved.publishModifiedEvent();
		return saved;
	}
}
