package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

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
import com.bukang.member.app.SellerFacade;
import com.bukang.member.domain.Member;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.SellerRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.support.MemberFixture;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManagerFactory;

// 게시판 등 다른 서비스가 회원 복사본을 처음 채우려고 부르는 내부 API: 회원 ID 순서로 나눠 받기 (BUK-161)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InMemoryIdentityVerificationStore.Config.class)
class InternalV1MemberReplicasTest {
	private static final String REPLICAS = "/internal/v1/member/members/replicas";

	@Autowired
	private InMemoryIdentityVerificationStore identityStore;
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private MemberFacade memberFacade;
	@Autowired
	private SellerFacade sellerFacade;
	@Autowired
	private MemberRepository memberRepository;
	@Autowired
	private SellerRepository sellerRepository;
	@Autowired
	private TermsAgreementRepository termsAgreementRepository;
	@Autowired
	private EntityManagerFactory entityManagerFactory;

	private long first;
	private long second;
	private long third;
	private long adminId;

	@BeforeEach
	void setUp() {
		first = join("러너A");
		second = join("러너B");
		third = join("러너C");
		adminId = memberFacade.join(MemberFixture.joinRequest().email("admin@crewrun.com").build(identityStore))
			.getId();
		memberFacade.grantAdmin("admin@crewrun.com");
	}

	@AfterEach
	void tearDown() {
		sellerRepository.deleteAll();
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("회원마다 memberId, nickname, sellerApproved, withdrawn, modifyDate 다섯 칸만 내보낸다 (개인정보 없음)")
	void exposesOnlyPublicFields() throws Exception {
		LocalDateTime modifyDate = memberRepository.findById(first).orElseThrow().getModifyDate();

		String body = replicas(first - 1, 1)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("회원 복사본 목록을 조회했습니다."))
			.andExpect(jsonPath("$.data.members.length()").value(1))
			.andExpect(jsonPath("$.data.members[0].length()").value(5))
			.andExpect(jsonPath("$.data.members[0].memberId").value(first))
			.andExpect(jsonPath("$.data.members[0].nickname").value("러너A"))
			.andExpect(jsonPath("$.data.members[0].sellerApproved").value(false))
			.andExpect(jsonPath("$.data.members[0].withdrawn").value(false))
			.andReturn().getResponse().getContentAsString();
		// 끝자리 0을 줄여 쓰는 표기 차이가 있어 문자열이 아니라 시각으로 비교한다
		String sent = JsonPath.read(body, "$.data.members[0].modifyDate");
		assertThat(LocalDateTime.parse(sent)).isEqualTo(modifyDate);
	}

	@Test
	@DisplayName("afterId보다 큰 회원만 회원 ID 순서로 size명까지 주고, 더 있으면 hasNext와 다음 afterId를 알려 준다")
	void pagesByMemberId() throws Exception {
		replicas(first - 1, 2)
			.andExpect(jsonPath("$.data.members[0].memberId").value(first))
			.andExpect(jsonPath("$.data.members[1].memberId").value(second))
			.andExpect(jsonPath("$.data.members.length()").value(2))
			.andExpect(jsonPath("$.data.hasNext").value(true))
			.andExpect(jsonPath("$.data.nextAfterId").value(second));

		// 다음 afterId로 이어 받으면 나머지 전부(관리자 포함)를 받고 끝난다
		replicas(second, 10)
			.andExpect(jsonPath("$.data.members[0].memberId").value(third))
			.andExpect(jsonPath("$.data.members[1].memberId").value(adminId))
			.andExpect(jsonPath("$.data.members.length()").value(2))
			.andExpect(jsonPath("$.data.hasNext").value(false))
			.andExpect(jsonPath("$.data.nextAfterId").value(adminId));
	}

	@Test
	@DisplayName("딱 size명 남았으면 hasNext는 false다 (빈 다음 쪽을 부르지 않게)")
	void exactlySizeLeft() throws Exception {
		replicas(second, 2)
			.andExpect(jsonPath("$.data.members.length()").value(2))
			.andExpect(jsonPath("$.data.hasNext").value(false));
	}

	@Test
	@DisplayName("더 받을 회원이 없으면 빈 목록, hasNext=false, nextAfterId는 받은 afterId 그대로다")
	void emptyPage() throws Exception {
		replicas(adminId, 10)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.members.length()").value(0))
			.andExpect(jsonPath("$.data.hasNext").value(false))
			.andExpect(jsonPath("$.data.nextAfterId").value(adminId));
	}

	@Test
	@DisplayName("afterId와 size를 안 보내면 처음부터 500명까지 준다")
	void defaults() throws Exception {
		mockMvc.perform(get(REPLICAS))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.members.length()").value(4))
			.andExpect(jsonPath("$.data.hasNext").value(false));
	}

	@Test
	@DisplayName("size는 1~500만 받는다. 0, 501, 숫자가 아닌 값, 음수 afterId는 400 INVALID_REQUEST다")
	void rejectsInvalidParams() throws Exception {
		replicas(0, 500).andExpect(status().isOk());
		for (ResultActions bad : new ResultActions[] {
			replicas(0, 0), replicas(0, 501), replicas(0, -1), replicas(-1, 10),
			mockMvc.perform(get(REPLICAS).param("size", "abc"))
		}) {
			bad.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}
	}

	@Test
	@DisplayName("탈퇴한 회원은 닉네임을 「탈퇴한 회원」으로, withdrawn을 true로 준다 (닉네임 조회 API와 같은 방식)")
	void withdrawnMember() throws Exception {
		Member member = memberRepository.findById(second).orElseThrow();
		member.withdraw(LocalDateTime.now());
		memberRepository.saveAndFlush(member);

		replicas(second - 1, 1)
			.andExpect(jsonPath("$.data.members[0].memberId").value(second))
			.andExpect(jsonPath("$.data.members[0].nickname").value("탈퇴한 회원"))
			.andExpect(jsonPath("$.data.members[0].withdrawn").value(true));
	}

	@Test
	@DisplayName("sellerApproved는 가장 최근 판매자 신청이 승인일 때만 true다 (신청 없음·대기·반려는 false)")
	void sellerApprovedFollowsLatestApplication() throws Exception {
		// first: 신청 없음, second: 대기 중, third: 반려 뒤 재신청해 승인, admin: 반려 뒤 재신청해 대기
		sellerFacade.apply(second, MemberFixture.sellerApplyRequest());

		long rejected = sellerFacade.apply(third, MemberFixture.sellerApplyRequest()).getId();
		sellerFacade.reject(adminId, rejected, "서류 확인 불가");
		long approved = sellerFacade.apply(third, MemberFixture.sellerApplyRequest()).getId();
		sellerFacade.approve(adminId, approved);

		long adminRejected = sellerFacade.apply(adminId, MemberFixture.sellerApplyRequest()).getId();
		sellerFacade.reject(adminId, adminRejected, "서류 확인 불가");

		replicas(first - 1, 10)
			.andExpect(jsonPath("$.data.members.length()").value(4))
			.andExpect(jsonPath("$.data.members[0].sellerApproved").value(false))
			.andExpect(jsonPath("$.data.members[1].sellerApproved").value(false))
			.andExpect(jsonPath("$.data.members[2].sellerApproved").value(true))
			.andExpect(jsonPath("$.data.members[3].sellerApproved").value(false));
	}

	@Test
	@DisplayName("회원 수와 판매자 신청 수에 상관없이 SQL을 한 번만 실행한다 (N+1 없음)")
	void singleQuery() throws Exception {
		long approved = sellerFacade.apply(first, MemberFixture.sellerApplyRequest()).getId();
		sellerFacade.approve(adminId, approved);
		sellerFacade.apply(second, MemberFixture.sellerApplyRequest());
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		replicas(first - 1, 10)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.members.length()").value(4));

		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

	private long join(String nickname) {
		return memberFacade.join(MemberFixture.joinRequest().nickname(nickname).build(identityStore)).getId();
	}

	private ResultActions replicas(long afterId, int size) throws Exception {
		return mockMvc.perform(get(REPLICAS).param("afterId", String.valueOf(afterId))
			.param("size", String.valueOf(size)));
	}
}
