package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.common.shared.member.web.MemberIdHeader;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.app.SocialAccountUseCase;
import com.bukang.member.crew.out.CrewInviteRepository;
import com.bukang.member.crew.out.CrewMemberRepository;
import com.bukang.member.crew.out.CrewRepository;
import com.bukang.member.domain.SocialProvider;
import com.bukang.member.domain.exception.KakaoUnavailableException;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.SocialAccountRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.out.kakao.KakaoApiException;
import com.bukang.member.out.kakao.KakaoFriend;
import com.bukang.member.out.kakao.KakaoRefreshedToken;
import com.bukang.member.out.kakao.KakaoTalkClient;
import com.bukang.member.out.oauth2.InMemorySocialLoginStore;
import com.bukang.member.out.oauth2.SocialIdentity;
import com.bukang.member.out.oauth2.SocialOAuth2Client;
import com.bukang.member.out.oauth2.SocialOAuth2Client.AuthorizationStart;
import com.bukang.member.out.oauth2.SocialTokens;
import com.bukang.member.out.oauth2.SocialUnlinkPort;
import com.bukang.member.out.oauth2.StoredAuthorization;
import com.bukang.member.support.MemberFixture;

import jakarta.servlet.http.Cookie;

// 크루 초대 카카오톡 보내기: 메시지 추가 동의, 카카오 토큰 저장·갱신·삭제, 친구 조회, 보내기 (kakao-crew-invite 결정 1~3)
// 카카오 로그인과 카카오 API는 목으로 바꾼다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({InMemorySocialLoginStore.Config.class, InMemoryIdentityVerificationStore.Config.class})
class ApiV1KakaoCrewInviteTest {
	private static final String CONSENT_TICKET = "/api/v1/member/members/me/kakao-message/consent-ticket";
	private static final String FRIENDS = "/api/v1/member/crews/{crewId}/kakao-invites/friends";
	private static final String SEND = "/api/v1/member/crews/{crewId}/kakao-invites";
	private static final String STATE = "state-1";
	private static final Set<String> MESSAGE_SCOPES = Set.of("profile_nickname", "talk_message", "friends");
	private static final Pattern INVITE_LINK = Pattern.compile("http://localhost:3000/invite/([^\"\\\\]+)");

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private MemberFacade memberFacade;
	@Autowired
	private SocialAccountUseCase socialAccountUseCase;
	@Autowired
	private InMemoryIdentityVerificationStore identityStore;
	@Autowired
	private InMemorySocialLoginStore store;
	@Autowired
	private MemberRepository memberRepository;
	@Autowired
	private SocialAccountRepository socialAccountRepository;
	@Autowired
	private TermsAgreementRepository termsAgreementRepository;
	@Autowired
	private CrewRepository crewRepository;
	@Autowired
	private CrewMemberRepository crewMemberRepository;
	@Autowired
	private CrewInviteRepository crewInviteRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private SocialOAuth2Client socialOAuth2Client;
	@MockitoBean
	private SocialUnlinkPort socialUnlinkPort;
	@MockitoBean
	private KakaoTalkClient kakaoTalkClient;

	private long leaderId;
	private long friendId;
	private long crewMateId;
	private long emailOnlyId;
	private long crewId;

	@BeforeEach
	void setUp() throws Exception {
		given(socialOAuth2Client.isConfigured(any())).willReturn(true);
		given(socialOAuth2Client.beginMessageConsent(any())).willReturn(new AuthorizationStart(
			"https://kauth.kakao.com/oauth/authorize?scope=talk_message%20friends",
			new StoredAuthorization(SocialProvider.KAKAO, STATE, "http://localhost/callback",
				Set.of("talk_message", "friends"), Map.of(), null, null)));

		leaderId = member("새벽러너", "k-leader");
		friendId = member("주말10K", "k-friend");
		crewMateId = member("한강러너", "k-mate");
		emailOnlyId = memberFacade.join(MemberFixture.joinRequest().nickname("이메일러너").build(identityStore)).getId();
		crewId = createCrew(leaderId, "새벽한강크루");
		joinCrew(crewId, leaderId, crewMateId);
	}

	@AfterEach
	void tearDown() {
		store.clear();
		crewInviteRepository.deleteAll();
		crewMemberRepository.deleteAll();
		crewRepository.deleteAll();
		socialAccountRepository.deleteAll();
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("동의 표를 받아 카카오 추가 동의를 마치면 토큰을 암호문으로 저장하고 초대 화면으로 돌려보내며, 저장한 토큰으로 친구를 조회한다")
	void consentFlowStoresEncryptedTokens() throws Exception {
		MockHttpServletResponse issued = mockMvc.perform(post(CONSENT_TICKET).header(MemberIdHeader.NAME, leaderId))
			.andExpect(status().isCreated())
			.andReturn().getResponse();
		Cookie ticket = issued.getCookie("oauth2Link");
		assertThat(ticket.isHttpOnly()).isTrue();
		assertThat(ticket.getPath()).isEqualTo("/api/v1/auth/oauth2/authorize");
		assertThat(ticket.getMaxAge()).isEqualTo(120);

		MockHttpServletResponse started = mockMvc.perform(get("/api/v1/auth/oauth2/authorize/kakao")
				.cookie(ticket).param("next", invitesPath()))
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION,
				"https://kauth.kakao.com/oauth/authorize?scope=talk_message%20friends"))
			.andReturn().getResponse();
		verify(socialOAuth2Client, never()).begin(any(), any());
		identity("k-leader", tokens("access-1", "refresh-1", MESSAGE_SCOPES, 6));

		callback(started.getCookie("oauth2Request").getValue())
			.andExpect(header().string(HttpHeaders.LOCATION, invitesPath() + "?kakaoConsent=granted"));

		Map<String, Object> row = tokenRow(leaderId);
		assertThat(row.get("access_token")).isNotNull().isNotEqualTo("access-1");
		assertThat(row.get("refresh_token")).isNotNull().isNotEqualTo("refresh-1");
		assertThat((String)row.get("scopes")).contains("talk_message", "friends");

		given(kakaoTalkClient.friends("access-1")).willReturn(List.of());
		friends(leaderId).andExpect(status().isOk());
		verify(kakaoTalkClient).friends("access-1");
	}

	@Test
	@DisplayName("카카오가 연결되지 않은 회원은 동의 표를 받지 못하고, 친구 조회도 409 KAKAO_NOT_LINKED다")
	void consentRequiresKakaoLink() throws Exception {
		joinCrewAsManager(emailOnlyId);

		mockMvc.perform(post(CONSENT_TICKET).header(MemberIdHeader.NAME, emailOnlyId))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("KAKAO_NOT_LINKED"));
		friends(emailOnlyId)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("KAKAO_NOT_LINKED"));
		verifyNoInteractions(kakaoTalkClient);
	}

	@Test
	@DisplayName("연결된 카카오 계정과 다른 계정으로 동의하면 토큰을 저장하지 않고 KAKAO_ACCOUNT_MISMATCH로 돌려보낸다")
	void consentRejectsOtherKakaoAccount() throws Exception {
		String requestId = startConsent(leaderId);
		identity("k-someone-else", tokens("access-x", "refresh-x", MESSAGE_SCOPES, 6));

		callback(requestId)
			.andExpect(header().string(HttpHeaders.LOCATION, invitesPath() + "?error=KAKAO_ACCOUNT_MISMATCH"));

		assertThat(tokenRow(leaderId).get("access_token")).isNull();
	}

	@Test
	@DisplayName("친구 목록이나 메시지 중 하나라도 동의하지 않았거나 동의 화면에서 취소하면 KAKAO_CONSENT_DENIED로 돌려보낸다")
	void consentDenied() throws Exception {
		String requestId = startConsent(leaderId);
		identity("k-leader", tokens("access-1", "refresh-1", Set.of("profile_nickname", "talk_message"), 6));
		callback(requestId)
			.andExpect(header().string(HttpHeaders.LOCATION, invitesPath() + "?error=KAKAO_CONSENT_DENIED"));
		assertThat(tokenRow(leaderId).get("access_token")).isNull();

		String canceled = startConsent(leaderId);
		mockMvc.perform(get("/api/v1/auth/oauth2/callback/kakao")
				.param("error", "access_denied").param("state", STATE)
				.cookie(new Cookie("oauth2Request", canceled)))
			.andExpect(header().string(HttpHeaders.LOCATION, invitesPath() + "?error=KAKAO_CONSENT_DENIED"));
	}

	@Test
	@DisplayName("동의하지 않은 회원이 친구를 조회하거나 보내면 403 KAKAO_MESSAGE_CONSENT_REQUIRED이고 카카오를 부르지 않는다")
	void consentRequired() throws Exception {
		friends(leaderId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("KAKAO_MESSAGE_CONSENT_REQUIRED"));
		send(leaderId, "[\"uuid-friend\"]")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("KAKAO_MESSAGE_CONSENT_REQUIRED"));
		verifyNoInteractions(kakaoTalkClient);
	}

	@Test
	@DisplayName("친구 목록에서 크루런 회원이면서 아직 그 크루원이 아닌 사람만 카카오 프로필로 돌려준다")
	void friendsFilteredToMembersOutsideCrew() throws Exception {
		consent(leaderId, "k-leader", tokens("access-1", "refresh-1", MESSAGE_SCOPES, 6));
		given(kakaoTalkClient.friends("access-1")).willReturn(List.of(
			new KakaoFriend("k-friend", "uuid-friend", "주말친구", "https://k.kakaocdn.net/f.jpg"),
			new KakaoFriend("k-mate", "uuid-mate", "한강친구", null),
			new KakaoFriend("k-stranger", "uuid-stranger", "모르는친구", null)));

		friends(leaderId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].uuid").value("uuid-friend"))
			.andExpect(jsonPath("$.data[0].nickname").value("주말친구"))
			.andExpect(jsonPath("$.data[0].profileImageUrl").value("https://k.kakaocdn.net/f.jpg"))
			.andExpect(jsonPath("$.data[0].id").doesNotExist());
	}

	@Test
	@DisplayName("액세스 토큰이 곧 끝나면 리프레시 토큰으로 한 번 갱신해 저장하고, 다음 요청은 새 토큰을 그대로 쓴다")
	void expiredAccessTokenRefreshed() throws Exception {
		consent(leaderId, "k-leader", tokens("access-1", "refresh-1", MESSAGE_SCOPES, -1));
		given(kakaoTalkClient.refresh("refresh-1")).willReturn(new KakaoRefreshedToken("access-2", 21599, "refresh-2"));
		given(kakaoTalkClient.friends("access-2")).willReturn(List.of());

		friends(leaderId).andExpect(status().isOk());
		friends(leaderId).andExpect(status().isOk());

		verify(kakaoTalkClient, times(1)).refresh("refresh-1");
		verify(kakaoTalkClient, times(2)).friends("access-2");
		verify(kakaoTalkClient, never()).friends("access-1");
	}

	@Test
	@DisplayName("갱신이 거절되면 저장한 토큰을 지우고 403 KAKAO_MESSAGE_CONSENT_REQUIRED로 다시 동의하게 한다")
	void refreshRejectedClearsTokens() throws Exception {
		consent(leaderId, "k-leader", tokens("access-1", "refresh-1", MESSAGE_SCOPES, -1));
		given(kakaoTalkClient.refresh("refresh-1")).willThrow(new KakaoApiException(-401));

		friends(leaderId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("KAKAO_MESSAGE_CONSENT_REQUIRED"));

		assertThat(tokenRow(leaderId).get("access_token")).isNull();
		assertThat(tokenRow(leaderId).get("refresh_token")).isNull();
	}

	@Test
	@DisplayName("카카오가 토큰이 틀렸거나(-401) 동의가 빠졌다고(-402) 답하면 토큰을 지우고 403으로 다시 동의하게 한다")
	void kakaoTokenErrorsClearTokens() throws Exception {
		consent(leaderId, "k-leader", tokens("access-1", "refresh-1", MESSAGE_SCOPES, 6));
		given(kakaoTalkClient.friends("access-1")).willThrow(new KakaoApiException(-402));
		friends(leaderId)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("KAKAO_MESSAGE_CONSENT_REQUIRED"));
		assertThat(tokenRow(leaderId).get("access_token")).isNull();

		consent(leaderId, "k-leader", tokens("access-1", "refresh-1", MESSAGE_SCOPES, 6));
		willThrow(new KakaoApiException(-401))
			.given(kakaoTalkClient).send(eq("access-1"), anyString(), anyString());
		send(leaderId, "[\"uuid-friend\"]")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("KAKAO_MESSAGE_CONSENT_REQUIRED"));
		assertThat(tokenRow(leaderId).get("access_token")).isNull();
	}

	@Test
	@DisplayName("받는 사람마다 새 초대 링크를 만들어 크루 이름과 링크 버튼을 담아 보내고, 응답에는 초대 토큰을 싣지 않는다")
	void sendCreatesInvitePerReceiver() throws Exception {
		consent(leaderId, "k-leader", tokens("access-1", "refresh-1", MESSAGE_SCOPES, 6));

		String body = send(leaderId, "[\"uuid-friend\",\"uuid-other\"]")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.sentUuids.length()").value(2))
			.andExpect(jsonPath("$.data.failures.length()").value(0))
			.andReturn().getResponse().getContentAsString();

		ArgumentCaptor<String> templates = ArgumentCaptor.forClass(String.class);
		verify(kakaoTalkClient).send(eq("access-1"), eq("uuid-friend"), templates.capture());
		verify(kakaoTalkClient).send(eq("access-1"), eq("uuid-other"), templates.capture());
		String first = inviteToken(templates.getAllValues().get(0));
		String second = inviteToken(templates.getAllValues().get(1));
		assertThat(first).isNotEqualTo(second);
		assertThat(templates.getAllValues().get(0)).contains("\"object_type\":\"feed\"", "새벽한강크루", "새벽러너");
		assertThat(body).doesNotContain(first, second);

		// 메시지 속 링크로 받은 사람이 초대를 열 수 있다
		mockMvc.perform(get("/api/v1/member/crews/invites/{inviteToken}", first).header(MemberIdHeader.NAME, friendId))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("보내는 도중 카카오가 토큰 오류로 답하면 멈추고, 이미 보낸 사람은 200으로 알리며 남은 사람은 실패로 돌려준다")
	void tokenErrorMidway() throws Exception {
		consent(leaderId, "k-leader", tokens("access-1", "refresh-1", MESSAGE_SCOPES, 6));
		willThrow(new KakaoApiException(-401))
			.given(kakaoTalkClient).send(eq("access-1"), eq("uuid-other"), anyString());

		send(leaderId, "[\"uuid-friend\",\"uuid-other\",\"uuid-third\"]")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.sentUuids.length()").value(1))
			.andExpect(jsonPath("$.data.sentUuids[0]").value("uuid-friend"))
			.andExpect(jsonPath("$.data.failures.length()").value(2))
			.andExpect(jsonPath("$.data.failures[0].reason").value("KAKAO_ERROR"));

		verify(kakaoTalkClient, never()).send(anyString(), eq("uuid-third"), anyString());
		assertThat(tokenRow(leaderId).get("access_token")).isNull();
		assertThat(canceledInvites()).isEqualTo(1);
	}

	@Test
	@DisplayName("일부만 하루 한도에 걸리면 200으로 보낸 사람과 실패 사유를 알리고, 보내지 못한 몫의 초대 링크는 취소한다")
	void partialDailyLimit() throws Exception {
		consent(leaderId, "k-leader", tokens("access-1", "refresh-1", MESSAGE_SCOPES, 6));
		willThrow(new KakaoApiException(-532))
			.given(kakaoTalkClient).send(eq("access-1"), eq("uuid-other"), anyString());

		send(leaderId, "[\"uuid-friend\",\"uuid-other\"]")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.sentUuids[0]").value("uuid-friend"))
			.andExpect(jsonPath("$.data.failures[0].uuid").value("uuid-other"))
			.andExpect(jsonPath("$.data.failures[0].reason").value("DAILY_LIMIT"));

		assertThat(canceledInvites()).isEqualTo(1);
	}

	@Test
	@DisplayName("모두 하루 한도(-532·앱 한도 -10)에 걸리면 429 KAKAO_MESSAGE_DAILY_LIMIT이고 만든 초대 링크를 모두 취소한다")
	void allDailyLimit() throws Exception {
		consent(leaderId, "k-leader", tokens("access-1", "refresh-1", MESSAGE_SCOPES, 6));
		willThrow(new KakaoApiException(-532))
			.given(kakaoTalkClient).send(eq("access-1"), eq("uuid-friend"), anyString());
		willThrow(new KakaoApiException(-10))
			.given(kakaoTalkClient).send(eq("access-1"), eq("uuid-other"), anyString());

		send(leaderId, "[\"uuid-friend\",\"uuid-other\"]")
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("KAKAO_MESSAGE_DAILY_LIMIT"));

		assertThat(canceledInvites()).isEqualTo(2);
	}

	@Test
	@DisplayName("카카오가 응답하지 않으면 502 KAKAO_UNAVAILABLE이고 만든 초대 링크를 취소한다")
	void kakaoUnavailable() throws Exception {
		consent(leaderId, "k-leader", tokens("access-1", "refresh-1", MESSAGE_SCOPES, 6));
		willThrow(new KakaoUnavailableException()).given(kakaoTalkClient).send(anyString(), anyString(), anyString());

		send(leaderId, "[\"uuid-friend\"]")
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.code").value("KAKAO_UNAVAILABLE"));

		assertThat(canceledInvites()).isEqualTo(1);
	}

	@Test
	@DisplayName("크루원이 아니면(남의 크루 포함) 403 CREW_NOT_MEMBER, 초대할 수 없는 크루원이면 403 CREW_INVITE_FORBIDDEN이다")
	void onlyInvitersOfThisCrew() throws Exception {
		long invitesBefore = crewInviteRepository.count();
		consent(friendId, "k-friend", tokens("access-f", "refresh-f", MESSAGE_SCOPES, 6));
		friends(friendId).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CREW_NOT_MEMBER"));
		send(friendId, "[\"uuid-leader\"]")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CREW_NOT_MEMBER"));

		consent(crewMateId, "k-mate", tokens("access-m", "refresh-m", MESSAGE_SCOPES, 6));
		send(crewMateId, "[\"uuid-friend\"]")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CREW_INVITE_FORBIDDEN"));

		verifyNoInteractions(kakaoTalkClient);
		assertThat(crewInviteRepository.count()).isEqualTo(invitesBefore);
	}

	@Test
	@DisplayName("받는 사람이 없거나, 6명 이상이거나, 같은 사람이 두 번 들어 있으면 400 INVALID_REQUEST다")
	void receiverValidation() throws Exception {
		consent(leaderId, "k-leader", tokens("access-1", "refresh-1", MESSAGE_SCOPES, 6));

		send(leaderId, "[]").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		send(leaderId, "[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"]")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		send(leaderId, "[\"a\",\"a\"]")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		verifyNoInteractions(kakaoTalkClient);
	}

	@Test
	@DisplayName("카카오 연결을 해제하면 저장한 카카오 토큰도 함께 지워진다")
	void unlinkRemovesTokens() throws Exception {
		consent(leaderId, "k-leader", tokens("access-1", "refresh-1", MESSAGE_SCOPES, 6));

		mockMvc.perform(delete("/api/v1/member/members/me/social-accounts/kakao").header(MemberIdHeader.NAME, leaderId))
			.andExpect(status().isOk());

		Long stored = jdbcTemplate.queryForObject(
			"select count(*) from member_social_account where member_id = ?", Long.class, leaderId);
		assertThat(stored).isZero();
	}

	private long member(String nickname, String kakaoUserId) {
		long memberId = memberFacade.join(MemberFixture.joinRequest().nickname(nickname).build(identityStore)).getId();
		socialAccountUseCase.link(memberId, new SocialIdentity(SocialProvider.KAKAO, kakaoUserId, null, null));
		return memberId;
	}

	private long createCrew(long memberId, String name) throws Exception {
		String body = mockMvc.perform(post("/api/v1/member/crews").header(MemberIdHeader.NAME, memberId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return Long.parseLong(body.replaceAll(".*\"crewId\":(\\d+).*", "$1"));
	}

	private void joinCrew(long crewId, long inviterId, long memberId) throws Exception {
		String body = mockMvc.perform(post("/api/v1/member/crews/{crewId}/invites", crewId)
				.header(MemberIdHeader.NAME, inviterId))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		String token = body.replaceAll(".*\"inviteToken\":\"([^\"]+)\".*", "$1");
		mockMvc.perform(post("/api/v1/member/crews/invites/{inviteToken}/join", token)
				.header(MemberIdHeader.NAME, memberId))
			.andExpect(status().is2xxSuccessful());
	}

	// 카카오가 없는 회원도 초대할 수 있는 역할이어야 KAKAO_NOT_LINKED까지 간다
	private void joinCrewAsManager(long memberId) throws Exception {
		joinCrew(crewId, leaderId, memberId);
		jdbcTemplate.update("update member_crew_member set role = 'MANAGER' where crew_id = ? and member_id = ?",
			crewId, memberId);
	}

	private String invitesPath() {
		return "/crews/" + crewId + "/invites";
	}

	private static SocialTokens tokens(String access, String refresh, Set<String> scopes, long expiresInHours) {
		return new SocialTokens(access, Instant.now().plus(expiresInHours, ChronoUnit.HOURS), refresh, scopes);
	}

	private void identity(String kakaoUserId, SocialTokens tokens) {
		given(socialOAuth2Client.complete(any(), anyString(), anyString()))
			.willReturn(new SocialIdentity(SocialProvider.KAKAO, kakaoUserId, null, null, tokens));
	}

	private String startConsent(long memberId) throws Exception {
		Cookie ticket = mockMvc.perform(post(CONSENT_TICKET).header(MemberIdHeader.NAME, memberId))
			.andReturn().getResponse().getCookie("oauth2Link");
		return mockMvc.perform(get("/api/v1/auth/oauth2/authorize/kakao").cookie(ticket).param("next", invitesPath()))
			.andReturn().getResponse().getCookie("oauth2Request").getValue();
	}

	private void consent(long memberId, String kakaoUserId, SocialTokens tokens) throws Exception {
		String requestId = startConsent(memberId);
		identity(kakaoUserId, tokens);
		callback(requestId)
			.andExpect(header().string(HttpHeaders.LOCATION, invitesPath() + "?kakaoConsent=granted"));
	}

	private ResultActions callback(String requestId) throws Exception {
		return mockMvc.perform(get("/api/v1/auth/oauth2/callback/kakao")
			.param("code", "authorization-code")
			.param("state", STATE)
			.cookie(new Cookie("oauth2Request", requestId)));
	}

	private ResultActions friends(long memberId) throws Exception {
		return mockMvc.perform(get(FRIENDS, crewId).header(MemberIdHeader.NAME, memberId));
	}

	private ResultActions send(long memberId, String receiverUuidsJson) throws Exception {
		return mockMvc.perform(post(SEND, crewId).header(MemberIdHeader.NAME, memberId)
			.contentType(MediaType.APPLICATION_JSON).content("{\"receiverUuids\":" + receiverUuidsJson + "}"));
	}

	private Map<String, Object> tokenRow(long memberId) {
		return jdbcTemplate.queryForMap("select access_token, refresh_token, scopes from member_social_account"
			+ " where member_id = ? and provider = 'KAKAO'", memberId);
	}

	private long canceledInvites() {
		return jdbcTemplate.queryForObject(
			"select count(*) from member_crew_invite where canceled_at is not null", Long.class);
	}

	private static String inviteToken(String template) {
		Matcher matcher = INVITE_LINK.matcher(template);
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}
}
