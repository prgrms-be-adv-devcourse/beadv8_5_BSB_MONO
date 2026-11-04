package com.bukang.member.out.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bukang.member.domain.exception.IdentityVerificationFailedException;
import com.bukang.member.domain.exception.IdentityVerificationUnavailableException;

class HttpPortOneIdentityClientTest {
	private static final String BASE_URL = "https://api.portone.io";
	private static final String ID = "crewrun-iv-1";

	// 포트원 V2 OpenAPI의 VerifiedIdentityVerification 모양. 쓰지 않는 칸(pgRawResponse 등)도 들어 있다
	private static final String VERIFIED_RESPONSE = """
		{
			"status": "VERIFIED",
			"id": "crewrun-iv-1",
			"channel": { "type": "TEST", "pgProvider": "INICIS_UNIFIED", "pgMerchantId": "MIIiasTest" },
			"verifiedCustomer": {
				"name": "김러너",
				"phoneNumber": "01012345678",
				"birthDate": "1995-04-12",
				"gender": "FEMALE",
				"isForeigner": false,
				"ci": "ci-value"
			},
			"requestedAt": "2026-11-02T02:59:00Z",
			"verifiedAt": "2026-11-02T03:00:00Z",
			"pgTxId": "tx",
			"pgRawResponse": "{}",
			"version": "V2"
		}
		""";

	@Test
	@DisplayName("API Secret과 상점 아이디로 조회하고, 인증 완료 결과의 고객 정보를 읽는다")
	void findVerified() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(BASE_URL + "/identity-verifications/" + ID + "?storeId=store-1"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "PortOne secret"))
			.andRespond(withSuccess(VERIFIED_RESPONSE, MediaType.APPLICATION_JSON));

		HttpPortOneIdentityClient client = new HttpPortOneIdentityClient(builder.build(), "secret", "store-1");
		PortOneIdentityVerification result = client.find(ID);

		assertThat(result).isEqualTo(new PortOneIdentityVerification(ID, "VERIFIED", "김러너",
			LocalDate.of(1995, 4, 12), "01012345678", "ci-value", Instant.parse("2026-11-02T03:00:00Z")));
		server.verify();
	}

	@Test
	@DisplayName("포트원에 없는 인증 ID(404)는 인증 실패, 그 밖의 실패는 잠시 뒤 다시 시도로 바꾼다")
	void failures() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(BASE_URL + "/identity-verifications/" + ID))
			.andRespond(withStatus(HttpStatus.NOT_FOUND));
		server.expect(requestTo(BASE_URL + "/identity-verifications/" + ID)).andRespond(withServerError());
		HttpPortOneIdentityClient client = new HttpPortOneIdentityClient(builder.build(), "secret", "");

		assertThatThrownBy(() -> client.find(ID)).isInstanceOf(IdentityVerificationFailedException.class);
		assertThatThrownBy(() -> client.find(ID)).isInstanceOf(IdentityVerificationUnavailableException.class);
		server.verify();
	}

	@Test
	@DisplayName("API Secret이 없으면 포트원을 부르지 않고 잠시 뒤 다시 시도로 답한다")
	void blankSecret() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

		assertThatThrownBy(() -> new HttpPortOneIdentityClient(builder.build(), "", "").find(ID))
			.isInstanceOf(IdentityVerificationUnavailableException.class);
		server.verify();
	}
}
