package com.bukang.member.app;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.bukang.member.domain.TermsType;
import com.bukang.member.dto.TestMemberDto;
import com.bukang.member.out.identity.VerifiedIdentity;

import lombok.RequiredArgsConstructor;

/**
 * QA용 테스트 회원을 본인인증 없이 만든다. 관리자만 부르고, 설정(member.auth.test-member.enabled)이 켜진 서버에만 주소가 있다
 * 가입은 MemberJoinUseCase를 그대로 써서 연락처 암호화 · 약관 동의 · 가입 이벤트가 실제 가입과 같다 (MemberDataInit과 같은 방식)
 * 이메일은 qa-buyer-{번호}@crewrun.test이고, 이미 있는 번호는 건너뛰어 다음 번호로 만든다
 * 비밀번호는 dev 시작 데이터(MemberDataInit)와 같은 알려진 값이다. 실제 사용자가 있는 서버에서는 켜지 않는다
 */
@Service
@RequiredArgsConstructor
public class TestMemberUseCase {
	static final String PASSWORD = "12345678910";
	// 빈 번호를 찾아 올라가는 한도. 넘으면 만든 만큼만 돌려준다
	static final int MAX_NUMBER = 999;
	private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 1, 1);
	private static final List<TermsType> REQUIRED_TERMS =
		List.of(TermsType.SERVICE, TermsType.PRIVACY, TermsType.AGE_OVER_14);

	private final AdminVerifyUseCase adminVerifyUseCase;
	private final MemberFacade memberFacade;
	private final MemberJoinUseCase memberJoinUseCase;

	public List<TestMemberDto> create(long adminMemberId, int count) {
		adminVerifyUseCase.verify(adminMemberId);
		List<TestMemberDto> created = new ArrayList<>();
		for (int number = 1; number <= MAX_NUMBER && created.size() < count; number++) {
			String email = "qa-buyer-%d@crewrun.test".formatted(number);
			if (!memberFacade.checkEmailAvailability(email).available()) {
				continue;
			}
			String nickname = "QA구매자%d".formatted(number);
			// 해시 · 인증 시각이 비어 있어 1인 1계정 검사에 걸리지 않는다
			VerifiedIdentity identity = new VerifiedIdentity(nickname, BIRTH_DATE,
				"010-0000-%04d".formatted(number), null, null, null, null);
			created.add(TestMemberDto.from(
				memberJoinUseCase.join(email, PASSWORD, nickname, null, REQUIRED_TERMS, identity)));
		}
		return created;
	}
}
