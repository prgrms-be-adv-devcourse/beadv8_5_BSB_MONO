package com.bukang.member.out.payoutaccount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bukang.member.config.payoutaccount.PayoutAccountProperties;
import com.bukang.member.domain.PayoutAccountHolderType;
import com.bukang.member.domain.exception.SellerAccountNotVerifiedException;
import com.bukang.member.domain.exception.SellerAccountVerifyUnavailableException;

// 오픈뱅킹 계좌실명조회 어댑터. 오픈뱅킹 서버는 MockRestServiceServer로 대신한다
@ExtendWith(OutputCaptureExtension.class)
class OpenBankingAccountVerifierTest {
	private static final String BASE_URL = "https://testapi.openbanking.or.kr";
	private static final String TOKEN_URL = BASE_URL + "/oauth/2.0/token";
	private static final String REAL_NAME_URL = BASE_URL + "/v2.0/inquiry/real_name";
	private static final String ACCOUNT_NUMBER = "1101230000678";
	private static final String SECRET = "client-secret-value";
	// 2026-11-03 01:02:03 UTC = 한국 시각 10:02:03
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-03T01:02:03Z"), ZoneOffset.UTC);

	private static final String TOKEN_RESPONSE = """
		{
			"access_token": "access-token-1",
			"token_type": "Bearer",
			"expires_in": 7776000,
			"scope": "oob",
			"client_use_code": "M202600001"
		}
		""";

	private static final PayoutAccountInquiry PERSONAL = new PayoutAccountInquiry(
		"004", ACCOUNT_NUMBER, PayoutAccountHolderType.PERSONAL, "950412", "김러너");
	private static final PayoutAccountInquiry BUSINESS = new PayoutAccountInquiry(
		"004", ACCOUNT_NUMBER, PayoutAccountHolderType.BUSINESS, "1234567890", "크루런 스포츠");

	@Test
	@DisplayName("2자 인증 토큰을 받아 계좌실명조회를 부르고, 은행이 맞다고 답하면 예금주 이름을 돌려준다")
	void verifyPersonal() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(TOKEN_URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
			.andExpect(content().formDataContains(Map.of(
				"client_id", "client-id-value",
				"client_secret", SECRET,
				"scope", "oob",
				"grant_type", "client_credentials")))
			.andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));
		server.expect(requestTo(REAL_NAME_URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token-1"))
			.andExpect(jsonPath("$.bank_tran_id", Matchers.matchesPattern("M202600001U[0-9A-Z]{9}")))
			.andExpect(jsonPath("$.bank_code_std").value("004"))
			.andExpect(jsonPath("$.account_num").value(ACCOUNT_NUMBER))
			.andExpect(jsonPath("$.account_holder_info_type").value(" "))
			.andExpect(jsonPath("$.account_holder_info").value("950412"))
			.andExpect(jsonPath("$.tran_dtime").value("20261103100203"))
			.andRespond(withSuccess(realNameResponse("A0000", "000", "김 러너"), MediaType.APPLICATION_JSON));

		String holderName = verifier(builder).verify(PERSONAL);

		assertThat(holderName).isEqualTo("김 러너");
		server.verify();
	}

	@Test
	@DisplayName("사업자 명의는 사업자번호 구분 코드로 조회하고, 토큰은 만료 전까지 다시 받지 않는다")
	void verifyBusinessReusesToken() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(once(), requestTo(TOKEN_URL))
			.andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));
		server.expect(requestTo(REAL_NAME_URL))
			.andExpect(jsonPath("$.account_holder_info_type").value("6"))
			.andExpect(jsonPath("$.account_holder_info").value("1234567890"))
			.andRespond(withSuccess(realNameResponse("A0000", "000", "크루런스포츠"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(REAL_NAME_URL))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token-1"))
			.andRespond(withSuccess(realNameResponse("A0000", "000", "크루런스포츠"), MediaType.APPLICATION_JSON));
		OpenBankingAccountVerifier verifier = verifier(builder);

		verifier.verify(BUSINESS);
		verifier.verify(BUSINESS);

		server.verify();
	}

	@Test
	@DisplayName("은행 응답 코드가 정상(000)이 아니면 예금주 확인 실패다")
	void bankRejects() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));
		server.expect(requestTo(REAL_NAME_URL))
			.andRespond(withSuccess(realNameResponse("A0000", "811", null), MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> verifier(builder).verify(BUSINESS))
			.isInstanceOf(SellerAccountNotVerifiedException.class);
	}

	@Test
	@DisplayName("오픈뱅킹 응답 코드가 정상(A0000)이 아니면 예금주 확인 실패다")
	void apiRejects() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));
		server.expect(requestTo(REAL_NAME_URL))
			.andRespond(withSuccess(realNameResponse("A0324", "", null), MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> verifier(builder).verify(PERSONAL))
			.isInstanceOf(SellerAccountNotVerifiedException.class);
	}

	@Test
	@DisplayName("토큰 발급이 서버 오류면 잠시 뒤 다시 시도로 바꾼다")
	void tokenServerError() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(TOKEN_URL)).andRespond(withServerError());

		assertThatThrownBy(() -> verifier(builder).verify(PERSONAL))
			.isInstanceOf(SellerAccountVerifyUnavailableException.class);
	}

	@Test
	@DisplayName("계좌실명조회가 시간 초과면 잠시 뒤 다시 시도로 바꾸고, 계좌번호·키는 로그에 남기지 않는다")
	void inquiryTimeout(CapturedOutput output) {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));
		server.expect(requestTo(REAL_NAME_URL)).andRespond(withException(new SocketTimeoutException("timeout")));

		assertThatThrownBy(() -> verifier(builder).verify(PERSONAL))
			.isInstanceOf(SellerAccountVerifyUnavailableException.class);
		assertThat(output).doesNotContain(ACCOUNT_NUMBER, SECRET, "access-token-1");
	}

	@Test
	@DisplayName("계좌실명조회가 서버 오류면 잠시 뒤 다시 시도로 바꾼다")
	void inquiryServerError() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));
		server.expect(requestTo(REAL_NAME_URL)).andRespond(withServerError());

		assertThatThrownBy(() -> verifier(builder).verify(PERSONAL))
			.isInstanceOf(SellerAccountVerifyUnavailableException.class);
	}

	@Test
	@DisplayName("계좌실명조회가 401이면 토큰을 버려, 다음 확인 때 토큰을 새로 받는다")
	void unauthorizedDropsToken() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));
		server.expect(requestTo(REAL_NAME_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
		server.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));
		server.expect(requestTo(REAL_NAME_URL))
			.andRespond(withSuccess(realNameResponse("A0000", "000", "김러너"), MediaType.APPLICATION_JSON));
		OpenBankingAccountVerifier verifier = verifier(builder);

		assertThatThrownBy(() -> verifier.verify(PERSONAL))
			.isInstanceOf(SellerAccountVerifyUnavailableException.class);
		assertThat(verifier.verify(PERSONAL)).isEqualTo("김러너");
		server.verify();
	}

	@Test
	@DisplayName("예금주 구분 코드 설정이 비어 있으면 추측해 보내지 않고 잠시 뒤 다시 시도로 바꾼다")
	void missingHolderTypeCode() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer.bindTo(builder).build();
		OpenBankingAccountVerifier verifier = new OpenBankingAccountVerifier(builder.build(),
			properties(" ", ""), CLOCK);

		assertThatThrownBy(() -> verifier.verify(BUSINESS))
			.isInstanceOf(SellerAccountVerifyUnavailableException.class);
	}

	private OpenBankingAccountVerifier verifier(RestClient.Builder builder) {
		return new OpenBankingAccountVerifier(builder.build(), properties(" ", "6"), CLOCK);
	}

	private static PayoutAccountProperties.OpenBanking properties(String birthType, String businessType) {
		return new PayoutAccountProperties.OpenBanking(BASE_URL, "client-id-value", SECRET, "M202600001",
			birthType, businessType, Duration.ofSeconds(3));
	}

	private static String realNameResponse(String rspCode, String bankRspCode, String holderName) {
		return """
			{
				"api_tran_id": "2ffd133a-d17a-431d-a6a5",
				"api_tran_dtm": "20261103100203123",
				"rsp_code": "%s",
				"rsp_message": "",
				"bank_tran_id": "M202600001U4BC34239Z",
				"bank_tran_date": "20261103",
				"bank_code_tran": "004",
				"bank_rsp_code": "%s",
				"bank_rsp_message": "",
				"bank_code_std": "004",
				"bank_name": "국민은행",
				"account_num": "1101230000678",
				"account_holder_info_type": " ",
				"account_holder_name": %s,
				"account_type": "1"
			}
			""".formatted(rspCode, bankRspCode, holderName == null ? "null" : "\"" + holderName + "\"");
	}
}
