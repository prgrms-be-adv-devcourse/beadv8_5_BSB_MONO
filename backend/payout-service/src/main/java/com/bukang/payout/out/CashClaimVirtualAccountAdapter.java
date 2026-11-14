package com.bukang.payout.out;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 캐시 서비스의 청구 가상계좌 발급 내부 API를 HTTP로 부르는 어댑터 (BUK-88, 캐시 쪽 BUK-145)
 * POST {캐시 서비스}/internal/v1/cash/claims/{claimCode}/virtual-account {amount} → data.{bankName, accountNumber}
 *
 * 같은 청구 ID로 다시 불러도 캐시가 처음 발급한 계좌를 돌려주므로, 응답을 못 받았으면 다음 배치에서 그냥 다시 부른다.
 * 실패는 모두 예외로 던지고 청구 배치가 로그만 남긴다. 입금 기한은 캐시가 받지 않아 보내지 않는다 (가상계좌는 90일 유효)
 * 테스트 프로파일에서는 NoClaimVirtualAccountAdapter가 대신 뜬다
 */
@Component
@Profile("!test")
public class CashClaimVirtualAccountAdapter implements ClaimVirtualAccountPort {
	static final String PATH = "/internal/v1/cash/claims/{claimCode}/virtual-account";
	// 캐시가 PG를 부르고 답하므로 지급보다 읽기 시간을 조금 길게 둔다. 그래도 한 건이 배치를 오래 붙잡지 않게 한다
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

	private final RestClient restClient;

	@Autowired
	public CashClaimVirtualAccountAdapter(RestClient.Builder builder,
		@Value("${payout.services.cash}") String cashServiceUrl) {
		this(builder
			.baseUrl(cashServiceUrl)
			.requestFactory(requestFactory())
			.build());
	}

	// 테스트에서 MockRestServiceServer에 묶은 RestClient를 넣는다
	CashClaimVirtualAccountAdapter(RestClient restClient) {
		this.restClient = restClient;
	}

	@Override
	public Optional<ClaimVirtualAccount> issue(String claimCode, long amount, LocalDate dueDate) {
		IssueResponse response = restClient.post()
			.uri(PATH, claimCode)
			.contentType(MediaType.APPLICATION_JSON)
			.body(new IssueRequest(amount))
			.retrieve()
			.body(IssueResponse.class);
		return Optional.ofNullable(response)
			.map(IssueResponse::data)
			.map(data -> new ClaimVirtualAccount(data.bankName(), data.accountNumber()));
	}

	private static SimpleClientHttpRequestFactory requestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(CONNECT_TIMEOUT);
		factory.setReadTimeout(READ_TIMEOUT);
		return factory;
	}

	record IssueRequest(long amount) {
	}

	record IssueResponse(Account data) {
	}

	record Account(String bankName, String accountNumber) {
	}
}
