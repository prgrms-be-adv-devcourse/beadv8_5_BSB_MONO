package com.bukang.member.out.payoutaccount;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.bukang.member.config.payoutaccount.PayoutAccountProperties;
import com.bukang.member.domain.PayoutAccountHolderType;
import com.bukang.member.domain.exception.SellerAccountNotVerifiedException;
import com.bukang.member.domain.exception.SellerAccountVerifyUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * 금융결제원 오픈뱅킹 계좌실명조회로 정산 계좌를 확인한다 (BE-031)
 * 2자 인증(Client Credentials, scope=oob)으로 받은 토큰으로 POST /v2.0/inquiry/real_name을 부른다
 * 계좌번호·생년월일·사업자번호·예금주 이름·키·토큰은 로그에 남기지 않는다. 실패 로그에는 HTTP 상태와 응답 코드만 남긴다
 */
@Slf4j
public class OpenBankingAccountVerifier implements PayoutAccountVerifier {
	private static final String TOKEN_PATH = "/oauth/2.0/token";
	private static final String REAL_NAME_PATH = "/v2.0/inquiry/real_name";
	private static final String SUCCESS_RSP_CODE = "A0000";
	private static final String SUCCESS_BANK_RSP_CODE = "000";
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter TRAN_DTIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private static final String TRAN_ID_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	private static final int TRAN_ID_SUFFIX_LENGTH = 9;
	// 만료 직전 토큰으로 부르다 실패하지 않게 만료 1분 전에 새로 받는다
	private static final long TOKEN_REFRESH_MARGIN_SECONDS = 60;

	private final RestClient restClient;
	private final PayoutAccountProperties.OpenBanking properties;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	private String accessToken;
	private Instant tokenExpiresAt;

	public OpenBankingAccountVerifier(RestClient restClient, PayoutAccountProperties.OpenBanking properties,
		Clock clock) {
		this.restClient = restClient;
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	public String verify(PayoutAccountInquiry inquiry) {
		String holderInfoType = holderInfoType(inquiry.holderType());
		// 공식 문서에서 확인하지 못한 값은 추측해 보내지 않는다. 설정이 비면 잠시 뒤 다시 시도로 안내한다
		if (holderInfoType.isEmpty() || properties.useCode().isBlank()) {
			log.warn("오픈뱅킹 이용기관 코드나 예금주 실명번호 구분 코드 설정이 비어 있어 계좌를 확인하지 못합니다. holderType={}",
				inquiry.holderType());
			throw new SellerAccountVerifyUnavailableException();
		}

		RealNameResponse response;
		try {
			response = restClient.post()
				.uri(REAL_NAME_PATH)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token())
				.contentType(MediaType.APPLICATION_JSON)
				.body(new RealNameRequest(bankTranId(), inquiry.bankCode(), inquiry.accountNumber(), holderInfoType,
					inquiry.holderInfo(), ZonedDateTime.ofInstant(clock.instant(), SEOUL).format(TRAN_DTIME)))
				.retrieve()
				.body(RealNameResponse.class);
		} catch (RestClientException exception) {
			throw unavailable("계좌실명조회", exception);
		}
		if (response == null) {
			log.warn("오픈뱅킹 계좌실명조회 응답이 비어 있습니다.");
			throw new SellerAccountVerifyUnavailableException();
		}
		if (!SUCCESS_RSP_CODE.equals(response.rspCode()) || !SUCCESS_BANK_RSP_CODE.equals(response.bankRspCode())) {
			log.info("오픈뱅킹 계좌실명조회 불일치: rsp_code={}, bank_rsp_code={}", response.rspCode(), response.bankRspCode());
			throw new SellerAccountNotVerifiedException();
		}
		return response.accountHolderName();
	}

	private String holderInfoType(PayoutAccountHolderType holderType) {
		return holderType == PayoutAccountHolderType.BUSINESS
			? properties.businessHolderType()
			: properties.birthHolderType();
	}

	// 토큰은 만료 전까지 다시 쓴다. 신청은 드물어 한 번에 하나만 받도록 메서드를 잠근다
	private synchronized String token() {
		Instant now = clock.instant();
		if (accessToken != null && now.isBefore(tokenExpiresAt)) {
			return accessToken;
		}
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("client_id", properties.clientId());
		form.add("client_secret", properties.clientSecret());
		form.add("scope", "oob");
		form.add("grant_type", "client_credentials");
		TokenResponse response;
		try {
			response = restClient.post()
				.uri(TOKEN_PATH)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(TokenResponse.class);
		} catch (RestClientException exception) {
			throw unavailable("토큰 발급", exception);
		}
		if (response == null || response.accessToken() == null) {
			log.warn("오픈뱅킹 토큰 발급 응답에 토큰이 없습니다.");
			throw new SellerAccountVerifyUnavailableException();
		}
		accessToken = response.accessToken();
		tokenExpiresAt = now.plusSeconds(Math.max(0, response.expiresIn() - TOKEN_REFRESH_MARGIN_SECONDS));
		return accessToken;
	}

	// 은행거래고유번호: 이용기관 코드 10자리 + U + 9자리 (공식 문서 예시 F123456789U4BC34239Z와 같은 모양)
	private String bankTranId() {
		StringBuilder builder = new StringBuilder(properties.useCode()).append('U');
		for (int i = 0; i < TRAN_ID_SUFFIX_LENGTH; i++) {
			builder.append(TRAN_ID_CHARS.charAt(random.nextInt(TRAN_ID_CHARS.length())));
		}
		return builder.toString();
	}

	// 예외 메시지에는 응답 본문이 들어갈 수 있어 남기지 않는다
	private static SellerAccountVerifyUnavailableException unavailable(String step, RestClientException exception) {
		if (exception instanceof RestClientResponseException responseException) {
			log.warn("오픈뱅킹 {} 실패: status={}", step, responseException.getStatusCode());
		} else {
			log.warn("오픈뱅킹 {} 실패: error={}", step, exception.getClass().getSimpleName());
		}
		return new SellerAccountVerifyUnavailableException();
	}

	record RealNameRequest(
		@JsonProperty("bank_tran_id") String bankTranId,
		@JsonProperty("bank_code_std") String bankCodeStd,
		@JsonProperty("account_num") String accountNum,
		@JsonProperty("account_holder_info_type") String accountHolderInfoType,
		@JsonProperty("account_holder_info") String accountHolderInfo,
		@JsonProperty("tran_dtime") String tranDtime
	) {
	}

	// 오픈뱅킹 응답에서 쓰는 칸만 읽고 나머지는 무시한다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record RealNameResponse(
		@JsonProperty("rsp_code") String rspCode,
		@JsonProperty("bank_rsp_code") String bankRspCode,
		@JsonProperty("account_holder_name") String accountHolderName
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TokenResponse(
		@JsonProperty("access_token") String accessToken,
		@JsonProperty("expires_in") long expiresIn
	) {
	}
}
