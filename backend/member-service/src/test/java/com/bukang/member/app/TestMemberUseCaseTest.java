package com.bukang.member.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.bukang.member.domain.Member;
import com.bukang.member.domain.exception.MemberNotAdminException;
import com.bukang.member.dto.EmailAvailabilityResponseDto;
import com.bukang.member.dto.TestMemberDto;
import com.bukang.member.out.identity.VerifiedIdentity;

// 테스트 회원은 관리자만 만들고, 이미 있는 번호는 건너뛴다
class TestMemberUseCaseTest {
	private static final long ADMIN = 1L;

	private final AdminVerifyUseCase adminVerifyUseCase = mock(AdminVerifyUseCase.class);
	private final MemberFacade memberFacade = mock(MemberFacade.class);
	private final MemberJoinUseCase memberJoinUseCase = mock(MemberJoinUseCase.class);
	private final TestMemberUseCase useCase =
		new TestMemberUseCase(adminVerifyUseCase, memberFacade, memberJoinUseCase);

	@Test
	@DisplayName("이미 있는 번호는 건너뛰고 다음 번호로 요청한 수만큼 만든다")
	void skipsTakenNumbers() {
		when(memberFacade.checkEmailAvailability(anyString())).thenReturn(new EmailAvailabilityResponseDto(true));
		when(memberFacade.checkEmailAvailability("qa-buyer-1@crewrun.test"))
			.thenReturn(new EmailAvailabilityResponseDto(false));
		when(memberJoinUseCase.join(anyString(), anyString(), anyString(), isNull(), any(), any()))
			.thenAnswer(call -> member(call.getArgument(0), call.getArgument(2)));

		List<TestMemberDto> created = useCase.create(ADMIN, 2);

		assertThat(created).extracting(TestMemberDto::email, TestMemberDto::nickname).containsExactly(
			tuple("qa-buyer-2@crewrun.test", "QA구매자2"), tuple("qa-buyer-3@crewrun.test", "QA구매자3"));
		// 연락처는 번호마다 다르게 넣는다
		ArgumentCaptor<VerifiedIdentity> identity = ArgumentCaptor.forClass(VerifiedIdentity.class);
		verify(memberJoinUseCase).join(eq("qa-buyer-2@crewrun.test"), eq(TestMemberUseCase.PASSWORD), eq("QA구매자2"),
			isNull(), any(), identity.capture());
		assertThat(identity.getValue().phone()).isEqualTo("010-0000-0002");
	}

	@Test
	@DisplayName("관리자가 아니면 아무도 만들지 않는다")
	void adminOnly() {
		when(adminVerifyUseCase.verify(ADMIN)).thenThrow(new MemberNotAdminException("관리자만 이용할 수 있습니다."));

		assertThatThrownBy(() -> useCase.create(ADMIN, 1)).isInstanceOf(MemberNotAdminException.class);
		verify(memberJoinUseCase, never()).join(any(), any(), any(), any(), any(), any());
	}

	private static Member member(String email, String nickname) {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(100L);
		when(member.getEmail()).thenReturn(email);
		when(member.getNickname()).thenReturn(nickname);
		return member;
	}
}
