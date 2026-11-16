package com.bukang.payout.out;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.seller.domain.SellerType;

/**
 * 회원 서비스에 판매자 사업자 정보를 묻는 어댑터 (BUK-246)
 * GET {회원 서비스}/internal/v1/member/sellers/{sellerId}/business-info → RsData.data {sellerType, businessName, ...}
 * 배치용이라 기다리는 사람이 없어 판매자 화면용 어댑터보다 길게 기다린다. 실패는 예외 그대로 던져 발행 실패로 남긴다
 */
@Component
@Profile("!test")
public class MemberSellerBusinessAdapter implements SellerBusinessPort {
	private static final String PATH = "/internal/v1/member/sellers/{sellerId}/business-info";
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

	private final RestClient restClient;

	@Autowired
	public MemberSellerBusinessAdapter(RestClient.Builder builder,
		@Value("${payout.services.member}") String memberServiceUrl) {
		this(builder
			.baseUrl(memberServiceUrl)
			.requestFactory(requestFactory())
			.build());
	}

	MemberSellerBusinessAdapter(RestClient restClient) {
		this.restClient = restClient;
	}

	@Override
	public SellerBusiness find(long sellerId) {
		RsData<BusinessInfoResponse> body = restClient.get()
			.uri(PATH, sellerId)
			.retrieve()
			.body(new ParameterizedTypeReference<>() {
			});
		if (body == null || body.data() == null || body.data().sellerType() == null) {
			throw new IllegalStateException("회원 서비스의 판매자 사업자 정보 응답이 비어 있습니다. sellerId=" + sellerId);
		}
		BusinessInfoResponse data = body.data();
		return new SellerBusiness(data.sellerType(), data.businessName(), data.businessNumber(),
			data.representativeName());
	}

	private static SimpleClientHttpRequestFactory requestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(CONNECT_TIMEOUT);
		factory.setReadTimeout(READ_TIMEOUT);
		return factory;
	}

	// 회원 서비스 응답(SellerBusinessInfoDto)의 모양
	private record BusinessInfoResponse(SellerType sellerType, String businessName, String businessNumber,
		String representativeName) {
	}
}
