package com.bukang.member.out.identity;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import com.bukang.member.domain.exception.IdentityVerificationFailedException;
import com.bukang.member.domain.exception.IdentityVerificationUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 포트원 V2 REST API로 본인인증 결과를 조회한다 (BUK-33)
 * API Secret은 로그에 남기지 않는다. 실패 로그에는 인증 ID와 HTTP 상태만 남긴다
 */
@Slf4j
public class HttpPortOneIdentityClient implements PortOneIdentityClient {
	static final String NOT_FOUND_MESSAGE = "본인인증 결과를 찾지 못했습니다. 본인인증을 다시 해주세요.";
	static final String UNAVAILABLE_MESSAGE = "본인인증 결과를 확인하지 못했습니다. 잠시 후 다시 시도해주세요.";

	private final RestClient restClient;
	private final String apiSecret;
	private final String storeId;

	public HttpPortOneIdentityClient(RestClient restClient, String apiSecret, String storeId) {
		this.restClient = restClient;
		this.apiSecret = apiSecret;
		this.storeId = storeId;
	}

	@Override
	public PortOneIdentityVerification find(String identityVerificationId) {
		if (apiSecret.isBlank()) {
			log.warn("포트원 API Secret이 없어 본인인증 결과를 조회하지 못합니다.");
			throw new IdentityVerificationUnavailableException(UNAVAILABLE_MESSAGE);
		}
		try {
			Response response = restClient.get()
				.uri(builder -> uri(builder, identityVerificationId))
				.header(HttpHeaders.AUTHORIZATION, "PortOne " + apiSecret)
				.retrieve()
				.body(Response.class);
			if (response == null) {
				throw new IdentityVerificationUnavailableException(UNAVAILABLE_MESSAGE);
			}
			return response.toResult();
		} catch (RestClientResponseException exception) {
			if (exception.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
				throw new IdentityVerificationFailedException(NOT_FOUND_MESSAGE);
			}
			log.warn("포트원 본인인증 조회 실패: id={}, status={}", identityVerificationId, exception.getStatusCode());
			throw new IdentityVerificationUnavailableException(UNAVAILABLE_MESSAGE);
		} catch (RestClientException exception) {
			log.warn("포트원 본인인증 조회 실패: id={}, error={}", identityVerificationId, exception.getClass().getSimpleName());
			throw new IdentityVerificationUnavailableException(UNAVAILABLE_MESSAGE);
		}
	}

	private URI uri(UriBuilder builder, String identityVerificationId) {
		builder.path("/identity-verifications/{id}");
		if (!storeId.isBlank()) {
			builder.queryParam("storeId", storeId);
		}
		return builder.build(identityVerificationId);
	}

	// 포트원 응답에서 쓰는 칸만 읽고 나머지는 무시한다 (포트원이 칸을 더해도 깨지지 않게)
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Response(String id, String status, VerifiedCustomer verifiedCustomer, Instant verifiedAt) {
		PortOneIdentityVerification toResult() {
			VerifiedCustomer customer = verifiedCustomer == null
				? new VerifiedCustomer(null, null, null, null)
				: verifiedCustomer;
			return new PortOneIdentityVerification(id, status, customer.name(), customer.birthDate(),
				customer.phoneNumber(), customer.ci(), verifiedAt);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record VerifiedCustomer(String name, LocalDate birthDate, String phoneNumber, String ci) {
	}
}
