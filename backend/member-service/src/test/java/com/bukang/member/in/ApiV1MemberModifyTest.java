package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.common.shared.member.event.MemberModifiedEvent;
import com.bukang.common.shared.member.web.MemberIdHeader;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.domain.Member;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.support.MemberFixture;

// 게이트웨이가 토큰을 검사하고 넣어 주는 X-Member-Id 헤더를 테스트에서는 직접 넣는다
// 수정 이벤트는 스프링 이벤트로 발행됐는지만 본다. 아웃박스 행은 MemberOutboxAppendTest가 확인한다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
@Import(InMemoryIdentityVerificationStore.Config.class)
class ApiV1MemberModifyTest {
	@Autowired
	private InMemoryIdentityVerificationStore identityStore;

	private static final String ME = "/api/v1/member/members/me";
	private static final String NAME = "김러너";
	private static final String NICKNAME = "러너1";
	private static final String PHONE = "010-1234-5678";
	private static final String BIRTH_DATE = "1995-04-12";
	private static final String OTHER_NICKNAME = "러너2";
	private static final String OTHER_PHONE = "010-9999-8888";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MemberFacade memberFacade;

	// 동시 수정을 재현하려고 중복 검사(existsBy...)만 바꿔 치고, 저장은 진짜 DB(H2)로 한다
	@MockitoSpyBean
	private MemberRepository memberRepository;

	@Autowired
	private TermsAgreementRepository termsAgreementRepository;

	// 테스트 메서드마다 새로 기록한다. setUp의 가입 이벤트도 담기므로 수정 이벤트만 골라 센다
	@Autowired
	private ApplicationEvents applicationEvents;

	private long memberId;

	@BeforeEach
	void setUp() {
		memberId = memberFacade.join(MemberFixture.joinRequest()
			.email("runner1@crewrun.com").name(NAME).nickname(NICKNAME).phone(PHONE)
			.birthDate(LocalDate.of(1995, 4, 12))
			.build(identityStore)).getId();
		// 가입할 때 부른 중복 검사·저장은 세지 않도록 지운다
		clearInvocations(memberRepository);
	}

	@AfterEach
	void tearDown() {
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("닉네임을 바꾸면 200과 바뀐 회원 정보를 받고 DB에도 반영된다")
	void modifySucceeds() throws Exception {
		modify(memberId, NAME, "새닉네임", PHONE, BIRTH_DATE)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("내 정보를 수정했습니다."))
			.andExpect(jsonPath("$.data.id").value(memberId))
			.andExpect(jsonPath("$.data.nickname").value("새닉네임"))
			.andExpect(jsonPath("$.data.email").value("runner1@crewrun.com"));

		assertThat(memberRepository.findById(memberId).orElseThrow().getNickname()).isEqualTo("새닉네임");
	}

	@Test
	@DisplayName("이름 · 생년월일 · 휴대폰 번호를 보내도 본인인증 값이라 바꾸지 않고 닉네임만 바꾼다")
	void modifyIgnoresVerifiedFields() throws Exception {
		modify(memberId, "박러너", "새닉네임", "01098765432", "1990-01-01")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.name").value(NAME))
			.andExpect(jsonPath("$.data.nickname").value("새닉네임"))
			.andExpect(jsonPath("$.data.phone").value(PHONE))
			.andExpect(jsonPath("$.data.birthDate").value(BIRTH_DATE));

		Member saved = memberRepository.findById(memberId).orElseThrow();
		assertThat(saved.getName()).isEqualTo(NAME);
		assertThat(saved.getPhone()).isEqualTo(PHONE);
		assertThat(saved.getBirthDate()).isEqualTo(LocalDate.parse(BIRTH_DATE));
	}

	@Test
	@DisplayName("닉네임은 그대로 두고 이름 · 휴대폰 번호만 다르게 보내면 아무것도 저장하지 않는다")
	void modifyOnlyVerifiedFieldsDoesNotSave() throws Exception {
		modify(memberId, "박러너", NICKNAME, "01098765432", "1990-01-01")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.name").value(NAME));

		verify(memberRepository, never()).saveAndFlush(any(Member.class));
		assertThat(modifiedEvents()).isEmpty();
	}

	@Test
	@DisplayName("정보를 바꾸면 저장된 수정 시각과 새 닉네임을 담은 수정 이벤트를 한 번 발행한다")
	void modifyPublishesModifiedEvent() throws Exception {
		modify(memberId, NAME, "새닉네임", PHONE, BIRTH_DATE)
			.andExpect(status().isOk());

		Member saved = memberRepository.findById(memberId).orElseThrow();
		assertThat(modifiedEvents())
			.singleElement()
			.satisfies(event -> {
				assertThat(event.id()).isEqualTo(memberId);
				assertThat(event.nickname()).isEqualTo("새닉네임");
				// 리눅스(CI)의 now()는 나노초까지 만들고 DB(H2)는 마이크로초로 반올림해 저장하므로 1마이크로초 차이까지 같게 본다
				assertThat(event.createDate()).isCloseTo(saved.getCreateDate(), within(1, ChronoUnit.MICROS));
				// flush 뒤에 발행해야 옛 수정 시각이 아니라 DB에 저장된 수정 시각이 담긴다
				assertThat(event.modifyDate()).isCloseTo(saved.getModifyDate(), within(1, ChronoUnit.MICROS));
			});
	}

	@Test
	@DisplayName("내 닉네임을 그대로 보내면 내 행에 걸리지 않아 200이고 저장하지 않는다")
	void modifyWithSameValuesDoesNotSave() throws Exception {
		modify(memberId, NAME, NICKNAME, PHONE, BIRTH_DATE)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.nickname").value(NICKNAME));

		verify(memberRepository, never()).saveAndFlush(any(Member.class));
		assertThat(modifiedEvents()).isEmpty();
	}

	@Test
	@DisplayName("다른 회원이 쓰는 닉네임으로 바꾸면 409다")
	void modifyFailsWithOthersNickname() throws Exception {
		memberFacade.join(MemberFixture.joinRequest().nickname(OTHER_NICKNAME).phone(OTHER_PHONE).build(identityStore));

		modify(memberId, NAME, OTHER_NICKNAME, PHONE, BIRTH_DATE)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 사용 중인 닉네임입니다."));

		assertThat(modifiedEvents()).isEmpty();
	}

	@Test
	@DisplayName("동시 수정으로 중복 검사를 통과해도 DB 닉네임 제약에 걸리면 409다")
	void modifyRaceFailsOnNicknameConstraint() throws Exception {
		memberFacade.join(MemberFixture.joinRequest().nickname(OTHER_NICKNAME).phone(OTHER_PHONE).build(identityStore));
		skipDuplicateChecks();

		modify(memberId, NAME, OTHER_NICKNAME, PHONE, BIRTH_DATE)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("이미 사용 중인 닉네임입니다."));

		assertThat(modifiedEvents()).isEmpty();
	}

	@Test
	@DisplayName("닉네임 규칙에 맞지 않으면 400이다")
	void modifyFailsWithInvalidNickname() throws Exception {
		modify(memberId, NAME, "러너!", PHONE, BIRTH_DATE)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("닉네임은 한글, 영문, 숫자만 쓸 수 있습니다."));
	}

	@Test
	@DisplayName("회원 ID 헤더가 없으면 401이다")
	void modifyFailsWithoutHeader() throws Exception {
		mockMvc.perform(put(ME)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(NAME, NICKNAME, PHONE, BIRTH_DATE)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
	}

	@Test
	@DisplayName("헤더의 회원이 없으면 404다")
	void modifyFailsWithUnknownMember() throws Exception {
		modify(memberId + 1000, NAME, NICKNAME, PHONE, BIRTH_DATE)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("존재하지 않는 회원입니다."));
	}

	// MySQL 기본 정렬(utf8mb4_0900_ai_ci)처럼 문자열을 대소문자 구별 없이 비교하는 별도 H2 DB에서 돌린다
	// H2는 기본으로 대소문자를 구별해, 이 설정 없이는 대소문자만 바꿔도 내 행에 걸리는 문제를 재현하지 못한다
	@Nested
	@TestPropertySource(properties = "spring.datasource.url="
		+ "jdbc:h2:mem:bukang-test-ignorecase;MODE=MySQL;DATABASE_TO_LOWER=TRUE;IGNORECASE=TRUE;DB_CLOSE_DELAY=-1")
	class IgnoreCaseDatabase {
		@Test
		@DisplayName("내 닉네임의 대소문자만 바꾸면 200과 바뀐 닉네임을 받는다")
		void modifyNicknameLetterCaseOnly() throws Exception {
			long caseMemberId = memberFacade.join(MemberFixture.joinRequest()
				.name(NAME).nickname("Runner1").phone(OTHER_PHONE).birthDate(LocalDate.of(1995, 4, 12))
				.build(identityStore)).getId();

			modify(caseMemberId, NAME, "runner1", OTHER_PHONE, BIRTH_DATE)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.nickname").value("runner1"));
		}
	}

	private List<MemberModifiedEvent> modifiedEvents() {
		return applicationEvents.stream(MemberModifiedEvent.class).toList();
	}

	// 다른 회원이 아직 없다고 답하게 해, 두 요청이 동시에 중복 검사를 통과한 상황을 만든다
	private void skipDuplicateChecks() {
		willReturn(false).given(memberRepository).existsByNicknameAndIdNot(anyString(), anyLong());
	}

	private ResultActions modify(long id, String name, String nickname, String phone, String birthDate)
		throws Exception {
		return mockMvc.perform(put(ME)
			.header(MemberIdHeader.NAME, id)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(name, nickname, phone, birthDate)));
	}

	private String body(String name, String nickname, String phone, String birthDate) {
		return """
			{
				"name": "%s",
				"nickname": "%s",
				"phone": "%s",
				"birthDate": "%s"
			}
			""".formatted(name, nickname, phone, birthDate);
	}
}
