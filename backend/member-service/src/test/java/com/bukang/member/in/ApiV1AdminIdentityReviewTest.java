package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.common.shared.member.web.MemberIdHeader;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.domain.TermsType;
import com.bukang.member.dto.MemberJoinRequestDto;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.support.MemberFixture;

// 어드민 「확인이 필요한 계정」 조회 (BUK-72, BE-038)
// 이름 + 생년월일 해시가 같고 휴대폰이 다른 회원을 묶음으로, 개인정보는 가려서 보여 준다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InMemoryIdentityVerificationStore.Config.class)
class ApiV1AdminIdentityReviewTest {
	private static final String URL = "/api/v1/member/admin/members/identity-review";
	private static final LocalDate BIRTH = LocalDate.of(1990, 1, 2);

	@Autowired
	private InMemoryIdentityVerificationStore identityStore;
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private MemberFacade memberFacade;
	@Autowired
	private MemberRepository memberRepository;
	@Autowired
	private TermsAgreementRepository termsAgreementRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	private long adminId;

	@BeforeEach
	void setUp() {
		// 관리자는 다른 회원과 이름이 달라 어느 묶음에도 들어가지 않는다
		adminId = memberFacade.join(MemberFixture.joinRequest()
			.email("admin@crewrun.com")
			.name("관리자")
			.build(identityStore)).getId();
		memberFacade.grantAdmin("admin@crewrun.com");
	}

	@AfterEach
	void tearDown() {
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("관리자가 아니면 403이다")
	void nonAdminIsForbidden() throws Exception {
		long memberId = join("홍길동", BIRTH, "010-1111-1111", "hong1@crewrun.com");

		find(memberId, 0, 20)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MEMBER_NOT_ADMIN"));
	}

	@Test
	@DisplayName("회원 ID 헤더가 없으면 401이다")
	void loginRequired() throws Exception {
		mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("이름과 생년월일이 같고 휴대폰이 다른 회원을 한 묶음으로, 최근 가입자부터 가려서 보여 준다")
	void groupsSameNameBirthWithDifferentPhones() throws Exception {
		long first = join("홍길동", BIRTH, "010-1111-1111", "hong1@crewrun.com");
		long second = joinWithoutCi("홍길동", BIRTH, "010-2222-5678", "hong2@crewrun.com");
		setJoinedAt(first, LocalDateTime.of(2026, 11, 1, 9, 0));
		setJoinedAt(second, LocalDateTime.of(2026, 11, 2, 9, 0));

		String body = find(adminId, 0, 20)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(1))
			.andExpect(jsonPath("$.data.groups.length()").value(1))
			.andExpect(jsonPath("$.data.groups[0].members.length()").value(2))
			.andExpect(jsonPath("$.data.groups[0].members[0].id").value(second))
			.andExpect(jsonPath("$.data.groups[0].members[0].maskedName").value("홍*동"))
			.andExpect(jsonPath("$.data.groups[0].members[0].maskedPhone").value("010-****-5678"))
			.andExpect(jsonPath("$.data.groups[0].members[0].maskedEmail").value("ho***@crewrun.com"))
			.andExpect(jsonPath("$.data.groups[0].members[0].ciVerified").value(false))
			.andExpect(jsonPath("$.data.groups[0].members[0].nickname").exists())
			.andExpect(jsonPath("$.data.groups[0].members[0].joinedAt").exists())
			.andExpect(jsonPath("$.data.groups[0].members[1].id").value(first))
			.andExpect(jsonPath("$.data.groups[0].members[1].ciVerified").value(true))
			.andReturn().getResponse().getContentAsString();

		// 원래 이름 · 휴대폰 · 이메일 · 생년월일은 응답 어디에도 없다
		assertThat(body).doesNotContain("홍길동", "1111-1111", "2222-5678", "hong1@", "hong2@", "1990-01-02");
	}

	@Test
	@DisplayName("이름만 같거나 생년월일만 같은 회원, 혼자인 회원은 묶지 않는다")
	void ignoresDifferentNameOrBirth() throws Exception {
		join("홍길동", BIRTH, "010-1111-1111", "hong1@crewrun.com");
		join("홍길동", BIRTH.plusDays(1), "010-1111-2222", "hong2@crewrun.com");
		join("김철수", BIRTH, "010-1111-3333", "kim@crewrun.com");

		find(adminId, 0, 20)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(0))
			.andExpect(jsonPath("$.data.groups.length()").value(0));
	}

	@Test
	@DisplayName("탈퇴한 회원은 빼고, 남은 회원이 한 명이면 묶음도 보여 주지 않는다")
	void excludesWithdrawnMembers() throws Exception {
		join("홍길동", BIRTH, "010-1111-1111", "hong1@crewrun.com");
		long second = join("홍길동", BIRTH, "010-1111-2222", "hong2@crewrun.com");
		long third = join("홍길동", BIRTH, "010-1111-3333", "hong3@crewrun.com");
		markWithdrawn(third);

		find(adminId, 0, 20)
			.andExpect(jsonPath("$.data.groups[0].members.length()").value(2))
			.andExpect(jsonPath("$.data.groups[0].members[?(@.id == %d)]".formatted(third)).isEmpty());

		markWithdrawn(second);

		find(adminId, 0, 20)
			.andExpect(jsonPath("$.data.totalElements").value(0));
	}

	@Test
	@DisplayName("이름 + 생년월일 해시가 비어 있는 회원(본인인증 전 가입)은 대상에서 뺀다")
	void excludesMembersWithoutHash() throws Exception {
		join("홍길동", BIRTH, "010-1111-1111", "hong1@crewrun.com");
		long legacy = join("홍길동", BIRTH, "010-1111-2222", "hong2@crewrun.com");
		jdbcTemplate.update("update member_member set name_birth_hash = null where member_id = ?", legacy);

		find(adminId, 0, 20)
			.andExpect(jsonPath("$.data.totalElements").value(0));
	}

	@Test
	@DisplayName("가장 최근 가입자가 있는 묶음부터, 묶음 단위로 페이지를 나눈다")
	void pagesByGroupNewestFirst() throws Exception {
		long oldA = join("홍길동", BIRTH, "010-1111-1111", "a1@crewrun.com");
		long oldB = join("홍길동", BIRTH, "010-1111-2222", "a2@crewrun.com");
		long newA = join("김철수", BIRTH, "010-1111-3333", "b1@crewrun.com");
		long newB = join("김철수", BIRTH, "010-1111-4444", "b2@crewrun.com");
		setJoinedAt(oldA, LocalDateTime.of(2026, 10, 1, 9, 0));
		setJoinedAt(oldB, LocalDateTime.of(2026, 10, 2, 9, 0));
		setJoinedAt(newA, LocalDateTime.of(2026, 9, 1, 9, 0));
		setJoinedAt(newB, LocalDateTime.of(2026, 11, 1, 9, 0));

		find(adminId, 0, 1)
			.andExpect(jsonPath("$.data.page").value(0))
			.andExpect(jsonPath("$.data.size").value(1))
			.andExpect(jsonPath("$.data.totalElements").value(2))
			.andExpect(jsonPath("$.data.totalPages").value(2))
			.andExpect(jsonPath("$.data.groups.length()").value(1))
			.andExpect(jsonPath("$.data.groups[0].members.length()").value(2))
			.andExpect(jsonPath("$.data.groups[0].members[0].id").value(newB));

		find(adminId, 1, 1)
			.andExpect(jsonPath("$.data.groups.length()").value(1))
			.andExpect(jsonPath("$.data.groups[0].members[0].id").value(oldB))
			.andExpect(jsonPath("$.data.groups[0].members[1].id").value(oldA));

		// 범위 밖 페이지는 빈 목록이고 전체 묶음 수는 그대로다
		find(adminId, 5, 1)
			.andExpect(jsonPath("$.data.groups.length()").value(0))
			.andExpect(jsonPath("$.data.totalElements").value(2));
	}

	@Test
	@DisplayName("같은 묶음 안에서 가입 시각이 같으면 회원 ID가 큰 회원부터 보여 준다")
	void sameJoinedAtOrdersByIdDesc() throws Exception {
		long first = join("홍길동", BIRTH, "010-1111-1111", "hong1@crewrun.com");
		long second = join("홍길동", BIRTH, "010-1111-2222", "hong2@crewrun.com");
		LocalDateTime sameTime = LocalDateTime.of(2026, 11, 1, 9, 0);
		setJoinedAt(first, sameTime);
		setJoinedAt(second, sameTime);

		find(adminId, 0, 20)
			.andExpect(jsonPath("$.data.groups[0].members[0].id").value(second))
			.andExpect(jsonPath("$.data.groups[0].members[1].id").value(first));
	}

	@Test
	@DisplayName("페이지 번호가 음수이거나 크기가 범위 밖이면 가까운 값으로 맞춘다")
	void clampsPageParameters() throws Exception {
		find(adminId, -1, 1000)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.page").value(0))
			.andExpect(jsonPath("$.data.size").value(100));

		find(adminId, 0, 0)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.size").value(1));
	}

	private ResultActions find(long memberId, int page, int size) throws Exception {
		return mockMvc.perform(get(URL)
			.param("page", String.valueOf(page))
			.param("size", String.valueOf(size))
			.header(MemberIdHeader.NAME, memberId));
	}

	private long join(String name, LocalDate birthDate, String phone, String email) {
		return memberFacade.join(MemberFixture.joinRequest()
			.name(name)
			.birthDate(birthDate)
			.phone(phone)
			.email(email)
			.build(identityStore)).getId();
	}

	// 카카오처럼 CI를 주지 않는 인증으로 가입한다
	private long joinWithoutCi(String name, LocalDate birthDate, String phone, String email) {
		String token = identityStore.verify(name, birthDate, phone, null);
		return memberFacade.join(new MemberJoinRequestDto(email, "password1234", "카카오" + phone.substring(9),
			null, List.of(TermsType.SERVICE, TermsType.PRIVACY), token)).getId();
	}

	private void setJoinedAt(long memberId, LocalDateTime joinedAt) {
		jdbcTemplate.update("update member_member set created_at = ? where member_id = ?", joinedAt, memberId);
	}

	// 해시를 남긴 채 상태만 바꿔, 조회가 상태로도 탈퇴 회원을 거르는지 확인한다
	private void markWithdrawn(long memberId) {
		jdbcTemplate.update("update member_member set status = 'WITHDRAWN' where member_id = ?", memberId);
	}
}
