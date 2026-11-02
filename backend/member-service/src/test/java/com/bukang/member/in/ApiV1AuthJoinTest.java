package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.member.app.MemberFacade;
import com.bukang.member.domain.Member;
import com.bukang.member.domain.TermsAgreement;
import com.bukang.member.domain.TermsType;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.TermsRepository;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InMemoryIdentityVerificationStore.Config.class)
class ApiV1AuthJoinTest {
	@Autowired
	private InMemoryIdentityVerificationStore identityStore;

	private static final String EMAIL = "runner1@crewrun.com";
	private static final String PASSWORD = "password1234";
	private static final String NICKNAME = "러너1";
	private static final String PHONE = "010-1234-5678";
	private static final String DEFAULT_AGREED_TERMS = "\"agreedTerms\": [\"SERVICE\", \"PRIVACY\"]";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MemberFacade memberFacade;

	// 동시 가입을 재현하려고 중복 검사(existsBy...)만 바꿔 치고, 저장은 진짜 DB(H2)로 한다
	@MockitoSpyBean
	private MemberRepository memberRepository;

	@Autowired
	private TermsAgreementRepository termsAgreementRepository;

	@Autowired
	private TermsRepository termsRepository;

	@Autowired
	private TermsDataInit termsDataInit;

	@AfterEach
	void tearDown() {
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("가입하면 201과 회원 정보를 받는다")
	void joinSucceeds() throws Exception {
		join(EMAIL, NICKNAME, PHONE)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value(201))
			.andExpect(jsonPath("$.data.nickname").value(NICKNAME))
			.andExpect(jsonPath("$.data.email").value(EMAIL));
	}

	@ParameterizedTest
	@ValueSource(strings = {"러너", "가나다라마바사아", "abcd", "abcdefghijklmnop", "러너abc", "Runner2026"})
	@DisplayName("닉네임이 4~16칸(한글 2칸, 영문·숫자 1칸)이면 가입할 수 있다")
	void joinSucceedsWithValidNickname(String nickname) throws Exception {
		join(EMAIL, nickname, PHONE)
			.andExpect(status().isCreated());
	}

	@ParameterizedTest
	@ValueSource(strings = {"러", "abc", "가나다라마바사아자", "abcdefghijklmnopq"})
	@DisplayName("닉네임이 4칸보다 짧거나 16칸보다 길면 400이다")
	void joinFailsWithNicknameOutOfWidth(String nickname) throws Exception {
		join(EMAIL, nickname, PHONE)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("닉네임은 한글 2~8자, 영문 4~16자로 작성해야합니다."));
	}

	@ParameterizedTest
	@ValueSource(strings = {"러너 1", "러너!", "러너😀", "ㅋㅋㅋㅋ", "runner_1"})
	@DisplayName("닉네임에 공백·특수문자·이모지·자음/모음만 있는 글자가 있으면 400이다")
	void joinFailsWithNicknameInvalidCharacters(String nickname) throws Exception {
		join(EMAIL, nickname, PHONE)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("닉네임은 한글, 영문, 숫자만 쓸 수 있습니다."));
	}

	@Test
	@DisplayName("닉네임 'ㅋㅋㅋ'는 400이다")
	void joinFailsWithConsonantOnlyNickname() throws Exception {
		// 문자 규칙과 칸 수(3칸) 둘 다 어겨 어느 메시지가 먼저 나올지 정해져 있지 않으므로 상태만 확인한다
		join(EMAIL, "ㅋㅋㅋ", PHONE)
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("이메일이 255자면 가입할 수 있다")
	void joinSucceedsWithEmailOfMaxLength() throws Exception {
		join(emailOfLength(255), NICKNAME, PHONE)
			.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("이메일이 255자를 넘으면 400이다")
	void joinFailsWithTooLongEmail() throws Exception {
		join(emailOfLength(256), NICKNAME, PHONE)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value("이메일은 255자 이하로 작성해주세요."));
	}

	// 영문 72자, 한글 24자(3바이트씩), 섞어서 72바이트
	@ParameterizedTest
	@ValueSource(strings = {
		"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
		"가나다라마바사아자차카타파하가나다라마바사아자차",
		"가나다라마바사아자차카타파하가나다라마바사아자abc"
	})
	@DisplayName("비밀번호가 UTF-8로 72바이트면 가입할 수 있다")
	void joinSucceedsWithPasswordOf72Bytes(String password) throws Exception {
		assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(72);

		joinWith(EMAIL, password, "김러너", NICKNAME, PHONE)
			.andExpect(status().isCreated());
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
		"가나다라마바사아자차카타파하가나다라마바사아자차a",
		"가나다라마바사아자차카타파하가나다라마바사아자차카"
	})
	@DisplayName("비밀번호가 UTF-8로 72바이트를 넘으면 400이다")
	void joinFailsWithPasswordOver72Bytes(String password) throws Exception {
		assertThat(password.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(72);

		joinWith(EMAIL, password, "김러너", NICKNAME, PHONE)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value("비밀번호가 너무 깁니다."));
	}

	@Test
	@DisplayName("이미 있는 닉네임으로 가입하면 409다")
	void joinFailsWithExistingNickname() throws Exception {
		joinDirectly(EMAIL, NICKNAME, PHONE);

		join("runner2@crewrun.com", NICKNAME, "010-9999-8888")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 사용 중인 닉네임입니다."));
	}

	@Test
	@DisplayName("이미 있는 전화번호로 가입하면 409다")
	void joinFailsWithExistingPhone() throws Exception {
		joinDirectly(EMAIL, NICKNAME, PHONE);

		join("runner2@crewrun.com", "러너2", PHONE)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 사용 중인 전화번호입니다."));
	}

	@Test
	@DisplayName("이미 있는 이메일로 가입하면 409다")
	void joinFailsWithExistingEmail() throws Exception {
		joinDirectly(EMAIL, NICKNAME, PHONE);

		join(EMAIL, "러너2", "010-9999-8888")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."));
	}

	@Test
	@DisplayName("동시 가입으로 중복 검사를 통과해도 DB 닉네임 제약에 걸리면 409다")
	void joinRaceFailsOnNicknameConstraint() throws Exception {
		joinDirectly(EMAIL, NICKNAME, PHONE);
		skipDuplicateChecks();

		join("runner2@crewrun.com", NICKNAME, "010-9999-8888")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 사용 중인 닉네임입니다."));
	}

	@Test
	@DisplayName("동시 가입으로 중복 검사를 통과해도 DB 전화번호 제약에 걸리면 409다")
	void joinRaceFailsOnPhoneConstraint() throws Exception {
		joinDirectly(EMAIL, NICKNAME, PHONE);
		skipDuplicateChecks();

		join("runner2@crewrun.com", "러너2", PHONE)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 사용 중인 전화번호입니다."));
	}

	@Test
	@DisplayName("동시 가입으로 중복 검사를 통과해도 DB 이메일 제약에 걸리면 409다")
	void joinRaceFailsOnEmailConstraint() throws Exception {
		joinDirectly(EMAIL, NICKNAME, PHONE);
		skipDuplicateChecks();

		join(EMAIL, "러너2", "010-9999-8888")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."));
	}

	@Test
	@DisplayName("본인인증으로 확인한 이름 · 생년월일 · 휴대폰 번호로 가입하고, CI 해시와 인증 시각을 남긴다")
	void joinUsesVerifiedIdentity() throws Exception {
		String token = identityStore.verify("박인증", LocalDate.of(1990, 1, 2), "010-5555-6666", "ci-hash-park");

		joinWithToken(EMAIL, PASSWORD, NICKNAME, token, DEFAULT_AGREED_TERMS)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.name").value("박인증"))
			.andExpect(jsonPath("$.data.phone").value("010-5555-6666"))
			.andExpect(jsonPath("$.data.birthDate").value("1990-01-02"));

		Member member = memberRepository.findByEmail(EMAIL).orElseThrow();
		assertThat(member.getCiHash()).isEqualTo("ci-hash-park");
		assertThat(member.getVerifiedAt()).isNotNull();
		// 가입이 커밋되면 인증 결과를 지워 같은 토큰으로 두 번 가입할 수 없다
		assertThat(identityStore.findVerified(token)).isEmpty();
	}

	@Test
	@DisplayName("본인인증 토큰이 없으면 400 INVALID_REQUEST, 저장소에 없으면 400 AUTH_VERIFICATION_REQUIRED다")
	void joinFailsWithoutVerification() throws Exception {
		mockMvc.perform(post("/api/v1/auth/join")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "email": "%s", "password": "%s", "nickname": "%s", %s }
					""".formatted(EMAIL, PASSWORD, NICKNAME, DEFAULT_AGREED_TERMS)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value("본인인증을 해주세요."));

		joinWithToken(EMAIL, PASSWORD, NICKNAME, "crewrun-iv-expired", DEFAULT_AGREED_TERMS)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AUTH_VERIFICATION_REQUIRED"));
		assertThat(memberRepository.count()).isZero();
	}

	@Test
	@DisplayName("같은 사람(CI 해시)이 다른 이메일 · 번호로 다시 가입하면 409 MEMBER_DUPLICATE_IDENTITY다")
	void joinFailsWithSameIdentity() throws Exception {
		String first = identityStore.verify("김러너", LocalDate.of(1995, 4, 12), PHONE, "same-ci");
		joinWithToken(EMAIL, PASSWORD, NICKNAME, first, DEFAULT_AGREED_TERMS)
			.andExpect(status().isCreated());

		String second = identityStore.verify("김러너", LocalDate.of(1995, 4, 12), "010-9999-8888", "same-ci");
		joinWithToken("runner2@crewrun.com", PASSWORD, "러너2", second, DEFAULT_AGREED_TERMS)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MEMBER_DUPLICATE_IDENTITY"));
	}

	@Test
	@DisplayName("CI를 주지 않는 인증 방법(카카오)으로도 가입하고, 세 값 해시와 이름 + 생년월일 해시를 남긴다")
	void joinWithoutCi() throws Exception {
		String token = identityStore.verify("김카카오", LocalDate.of(1995, 4, 12), PHONE, null);

		joinWithToken(EMAIL, PASSWORD, NICKNAME, token, DEFAULT_AGREED_TERMS)
			.andExpect(status().isCreated());

		Member member = memberRepository.findByEmail(EMAIL).orElseThrow();
		assertThat(member.getCiHash()).isNull();
		assertThat(member.getIdentityHash()).hasSize(64);
		assertThat(member.getNameBirthHash()).hasSize(64);
	}

	@Test
	@DisplayName("CI가 없어도 이름 · 생년월일 · 휴대폰 번호가 같으면 두 번째 가입은 409 MEMBER_DUPLICATE_IDENTITY다")
	void joinFailsWithSameNameBirthPhone() throws Exception {
		String first = identityStore.verify("김카카오", LocalDate.of(1995, 4, 12), PHONE, null);
		joinWithToken(EMAIL, PASSWORD, NICKNAME, first, DEFAULT_AGREED_TERMS)
			.andExpect(status().isCreated());

		// 하이픈만 다른 같은 번호다
		String second = identityStore.verify("김카카오", LocalDate.of(1995, 4, 12), "01012345678", null);
		joinWithToken("runner2@crewrun.com", PASSWORD, "러너2", second, DEFAULT_AGREED_TERMS)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MEMBER_DUPLICATE_IDENTITY"));
	}

	@Test
	@DisplayName("동시 가입으로 1인 1계정 검사를 통과해도 DB 유니크 제약에 걸려 하나만 가입하고 409 MEMBER_DUPLICATE_IDENTITY다")
	void joinRaceFailsOnIdentityConstraint() throws Exception {
		String first = identityStore.verifyWithIdentityHash("김러너", LocalDate.of(1995, 4, 12), PHONE, "same-identity");
		joinWithToken(EMAIL, PASSWORD, NICKNAME, first, DEFAULT_AGREED_TERMS)
			.andExpect(status().isCreated());
		// 먼저 가입한 사람이 아직 없다고 답하게 해, 두 요청이 동시에 검사를 통과한 상황을 만든다
		willReturn(false).given(memberRepository).existsByIdentityHash(anyString());

		String second = identityStore.verifyWithIdentityHash("김러너", LocalDate.of(1995, 4, 12), "010-9999-8888",
			"same-identity");
		joinWithToken("runner2@crewrun.com", PASSWORD, "러너2", second, DEFAULT_AGREED_TERMS)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MEMBER_DUPLICATE_IDENTITY"));
		assertThat(memberRepository.count()).isOne();
	}

	@Test
	@DisplayName("탈퇴한 회원과 같은 사람(CI 해시)은 다시 가입할 수 있다")
	void joinAllowedAfterWithdrawal() throws Exception {
		String first = identityStore.verify("김러너", LocalDate.of(1995, 4, 12), PHONE, "same-ci");
		joinWithToken(EMAIL, PASSWORD, NICKNAME, first, DEFAULT_AGREED_TERMS)
			.andExpect(status().isCreated());
		Member withdrawn = memberRepository.findByEmail(EMAIL).orElseThrow();
		withdrawn.withdraw(LocalDateTime.now());
		memberRepository.saveAndFlush(withdrawn);

		String again = identityStore.verify("김러너", LocalDate.of(1995, 4, 12), PHONE, "same-ci");
		joinWithToken(EMAIL, PASSWORD, NICKNAME, again, DEFAULT_AGREED_TERMS)
			.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("필수 약관만 동의하면 201이고, 현재 약관 3개에 대해 동의 기록이 남으며 마케팅은 미동의다")
	void joinRecordsAgreementPerTerms() throws Exception {
		join(EMAIL, NICKNAME, PHONE, "\"agreedTerms\": [\"SERVICE\", \"PRIVACY\"]")
			.andExpect(status().isCreated());

		long memberId = memberRepository.findByEmail(EMAIL).orElseThrow().getId();
		Map<TermsType, Boolean> agreedByType = agreedByType(memberId);
		assertThat(agreedByType).containsOnlyKeys(TermsType.SERVICE, TermsType.PRIVACY, TermsType.MARKETING);
		assertThat(agreedByType).containsEntry(TermsType.SERVICE, true)
			.containsEntry(TermsType.PRIVACY, true)
			.containsEntry(TermsType.MARKETING, false);
	}

	@Test
	@DisplayName("마케팅까지 동의하면 마케팅 동의 기록이 동의로 남는다")
	void joinRecordsMarketingAgreed() throws Exception {
		join(EMAIL, NICKNAME, PHONE, "\"agreedTerms\": [\"SERVICE\", \"PRIVACY\", \"MARKETING\"]")
			.andExpect(status().isCreated());

		long memberId = memberRepository.findByEmail(EMAIL).orElseThrow().getId();
		assertThat(agreedByType(memberId)).containsEntry(TermsType.MARKETING, true);
	}

	@Test
	@DisplayName("필수 약관(개인정보)이 빠지면 400이고 회원도 동의 기록도 저장하지 않는다")
	void joinFailsWithoutRequiredTerms() throws Exception {
		join(EMAIL, NICKNAME, PHONE, "\"agreedTerms\": [\"SERVICE\", \"MARKETING\"]")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MEMBER_REQUIRED_TERMS_NOT_AGREED"))
			.andExpect(jsonPath("$.message").value("필수 약관에 동의해야 가입할 수 있습니다."));

		assertThat(memberRepository.existsByEmail(EMAIL)).isFalse();
		assertThat(termsAgreementRepository.count()).isZero();
	}

	@Test
	@DisplayName("약관 목록 칸이 없으면 400이다")
	void joinFailsWithoutAgreedTerms() throws Exception {
		join(EMAIL, NICKNAME, PHONE, "")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("모르는 약관 종류가 있으면 400 INVALID_REQUEST다")
	void joinFailsWithUnknownTermsType() throws Exception {
		join(EMAIL, NICKNAME, PHONE, "\"agreedTerms\": [\"SERVICE\", \"PRIVACY\", \"FOO\"]")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("시행 중인 필수 약관이 없으면 500이고 회원도 동의 기록도 저장하지 않는다")
	void joinFailsClosedWithoutCurrentTerms() throws Exception {
		termsRepository.deleteAll();
		try {
			join(EMAIL, NICKNAME, PHONE)
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

			assertThat(memberRepository.existsByEmail(EMAIL)).isFalse();
			assertThat(termsAgreementRepository.count()).isZero();
		} finally {
			// 다른 테스트가 쓰는 기본 약관을 되돌린다
			termsDataInit.makeBaseTerms();
		}
	}

	private Map<TermsType, Boolean> agreedByType(long memberId) {
		Map<TermsType, Boolean> agreedByType = new HashMap<>();
		for (TermsAgreement agreement : termsAgreementRepository.findByMemberId(memberId)) {
			agreedByType.put(agreement.getTerms().getType(), agreement.isAgreed());
		}
		return agreedByType;
	}

	// 먼저 가입한 회원이 아직 없다고 답하게 해, 두 요청이 동시에 중복 검사를 통과한 상황을 만든다
	private void skipDuplicateChecks() {
		willReturn(false).given(memberRepository).existsByEmail(anyString());
		willReturn(false).given(memberRepository).existsByNickname(anyString());
		willReturn(false).given(memberRepository).existsByPhoneHash(anyString());
	}

	private void joinDirectly(String email, String nickname, String phone) {
		memberFacade.join(identityStore.joinRequest(
			email, "password1234", "김러너", nickname, phone, LocalDate.of(1995, 4, 12), null,
			List.of(TermsType.SERVICE, TermsType.PRIVACY)
		));
	}

	// joinDirectly(김러너)와 다른 사람으로 가입한다. 같은 사람이면 이메일 · 번호 중복보다 1인 1계정 검사에 먼저 걸린다
	private ResultActions join(String email, String nickname, String phone) throws Exception {
		return joinWith(email, PASSWORD, "박러너", nickname, phone);
	}

	// agreedTermsField는 JSON 칸 하나(예: "agreedTerms": [...])이고, 빈 문자열이면 칸을 아예 보내지 않는다
	private ResultActions join(String email, String nickname, String phone, String agreedTermsField)
		throws Exception {
		return joinWith(email, PASSWORD, "박러너", nickname, phone, agreedTermsField);
	}

	// 총 길이가 length인 이메일 (로컬 파트 64자, 도메인 라벨 63자 이하라 형식 검사를 통과한다)
	private String emailOfLength(int length) {
		int lastLabelLength = length - 65 - 128;
		return "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(lastLabelLength);
	}

	private ResultActions joinWith(String email, String password, String name, String nickname, String phone)
		throws Exception {
		return joinWith(email, password, name, nickname, phone, DEFAULT_AGREED_TERMS);
	}

	// 이름 · 휴대폰 번호 · 생년월일은 본인인증 결과로 넣는다. CI 해시는 매번 달라 같은 사람 검사에는 걸리지 않는다
	private ResultActions joinWith(String email, String password, String name, String nickname, String phone,
		String agreedTermsField) throws Exception {
		String token = identityStore.verify(name, LocalDate.of(1995, 4, 12), phone, "ci-" + UUID.randomUUID());
		return joinWithToken(email, password, nickname, token, agreedTermsField);
	}

	private ResultActions joinWithToken(String email, String password, String nickname, String token,
		String agreedTermsField) throws Exception {
		String extraField = agreedTermsField.isEmpty() ? "" : ", " + agreedTermsField;
		return mockMvc.perform(post("/api/v1/auth/join")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
					"email": "%s",
					"password": "%s",
					"nickname": "%s",
					"verificationToken": "%s"%s
				}
				""".formatted(email, password, nickname, token, extraField)));
	}
}
