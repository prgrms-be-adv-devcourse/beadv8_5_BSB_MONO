package com.bukang.payout.out;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.payout.domain.exception.PayoutSellerAccountLookupUnavailableException;

/**
 * 회원 서비스에 판매자 정산 계좌 등록 여부를 묻는 어댑터 (BUK-63)
 * GET {회원 서비스}/internal/v1/member/sellers/{sellerId}/payout-account → RsData.data.registered (PR #292)
 * 회원 서비스가 판매자 신청 때 계좌실명조회를 통과한 계좌를 판매자 신청 행에 저장한다. 정산은 등록 여부만 받는다
 *
 * 응답이 비거나 실패하면(404 판매자 없음 포함) 예외를 던진다. 배치는 그 정산 내역을 실패로 남기고 되돌아보기(7일)로 따라잡는다
 * '없음'으로 바꾸면 계좌가 있는 판매자의 정산이 보류되고, '있음'으로 바꾸면 계좌 없는 판매자에게 지급되므로 어느 쪽으로도 삼키지 않는다
 */
@Component
@Profile("!test")
public class MemberSellerAccountAdapter implements SellerAccountPort {
	private static final String PATH = "/internal/v1/member/sellers/{sellerId}/payout-account";
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

	private final RestClient restClient;

	@Autowired
	public MemberSellerAccountAdapter(RestClient.Builder builder,
		@Value("${payout.services.member}") String memberServiceUrl) {
		// Boot가 주는 빌더는 주입마다 새로 만들어져(prototype) 다른 어댑터와 baseUrl이 섞이지 않는다
		this(builder
			.baseUrl(memberServiceUrl)
			.requestFactory(requestFactory())
			.build());
	}

	// 테스트에서 MockRestServiceServer에 묶은 RestClient를 넣는다
	MemberSellerAccountAdapter(RestClient restClient) {
		this.restClient = restClient;
	}

	@Override
	public boolean hasPayoutAccount(long sellerId) {
		RsData<PayoutAccountResponse> body;
		try {
			body = restClient.get()
				.uri(PATH, sellerId)
				.retrieve()
				.body(new ParameterizedTypeReference<>() {
				});
		} catch (RestClientException exception) {
			throw new PayoutSellerAccountLookupUnavailableException(sellerId, exception);
		}

		if (body == null || body.data() == null || body.data().registered() == null) {
			throw new PayoutSellerAccountLookupUnavailableException(sellerId,
				new IllegalStateException("회원 서비스의 정산 계좌 응답이 비어 있습니다."));
		}
		return body.data().registered();
	}

	private static SimpleClientHttpRequestFactory requestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(CONNECT_TIMEOUT);
		factory.setReadTimeout(READ_TIMEOUT);
		return factory;
	}

	// 회원 서비스 응답(SellerPayoutAccountDto)의 모양. 등록 여부만 받는다 (계좌번호 같은 개인정보는 정산이 받지 않는다)
	private record PayoutAccountResponse(Boolean registered) {
	}
}
