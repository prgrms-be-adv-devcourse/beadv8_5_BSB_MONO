package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.member.app.MemberFacade;
import com.bukang.member.domain.Member;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.support.MemberFixture;

import jakarta.persistence.EntityManagerFactory;

// 주문 서비스가 파티원 이름을 띄우려고 부르는 내부 API: 회원 ID 여러 개 → 닉네임 (BUK-39)
// 쿼리 수는 Hibernate 통계로 센다 (application-test.yaml의 generate_statistics)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InMemoryIdentityVerificationStore.Config.class)
class InternalV1MemberNicknamesTest {
	private static final String NICKNAMES = "/internal/v1/member/members/nicknames";

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
	private EntityManagerFactory entityManagerFactory;

	private long firstId;
	private long secondId;

	@BeforeEach
	void setUp() {
		firstId = memberFacade.join(MemberFixture.joinRequest().nickname("러너A").build(identityStore)).getId();
		secondId = memberFacade.join(MemberFixture.joinRequest().nickname("러너B").build(identityStore)).getId();
	}

	@AfterEach
	void tearDown() {
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("회원 ID마다 memberId와 nickname을 돌려준다")
	void nicknames() throws Exception {
		findNicknames(firstId + "," + secondId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("회원 닉네임을 조회했습니다."))
			.andExpect(jsonPath("$.data", hasSize(2)))
			.andExpect(jsonPath("$.data[*].memberId").value(containsInAnyOrder((int)firstId, (int)secondId)))
			.andExpect(jsonPath("$.data[*].nickname").value(containsInAnyOrder("러너A", "러너B")));
	}

	@Test
	@DisplayName("닉네임 말고 다른 개인정보(이름, 이메일, 휴대폰, 생년월일 등)는 내보내지 않는다")
	void exposesOnlyNickname() throws Exception {
		findNicknames(String.valueOf(firstId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].length()").value(2))
			.andExpect(jsonPath("$.data[0].memberId").value(firstId))
			.andExpect(jsonPath("$.data[0].nickname").value("러너A"));
	}

	@Test
	@DisplayName("없는 회원 ID는 결과에서 뺀다")
	void skipsUnknownMember() throws Exception {
		findNicknames(firstId + "," + (secondId + 1000))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data", hasSize(1)))
			.andExpect(jsonPath("$.data[0].memberId").value(firstId));
	}

	@Test
	@DisplayName("탈퇴한 회원은 닉네임을 「탈퇴한 회원」으로 돌려준다")
	void withdrawnMember() throws Exception {
		Member member = memberRepository.findById(secondId).orElseThrow();
		member.withdraw(LocalDateTime.now());
		memberRepository.saveAndFlush(member);

		findNicknames(String.valueOf(secondId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data", hasSize(1)))
			.andExpect(jsonPath("$.data[0].memberId").value(secondId))
			.andExpect(jsonPath("$.data[0].nickname").value("탈퇴한 회원"));
	}

	@Test
	@DisplayName("같은 회원 ID가 여러 번 와도 한 번만 돌려준다")
	void duplicateIds() throws Exception {
		findNicknames(firstId + "," + firstId + "," + firstId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data", hasSize(1)));
	}

	@Test
	@DisplayName("같은 회원 ID를 101번 보내도 서로 다른 ID는 하나라 100개 제한에 걸리지 않는다")
	void duplicatesCountOnce() throws Exception {
		String ids = LongStream.range(0, 101)
			.mapToObj(index -> String.valueOf(firstId))
			.collect(Collectors.joining(","));

		findNicknames(ids)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data", hasSize(1)));
	}

	@Test
	@DisplayName("빈 항목과 공백이 섞여도 숫자인 회원 ID만 조회한다")
	void blankItemsAndSpaces() throws Exception {
		findNicknames(firstId + ",," + secondId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data", hasSize(2)));
		findNicknames(" " + firstId + " , " + secondId + " ")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data", hasSize(2)));
	}

	@Test
	@DisplayName("ids가 비어 있거나 없으면 빈 목록을 돌려준다")
	void emptyIds() throws Exception {
		findNicknames("")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data", hasSize(0)));
		mockMvc.perform(get(NICKNAMES))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data", hasSize(0)));
	}

	@Test
	@DisplayName("서로 다른 회원 ID 100개까지는 조회한다")
	void hundredIds() throws Exception {
		findNicknames(idsFrom(firstId, 100))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("서로 다른 회원 ID가 100개를 넘으면 400 INVALID_REQUEST다")
	void tooManyIds() throws Exception {
		findNicknames(idsFrom(firstId, 101))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value("회원 ID는 한 번에 100개까지 조회할 수 있습니다."));
	}

	@Test
	@DisplayName("회원 ID가 숫자가 아니면 500이 아니라 400 INVALID_REQUEST다")
	void notNumber() throws Exception {
		findNicknames(firstId + ",abc")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."));
	}

	@Test
	@DisplayName("회원 수와 상관없이 SQL을 한 번만 실행한다 (회원마다 조회하지 않음)")
	void singleQuery() throws Exception {
		long thirdId = memberFacade.join(MemberFixture.joinRequest().build(identityStore)).getId();
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		findNicknames(firstId + "," + secondId + "," + thirdId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data", hasSize(3)));

		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

	private ResultActions findNicknames(String ids) throws Exception {
		return mockMvc.perform(get(NICKNAMES).param("ids", ids));
	}

	private static String idsFrom(long start, int count) {
		return LongStream.range(start, start + count)
			.mapToObj(String::valueOf)
			.collect(Collectors.joining(","));
	}
}
