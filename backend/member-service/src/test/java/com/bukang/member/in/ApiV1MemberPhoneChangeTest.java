package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.common.global.crypto.BlindIndexGenerator;
import com.bukang.common.shared.member.event.MemberModifiedEvent;
import com.bukang.common.shared.member.web.MemberIdHeader;
import com.bukang.member.app.IdentityHasher;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.domain.Member;
import com.bukang.member.domain.TermsType;
import com.bukang.member.dto.MemberJoinRequestDto;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.out.identity.PortOneIdentityClient;
import com.bukang.member.out.identity.PortOneIdentityVerification;
import com.bukang.member.out.identity.VerifiedIdentity;
import com.bukang.member.support.MemberFixture;
import com.jayway.jsonpath.JsonPath;

// 휴대폰 번호 바꾸기 (BUK-71, BE-037). 로그인한 회원이 본인인증을 다시 해야 바뀌고, 인증한 사람이 회원 본인이어야 한다
// 게이트웨이가 넣어 주는 X-Member-Id 헤더를 테스트에서는 직접 넣는다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
@Import(InMemoryIdentityVerificationStore.Config.class)
class ApiV1MemberPhoneChangeTest {
	private static final String PHONE_PATH = "/api/v1/member/members/me/phone";
	private static final String COMPLETE_PATH = "/api/v1/member/members/me/phone-verifications/%s/complete";
	private static final String START_PATH = "/api/v1/auth/identity-verifications";
	private static final String NAME = "김러너";
	private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 4, 12);
	private static final String PHONE = "010-1234-5678";
	private static final String NEW_PHONE = "010-5555-6666";
	private static final String CI = "real-ci-value";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private InMemoryIdentityVerificationStore identityStore;

	@Autowired
	private IdentityHasher hasher;

	@Autowired
	private BlindIndexGenerator blindIndexGenerator;

	@Autowired
	private MemberFacade memberFacade;

	// 동시 변경을 재현하려고 중복 검사만 바꿔 치고, 저장은 진짜 DB(H2)로 한다
	@MockitoSpyBean
	private MemberRepository memberRepository;

	@Autowired
	private TermsAgreementRepository termsAgreementRepository;

	@Autowired
	private ApplicationEvents applicationEvents;

	// 테스트는 포트원을 부르지 않는다
	@MockitoBean
	private PortOneIdentityClient portOneIdentityClient;

	private long memberId;

	@BeforeEach
	void setUp() {
		memberId = join(NAME, BIRTH_DATE, PHONE, hasher.ci(CI));
	}

	@AfterEach
	void tearDown() {
		identityStore.clear();
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("본인이 다시 인증하면 휴대폰 번호와 본인인증 해시가 새 번호로 바뀌고 200과 바뀐 회원 정보를 받는다")
	void changeSucceeds() throws Exception {
		String token = identityStore.verify(NAME, BIRTH_DATE, NEW_PHONE, hasher.ci(CI));
		VerifiedIdentity identity = identityStore.findVerified(token).orElseThrow();

		change(memberId, token)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(memberId))
			.andExpect(jsonPath("$.data.phone").value(NEW_PHONE))
			.andExpect(jsonPath("$.data.name").value(NAME));

		Member member = memberRepository.findById(memberId).orElseThrow();
		assertThat(member.getPhone()).isEqualTo(NEW_PHONE);
		assertThat(member.getPhoneHash()).isEqualTo(blindIndexGenerator.generatePhone(NEW_PHONE));
		assertThat(member.getIdentityHash()).isEqualTo(identity.identityHash());
		assertThat(member.getNameBirthHash()).isEqualTo(identity.nameBirthHash());
		assertThat(member.getCiHash()).isEqualTo(hasher.ci(CI));
	}

	@Test
	@DisplayName("번호를 바꾸면 예전 번호는 비어서 다른 사람이 그 번호로 가입할 수 있다")
	void oldPhoneIsReleased() throws Exception {
		change(memberId, identityStore.verify(NAME, BIRTH_DATE, NEW_PHONE, hasher.ci(CI))).andExpect(status().isOk());

		assertThat(memberRepository.existsByPhoneHash(blindIndexGenerator.generatePhone(PHONE))).isFalse();
		join("박러너", LocalDate.of(1990, 1, 1), PHONE, null);
	}

	@Test
	@DisplayName("쓴 인증 결과는 지워져 같은 토큰으로 다시 바꿀 수 없다 (400 AUTH_VERIFICATION_REQUIRED)")
	void tokenIsConsumed() throws Exception {
		String token = identityStore.verify(NAME, BIRTH_DATE, NEW_PHONE, hasher.ci(CI));

		change(memberId, token).andExpect(status().isOk());

		assertThat(identityStore.findVerified(token)).isEmpty();
		change(memberId, token)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AUTH_VERIFICATION_REQUIRED"));
	}

	@Test
	@DisplayName("서버에 없는 토큰이면 400 AUTH_VERIFICATION_REQUIRED다")
	void unknownToken() throws Exception {
		change(memberId, "crewrun-iv-made-elsewhere")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AUTH_VERIFICATION_REQUIRED"));
	}

	@Test
	@DisplayName("토큰을 빼고 보내면 400이다")
	void blankToken() throws Exception {
		mockMvc.perform(put(PHONE_PATH)
				.header(MemberIdHeader.NAME, memberId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("이름이 다른 사람의 인증으로는 바꿀 수 없다 (403 MEMBER_IDENTITY_MISMATCH, 번호 그대로)")
	void otherNameIsRejected() throws Exception {
		String token = identityStore.verify("박러너", BIRTH_DATE, NEW_PHONE, null);

		change(memberId, token)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MEMBER_IDENTITY_MISMATCH"));

		assertThat(memberRepository.findById(memberId).orElseThrow().getPhone()).isEqualTo(PHONE);
	}

	@Test
	@DisplayName("생년월일이 다른 사람의 인증으로는 바꿀 수 없다 (403)")
	void otherBirthDateIsRejected() throws Exception {
		String token = identityStore.verify(NAME, LocalDate.of(1995, 4, 13), NEW_PHONE, null);

		change(memberId, token)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MEMBER_IDENTITY_MISMATCH"));
	}

	@Test
	@DisplayName("회원과 인증 결과 둘 다 CI가 있는데 다르면, 이름 · 생년월일이 같아도 바꿀 수 없다 (403)")
	void otherCiIsRejected() throws Exception {
		String token = identityStore.verify(NAME, BIRTH_DATE, NEW_PHONE, hasher.ci("other-ci"));

		change(memberId, token)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MEMBER_IDENTITY_MISMATCH"));
	}

	@Test
	@DisplayName("인증 결과에 CI가 없으면(카카오) 이름 · 생년월일로만 보고, 회원의 CI 해시는 그대로 둔다")
	void verificationWithoutCi() throws Exception {
		change(memberId, identityStore.verify(NAME, BIRTH_DATE, NEW_PHONE, null)).andExpect(status().isOk());

		assertThat(memberRepository.findById(memberId).orElseThrow().getCiHash()).isEqualTo(hasher.ci(CI));
	}

	@Test
	@DisplayName("CI 없이 가입한 회원이 CI를 주는 인증으로 바꾸면 이번 CI 해시를 채운다")
	void fillsMissingCi() throws Exception {
		long kakaoMemberId = join("이러너", LocalDate.of(1992, 3, 3), "010-2222-3333", null);

		change(kakaoMemberId, identityStore.verify("이러너", LocalDate.of(1992, 3, 3), NEW_PHONE, hasher.ci("lee-ci")))
			.andExpect(status().isOk());

		assertThat(memberRepository.findById(kakaoMemberId).orElseThrow().getCiHash()).isEqualTo(hasher.ci("lee-ci"));
	}

	@Test
	@DisplayName("인증 이름 앞뒤 공백은 무시하고 같은 사람으로 본다")
	void nameWhitespaceIgnored() throws Exception {
		change(memberId, identityStore.verify(" 김러너 ", BIRTH_DATE, NEW_PHONE, hasher.ci(CI)))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("새 번호를 다른 회원이 쓰면 409 MEMBER_DUPLICATE_PHONE이고, 번호와 인증 결과는 그대로 남는다")
	void duplicatePhone() throws Exception {
		join("박러너", LocalDate.of(1990, 1, 1), NEW_PHONE, null);
		String token = identityStore.verify(NAME, BIRTH_DATE, NEW_PHONE, hasher.ci(CI));

		change(memberId, token)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MEMBER_DUPLICATE_PHONE"));

		assertThat(memberRepository.findById(memberId).orElseThrow().getPhone()).isEqualTo(PHONE);
		assertThat(identityStore.findVerified(token)).isPresent();
	}

	@Test
	@DisplayName("동시에 같은 번호로 바꿔 중복 검사를 함께 통과해도, 늦은 쪽은 DB 유니크 제약에 걸려 409다")
	void concurrentDuplicatePhone() throws Exception {
		join("박러너", LocalDate.of(1990, 1, 1), NEW_PHONE, null);
		willReturn(false).given(memberRepository).existsByPhoneHashAndIdNot(anyString(), anyLong());

		change(memberId, identityStore.verify(NAME, BIRTH_DATE, NEW_PHONE, hasher.ci(CI)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MEMBER_DUPLICATE_PHONE"));
	}

	@Test
	@DisplayName("해시가 비어 있는 회원이 지금 번호로 다시 인증하면 번호는 그대로 두고 본인인증 해시와 인증 시각을 채운다 (BUK-193)")
	void samePhoneFillsMissingHashes() throws Exception {
		clearIdentityHashes(memberId);
		String token = identityStore.verify(NAME, BIRTH_DATE, "01012345678", hasher.ci(CI));
		VerifiedIdentity identity = identityStore.findVerified(token).orElseThrow();

		change(memberId, token)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(memberId))
			.andExpect(jsonPath("$.data.phone").value(PHONE));

		Member member = memberRepository.findById(memberId).orElseThrow();
		assertThat(member.getPhone()).isEqualTo(PHONE);
		assertThat(member.getPhoneHash()).isEqualTo(blindIndexGenerator.generatePhone(PHONE));
		assertThat(member.getCiHash()).isEqualTo(hasher.ci(CI));
		assertThat(member.getIdentityHash()).isEqualTo(identity.identityHash());
		assertThat(member.getNameBirthHash()).isEqualTo(identity.nameBirthHash());
		assertThat(member.getVerifiedAt()).isEqualTo(identity.verifiedAt().truncatedTo(ChronoUnit.MICROS));
		assertThat(applicationEvents.stream(MemberModifiedEvent.class)).isEmpty();
		assertThat(identityStore.findVerified(token)).isEmpty();
	}

	@Test
	@DisplayName("해시가 비어 있어도 다른 사람이 지금 번호로 인증하면 403이고 해시는 비어 있는 채로 남는다 (BUK-193)")
	void samePhoneOtherPersonIsRejected() throws Exception {
		clearIdentityHashes(memberId);

		change(memberId, identityStore.verify("박러너", BIRTH_DATE, PHONE, null))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MEMBER_IDENTITY_MISMATCH"));

		Member member = memberRepository.findById(memberId).orElseThrow();
		assertThat(member.getIdentityHash()).isNull();
		assertThat(member.getNameBirthHash()).isNull();
		assertThat(member.getCiHash()).isNull();
	}

	@Test
	@DisplayName("해시가 이미 있는 회원이 지금 번호로 다시 인증하면 같은 값으로 덮이고 번호 표기도 그대로다")
	void samePhoneKeepsExistingHashes() throws Exception {
		Member before = memberRepository.findById(memberId).orElseThrow();
		String token = identityStore.verify(NAME, BIRTH_DATE, "01012345678", hasher.ci(CI));
		VerifiedIdentity identity = identityStore.findVerified(token).orElseThrow();

		change(memberId, token)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.phone").value(PHONE));

		Member after = memberRepository.findById(memberId).orElseThrow();
		assertThat(after.getPhone()).isEqualTo(PHONE);
		assertThat(after.getPhoneHash()).isEqualTo(before.getPhoneHash());
		assertThat(after.getCiHash()).isEqualTo(before.getCiHash());
		assertThat(after.getIdentityHash()).isEqualTo(before.getIdentityHash());
		assertThat(after.getNameBirthHash()).isEqualTo(before.getNameBirthHash());
		assertThat(after.getVerifiedAt()).isEqualTo(identity.verifiedAt().truncatedTo(ChronoUnit.MICROS));
		assertThat(identityStore.findVerified(token)).isEmpty();
	}

	@Test
	@DisplayName("CI가 있는 회원이 CI 없는 인증(카카오)으로 지금 번호를 다시 인증해도 CI 해시는 그대로다")
	void samePhoneWithoutCiKeepsCi() throws Exception {
		change(memberId, identityStore.verify(NAME, BIRTH_DATE, PHONE, null)).andExpect(status().isOk());

		assertThat(memberRepository.findById(memberId).orElseThrow().getCiHash()).isEqualTo(hasher.ci(CI));
	}

	@Test
	@DisplayName("번호를 바꿔도 회원 사본용 수정 이벤트는 보내지 않는다 (사본에는 닉네임만 있다)")
	void noModifiedEvent() throws Exception {
		change(memberId, identityStore.verify(NAME, BIRTH_DATE, NEW_PHONE, hasher.ci(CI))).andExpect(status().isOk());

		assertThat(applicationEvents.stream(MemberModifiedEvent.class)).isEmpty();
	}

	@Test
	@DisplayName("X-Member-Id 헤더가 없으면 401이다")
	void loginRequired() throws Exception {
		mockMvc.perform(put(PHONE_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"verificationToken\":\"crewrun-iv-x\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("없는 회원이면 404다")
	void memberNotFound() throws Exception {
		change(999_999L, identityStore.verify(NAME, BIRTH_DATE, NEW_PHONE, hasher.ci(CI)))
			.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("탈퇴한 회원이면 404다")
	void withdrawnMember() throws Exception {
		Member member = memberRepository.findById(memberId).orElseThrow();
		member.withdraw(LocalDateTime.now());
		memberRepository.saveAndFlush(member);

		change(memberId, identityStore.verify(NAME, BIRTH_DATE, NEW_PHONE, hasher.ci(CI)))
			.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("회원 전용 확인: 이미 가입한 본인이 다시 인증해도 409가 아니라 200으로 결과를 저장한다")
	void completeForMemberSucceeds() throws Exception {
		String id = start();
		given(portOneIdentityClient.find(id)).willReturn(portOne(id, NAME, CI));

		complete(memberId, id)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.verificationToken").value(id))
			.andExpect(jsonPath("$.data.phone").value(NEW_PHONE));

		assertThat(identityStore.findVerified(id)).isPresent();
		change(memberId, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.phone").value(NEW_PHONE));
	}

	@Test
	@DisplayName("회원 전용 확인: 다른 사람이 인증하면 403 MEMBER_IDENTITY_MISMATCH이고 결과를 저장하지 않는다")
	void completeForOtherPersonFails() throws Exception {
		String id = start();
		given(portOneIdentityClient.find(id)).willReturn(portOne(id, "박러너", "other-ci"));

		complete(memberId, id)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MEMBER_IDENTITY_MISMATCH"));

		assertThat(identityStore.findVerified(id)).isEmpty();
	}

	@Test
	@DisplayName("회원 전용 확인: 다른 사람이 이미 확인한 결과의 ID로 불러도 403이고 그 사람의 정보를 돌려주지 않는다")
	void completeWithOthersSavedResultFails() throws Exception {
		String othersToken = identityStore.verify("박러너", LocalDate.of(1990, 1, 1), NEW_PHONE, null);

		complete(memberId, othersToken)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MEMBER_IDENTITY_MISMATCH"))
			.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	@DisplayName("회원 전용 확인은 로그인해야 부를 수 있다 (헤더 없으면 401)")
	void completeRequiresLogin() throws Exception {
		mockMvc.perform(post(COMPLETE_PATH.formatted("crewrun-iv-x")))
			.andExpect(status().isUnauthorized());
	}

	private long join(String name, LocalDate birthDate, String phone, String ciHash) {
		String token = identityStore.verify(name, birthDate, phone, ciHash);
		MemberJoinRequestDto request = MemberFixture.joinRequest().build(identityStore);
		return memberFacade.join(new MemberJoinRequestDto(request.getEmail(), request.getPassword(),
			request.getNickname(), null, List.of(TermsType.SERVICE, TermsType.PRIVACY), token)).getId();
	}

	// 본인인증 해시가 생기기 전(BUK-33)에 가입한 회원처럼 만든다
	private void clearIdentityHashes(long id) {
		Member member = memberRepository.findById(id).orElseThrow();
		member.recordIdentityVerification(null, null, null, null);
		memberRepository.saveAndFlush(member);
	}

	private ResultActions change(long id, String token) throws Exception {
		return mockMvc.perform(put(PHONE_PATH)
			.header(MemberIdHeader.NAME, id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"verificationToken\":\"" + token + "\"}"));
	}

	private String start() throws Exception {
		String body = mockMvc.perform(post(START_PATH)).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.data.identityVerificationId");
	}

	private ResultActions complete(long id, String identityVerificationId) throws Exception {
		return mockMvc.perform(post(COMPLETE_PATH.formatted(identityVerificationId)).header(MemberIdHeader.NAME, id));
	}

	private static PortOneIdentityVerification portOne(String id, String name, String ci) {
		return new PortOneIdentityVerification(id, "VERIFIED", name, BIRTH_DATE, "01055556666", ci,
			Instant.parse("2026-11-04T03:00:00Z"));
	}
}
