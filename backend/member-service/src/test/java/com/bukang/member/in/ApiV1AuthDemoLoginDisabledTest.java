package com.bukang.member.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.bukang.member.app.MemberFacade;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.RefreshTokenRepository;
import com.bukang.member.out.RefreshTokenRepository.NewFamily;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.support.MemberFixture;

// 설정을 주지 않은 기본 상태. 운영에서 켜는 것을 잊으면 이 상태가 된다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InMemoryIdentityVerificationStore.Config.class)
class ApiV1AuthDemoLoginDisabledTest {
	private static final String PATH = "/api/v1/auth/demo-login";

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
	private ApplicationContext context;

	@MockitoBean
	private RefreshTokenRepository refreshTokenRepository;

	// 기본 계정이 있어도 꺼져 있으면 로그인되지 않아야 한다
	@BeforeEach
	void setUp() {
		memberFacade.join(MemberFixture.joinRequest().email("admin@crewrun.com").build(identityStore));
	}

	@AfterEach
	void tearDown() {
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}

	@Test
	@DisplayName("기본은 꺼짐이라 GET은 404다 (프론트가 버튼을 숨긴다)")
	void availabilityIsNotFound() throws Exception {
		mockMvc.perform(get(PATH)).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("꺼져 있으면 POST도 404이고 토큰을 주지 않는다")
	void loginIsNotFound() throws Exception {
		MvcResult result = mockMvc.perform(post(PATH))
			.andExpect(status().isNotFound())
			.andReturn();

		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
		verify(refreshTokenRepository, never()).create(anyLong(), any(NewFamily.class));
	}

	@Test
	@DisplayName("꺼져 있으면 데모 로그인 컨트롤러 빈 자체가 없다")
	void controllerBeanAbsent() {
		assertThat(context.getBeanNamesForType(ApiV1AuthDemoLoginController.class)).isEmpty();
	}
}
