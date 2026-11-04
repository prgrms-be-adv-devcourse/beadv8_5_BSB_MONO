package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.common.shared.member.web.MemberIdHeader;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.app.SocialAccountUseCase;
import com.bukang.member.app.SocialJoinUseCase;
import com.bukang.member.domain.SocialProvider;
import com.bukang.member.domain.TermsType;
import com.bukang.member.dto.SocialJoinRequestDto;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.RefreshTokenRepository;
import com.bukang.member.out.SellerRepository;
import com.bukang.member.out.SocialAccountRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.cash.CashBalancePort;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.out.oauth2.InMemorySocialLoginStore;
import com.bukang.member.out.oauth2.LinkTicket;
import com.bukang.member.out.oauth2.PendingSignup;
import com.bukang.member.out.oauth2.SocialIdentity;
import com.bukang.member.out.oauth2.SocialOAuth2Client;
import com.bukang.member.out.oauth2.SocialOAuth2Client.AuthorizationStart;
import com.bukang.member.out.oauth2.SocialUnlinkPort;
import com.bukang.member.out.oauth2.StoredAuthorization;
import com.bukang.member.out.order.ActiveTicketPort;
import com.bukang.member.support.MemberFixture;

import jakarta.servlet.http.Cookie;

// 로그인한 회원의 계정 연결·해제와 탈퇴 때 연결 정리를 확인한다 (BE-032·034). 제공자 호출은 목으로 바꾼다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({InMemorySocialLoginStore.Config.class, InMemoryIdentityVerificationStore.Config.class})
class ApiV1SocialAccountTest {
	@Autowired
	private InMemoryIdentityVerificationStore identityStore;

	private static final String ACCOUNTS = "/api/v1/member/members/me/social-accounts/";
	private static final String STATE = "state-1";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MemberFacade memberFacade;

	@Autowired
	private SocialJoinUseCase socialJoinUseCase;

	@Autowired
	private SocialAccountUseCase socialAccountUseCase;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private SocialAccountRepository socialAccountRepository;

	@Autowired
	private TermsAgreementRepository termsAgreementRepository;

	@Autowired
	private SellerRepository sellerRepository;

	@Autowired
	private InMemorySocialLoginStore store;

	@MockitoBean
	private SocialOAuth2Client socialOAuth2Client;

	@MockitoBean
	private SocialUnlinkPort socialUnlinkPort;

	@MockitoBean
	private RefreshTokenRepository refreshTokenRepository;

	@MockitoBean
	private CashBalancePort cashBalancePort;

	@MockitoBean
	private ActiveTicketPort activeTicketPort;

	private long emailMemberId;

	@BeforeEach
	void setUp() {
		given(socialOAuth2Client.isConfigured(any())).willReturn(true);
		given(socialOAuth2Client.begin(any(), any())).willAnswer(invocation -> {
			SocialProvider provider = invocation.getArgument(0);
			return new AuthorizationStart("https://provider.example/authorize",
				new StoredAuthorization(provider, STATE, "http://localhost/callback", Set.of("profile"), Map.of(),
					null, null));
		});
		emailMemberId = memberFacade.join(MemberFixture.joinRequest().build(identityStore)).getId();
	}

	@AfterEach
	void tearDown() {
		store.clear();
		socialAccountRepository.deleteAll();
		sellerRepository.deleteAll();
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("연결 표 쿠키를 받아 시작하면 회원 ID가 인가 요청에 저장되고, 콜백 뒤 이메일이 달라도 연결해 계정 연결 화면으로 보낸다")
	void linkFlow() throws Exception {
		MockHttpServletResponse issued = issueTicket(emailMemberId, "google")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data").doesNotExist())
			.andReturn().getResponse();
		Cookie linkCookie = issued.getCookie("oauth2Link");
		assertThat(linkCookie.isHttpOnly()).isTrue();
		assertThat(linkCookie.getPath()).isEqualTo("/api/v1/auth/oauth2/authorize");
		assertThat(linkCookie.getMaxAge()).isEqualTo(120);
		String ticket = linkCookie.getValue();
		assertThat(store.takeLinkTicket(ticket)).contains(new LinkTicket(emailMemberId, SocialProvider.GOOGLE));
		store.saveLinkTicket(ticket, new LinkTicket(emailMemberId, SocialProvider.GOOGLE));

		MockHttpServletResponse started = mockMvc.perform(get("/api/v1/auth/oauth2/authorize/google")
				.cookie(new Cookie("oauth2Link", ticket)).param("next", "/me/accounts"))
			.andExpect(status().isFound())
			.andReturn().getResponse();
		// 연결 표 쿠키는 한 번 쓰면 지운다
		assertThat(started.getCookie("oauth2Link").getMaxAge()).isZero();
		String requestId = started.getCookie("oauth2Request").getValue();
		identity(new SocialIdentity(SocialProvider.GOOGLE, "google-sub-1", "different@gmail.com", null));

		callback("google", requestId)
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, "/me/accounts?linked=google"));

		assertThat(socialAccountRepository.findActiveMemberId(SocialProvider.GOOGLE, "google-sub-1"))
			.contains(emailMemberId);
		// 연결 표는 한 번만 쓴다
		assertThat(store.takeLinkTicket(ticket)).isEmpty();
	}

	@Test
	@DisplayName("다른 회원에 연결된 소셜 계정을 연결하면 next에 AUTH_SOCIAL_ALREADY_LINKED를 붙여 보낸다")
	void linkRejectsAccountOfOtherMember() throws Exception {
		long otherId = socialMember("kakao-1", "other@kakao.com");
		String requestId = startLink(emailMemberId, SocialProvider.KAKAO);
		identity(new SocialIdentity(SocialProvider.KAKAO, "kakao-1", null, null));

		callback("kakao", requestId)
			.andExpect(header().string(HttpHeaders.LOCATION, "/me/accounts?error=AUTH_SOCIAL_ALREADY_LINKED"));

		assertThat(socialAccountRepository.findActiveMemberId(SocialProvider.KAKAO, "kakao-1")).contains(otherId);
	}

	@Test
	@DisplayName("잘못되거나 다른 제공자의 연결 표로 시작하면 계정 연결 화면에 AUTH_SOCIAL_FAILED를 붙여 보낸다")
	void linkRejectsBadTicket() throws Exception {
		mockMvc.perform(get("/api/v1/auth/oauth2/authorize/kakao").cookie(new Cookie("oauth2Link", "unknown")))
			.andExpect(header().string(HttpHeaders.LOCATION, "/me/accounts?error=AUTH_SOCIAL_FAILED"));

		store.saveLinkTicket("google-ticket", new LinkTicket(emailMemberId, SocialProvider.GOOGLE));
		mockMvc.perform(get("/api/v1/auth/oauth2/authorize/kakao").cookie(new Cookie("oauth2Link", "google-ticket")))
			.andExpect(header().string(HttpHeaders.LOCATION, "/me/accounts?error=AUTH_SOCIAL_FAILED"));
	}

	@Test
	@DisplayName("연결 표를 주소에 붙여 보내도 연결로 처리하지 않는다 (남이 만든 링크로 연결되지 않게)")
	void linkTicketInQueryIgnored() throws Exception {
		store.saveLinkTicket("attacker-ticket", new LinkTicket(emailMemberId, SocialProvider.KAKAO));

		String requestId = mockMvc.perform(get("/api/v1/auth/oauth2/authorize/kakao")
				.param("linkTicket", "attacker-ticket"))
			.andExpect(status().isFound())
			.andReturn().getResponse().getCookie("oauth2Request").getValue();

		assertThat(store.takeAuthorization(requestId)).hasValueSatisfying(
			authorization -> assertThat(authorization.isLink()).isFalse());
		assertThat(store.takeLinkTicket("attacker-ticket")).isPresent();
	}

	@Test
	@DisplayName("이미 같은 제공자가 연결돼 있으면 연결 표를 주지 않고 409 MEMBER_SOCIAL_PROVIDER_ALREADY_LINKED다")
	void ticketRejectedWhenProviderLinked() throws Exception {
		socialAccountUseCase.link(emailMemberId, new SocialIdentity(SocialProvider.KAKAO, "kakao-9", null, null));

		issueTicket(emailMemberId, "kakao")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MEMBER_SOCIAL_PROVIDER_ALREADY_LINKED"));
	}

	@Test
	@DisplayName("비밀번호가 있으면 소셜 연결을 해제하고, 커밋 뒤 카카오 연결 끊기를 부른다")
	void unlinkWithPassword() throws Exception {
		socialAccountUseCase.link(emailMemberId, new SocialIdentity(SocialProvider.KAKAO, "kakao-9", null, null));

		unlink(emailMemberId, "kakao")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.socialProviders").isEmpty())
			.andExpect(jsonPath("$.data.passwordSet").value(true));

		assertThat(socialAccountRepository.count()).isZero();
		verify(socialUnlinkPort).unlink(SocialProvider.KAKAO, "kakao-9");
	}

	@Test
	@DisplayName("비밀번호 없는 회원의 마지막 소셜 연결은 409 MEMBER_SOCIAL_LAST_LOGIN_METHOD로 막고, 연결되지 않은 제공자는 404다")
	void unlinkRules() throws Exception {
		long socialId = socialMember("kakao-1", "runner@kakao.com");

		unlink(socialId, "kakao")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MEMBER_SOCIAL_LAST_LOGIN_METHOD"));
		unlink(socialId, "google")
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("MEMBER_SOCIAL_ACCOUNT_NOT_FOUND"));

		assertThat(socialAccountRepository.count()).isEqualTo(1);
		verify(socialUnlinkPort, never()).unlink(any(), anyString());
	}

	@Test
	@DisplayName("소셜 회원이 탈퇴하면 연결 행을 지우고 커밋 뒤 카카오 연결을 끊으며, 같은 카카오 계정으로 다시 오면 새로 가입한다")
	void withdrawRemovesLinks() throws Exception {
		long socialId = socialMember("kakao-1", "runner@kakao.com");

		mockMvc.perform(delete("/api/v1/member/members/me").header(MemberIdHeader.NAME, socialId))
			.andExpect(status().isOk());

		assertThat(socialAccountRepository.count()).isZero();
		verify(socialUnlinkPort).unlink(SocialProvider.KAKAO, "kakao-1");
		assertThat(socialAccountRepository.findActiveMemberId(SocialProvider.KAKAO, "kakao-1")).isEmpty();
	}

	// 카카오로 가입한 비밀번호 없는 회원
	private long socialMember(String kakaoUserId, String email) {
		store.savePendingSignup("pending-" + kakaoUserId,
			new PendingSignup(SocialProvider.KAKAO, kakaoUserId, email, null));
		String token = identityStore.verify("김소셜", LocalDate.of(1995, 4, 12), "010-9876-5432");
		return socialJoinUseCase.join("pending-" + kakaoUserId, new SocialJoinRequestDto(null, "소셜러너", null,
			List.of(TermsType.SERVICE, TermsType.PRIVACY), token))
			.member().getId();
	}

	private String startLink(long memberId, SocialProvider provider) throws Exception {
		store.saveLinkTicket("ticket", new LinkTicket(memberId, provider));
		return mockMvc.perform(get("/api/v1/auth/oauth2/authorize/" + provider.getPathValue())
				.cookie(new Cookie("oauth2Link", "ticket")))
			.andReturn().getResponse().getCookie("oauth2Request").getValue();
	}

	private void identity(SocialIdentity identity) {
		given(socialOAuth2Client.complete(any(), anyString(), anyString())).willReturn(identity);
	}

	private ResultActions issueTicket(long memberId, String provider) throws Exception {
		return mockMvc.perform(post(ACCOUNTS + provider + "/link-ticket").header(MemberIdHeader.NAME, memberId));
	}

	private ResultActions unlink(long memberId, String provider) throws Exception {
		return mockMvc.perform(delete(ACCOUNTS + provider).header(MemberIdHeader.NAME, memberId));
	}

	private ResultActions callback(String provider, String requestId) throws Exception {
		return mockMvc.perform(get("/api/v1/auth/oauth2/callback/" + provider)
			.param("code", "authorization-code")
			.param("state", STATE)
			.cookie(new Cookie("oauth2Request", requestId)));
	}
}
