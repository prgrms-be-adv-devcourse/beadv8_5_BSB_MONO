package com.bukang.member.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.bukang.member.domain.Member;
import com.bukang.member.domain.exception.MemberNotFoundException;
import com.bukang.member.dto.MemberLoginRequestDto;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.RefreshTokenRepository;
import com.bukang.member.out.RefreshTokenRepository.NewFamily;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.support.MemberFixture;

// 로그인은 bcrypt 비교가 느려서 DB 커넥션을 잡은 채 기다리지 않게 트랜잭션 밖에서 실행한다 (BUK-184)
@SpringBootTest
@ActiveProfiles("test")
@Import(InMemoryIdentityVerificationStore.Config.class)
class MemberLoginTransactionTest {
	private static final String EMAIL = "runner1@crewrun.com";
	private static final String PASSWORD = "password1234";

	@Autowired
	private InMemoryIdentityVerificationStore identityStore;

	@Autowired
	private MemberFacade memberFacade;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private TermsAgreementRepository termsAgreementRepository;

	@MockitoSpyBean
	private AuthenticationManager authenticationManager;

	@MockitoBean
	private RefreshTokenRepository refreshTokenRepository;

	private long memberId;

	@BeforeEach
	void setUp() {
		memberId = memberFacade.join(MemberFixture.joinRequest()
			.email(EMAIL).password(PASSWORD)
			.build(identityStore)).getId();
	}

	@AfterEach
	void tearDown() {
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("비밀번호를 비교하는 동안과 토큰을 발급하는 동안 활성 트랜잭션이 없다")
	void noTransactionWhileComparingPassword() {
		AtomicBoolean transactionActive = new AtomicBoolean(true);
		doAnswer(invocation -> {
			transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
			return invocation.callRealMethod();
		}).when(authenticationManager).authenticate(any());
		AtomicBoolean issuingInTransaction = new AtomicBoolean(true);
		doAnswer(invocation -> {
			issuingInTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
			return null;
		}).when(refreshTokenRepository).create(anyLong(), any(NewFamily.class));

		memberFacade.login(new MemberLoginRequestDto(EMAIL, PASSWORD));

		assertThat(transactionActive).isFalse();
		assertThat(issuingInTransaction).isFalse();
	}

	@Test
	@DisplayName("비밀번호를 비교하는 사이 탈퇴한 회원에게는 토큰을 주지 않는다")
	void noTokenWhenWithdrawnDuringComparison() {
		doAnswer(invocation -> {
			Object result = invocation.callRealMethod();
			Member member = memberRepository.findById(memberId).orElseThrow();
			member.withdraw(LocalDateTime.now());
			memberRepository.saveAndFlush(member);
			return result;
		}).when(authenticationManager).authenticate(any());

		assertThatThrownBy(() -> memberFacade.login(new MemberLoginRequestDto(EMAIL, PASSWORD)))
			.isInstanceOf(MemberNotFoundException.class);
		verify(refreshTokenRepository, never()).create(anyLong(), any(NewFamily.class));
	}
}
