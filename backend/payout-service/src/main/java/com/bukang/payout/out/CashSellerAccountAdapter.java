package com.bukang.payout.out;

import java.time.Duration;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.bukang.common.global.rsdata.RsData;

/**
 * 캐시 서비스에 판매자 정산 계좌 등록 여부를 묻는 어댑터 (PRO-224)
 * GET {캐시 서비스}/internal/v1/cash/sellers/{sellerId}/payout-account → RsData.data.registered
 *
 * 캐시 서비스에는 아직 이 API도, 판매자 정산 계좌도 없다. 경로와 응답 모양을 가정하고 먼저 둔다
 * 그래서 빈으로 등록하지 않는다(@Component 없음). 지금은 모든 프로파일에서 NoSellerAccountAdapter가 '있음'으로 답한다
 * 캐시 서비스에 API가 생기면 이 클래스를 빈으로 등록하고 NoSellerAccountAdapter를 운영에서 뺀다
 * 판매자 ID로 묻는다. 캐시 서비스는 지금 회원 ID로 지갑을 찾으므로, 판매자 ID를 알게 되는 것도 그쪽 작업에 포함된다
 *
 * 응답이 비거나 실패하면 예외를 던진다. 배치는 그 정산 내역을 실패로 남기고 되돌아보기(7일)로 따라잡는다
 * '없음'으로 바꾸면 계좌가 있는 판매자의 정산이 보류되고, '있음'으로 바꾸면 계좌 없는 판매자에게 지급되므로 어느 쪽으로도 삼키지 않는다
 */
public class CashSellerAccountAdapter implements SellerAccountPort {
	private static final String PATH = "/internal/v1/cash/sellers/{sellerId}/payout-account";
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

	private final RestClient restClient;

	public CashSellerAccountAdapter(String cashServiceUrl) {
		this(RestClient.builder()
			.baseUrl(cashServiceUrl)
			.requestFactory(requestFactory())
			.build());
	}

	// 테스트에서 MockRestServiceServer에 묶은 RestClient를 넣는다
	CashSellerAccountAdapter(RestClient restClient) {
		this.restClient = restClient;
	}

	@Override
	public boolean hasPayoutAccount(int sellerId) {
		RsData<PayoutAccountResponse> body = restClient.get()
			.uri(PATH, sellerId)
			.retrieve()
			.body(new ParameterizedTypeReference<>() {
			});

		if (body == null || body.data() == null || body.data().registered() == null) {
			throw new IllegalStateException("캐시 서비스의 정산 계좌 응답이 비어 있습니다. sellerId=%d".formatted(sellerId));
		}
		return body.data().registered();
	}

	private static SimpleClientHttpRequestFactory requestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(CONNECT_TIMEOUT);
		factory.setReadTimeout(READ_TIMEOUT);
		return factory;
	}

	// 캐시 서비스 응답의 가정한 모양. 등록 여부만 받는다 (계좌번호 같은 개인정보는 정산이 받지 않는다)
	private record PayoutAccountResponse(Boolean registered) {
	}
}
