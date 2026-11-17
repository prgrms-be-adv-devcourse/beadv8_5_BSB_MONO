package com.bukang.payout.out;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.bukang.common.global.rsdata.RsData;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 상품 서비스의 대회 판매자 내부 API를 부른다 (GET /internal/v1/product/races/{raceId}/seller)
 * - 200: sellerMemberId (판매자 ID가 아니라 판매자의 회원 ID)
 * - 404: 없는 대회 → 빈 값
 * - 그 밖의 실패: RestClientException을 그대로 던진다. 알림은 잃어도 되므로 부른 쪽이 로그만 남기고 넘어간다
 */
@Component
@Profile("!test")
public class ProductRaceSellerAdapter implements RaceSellerPort {
	static final String PATH = "/internal/v1/product/races/{raceId}/seller";
	// 관리자 보류 요청이 정산 내역을 잠근 채 기다리므로 짧게 끊는다
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(2);

	private final RestClient restClient;

	@Autowired
	public ProductRaceSellerAdapter(RestClient.Builder builder,
		@Value("${payout.services.product}") String productServiceUrl) {
		this(builder
			.baseUrl(productServiceUrl)
			.requestFactory(requestFactory())
			.build());
	}

	// 테스트에서 MockRestServiceServer에 묶은 RestClient를 넣는다
	ProductRaceSellerAdapter(RestClient restClient) {
		this.restClient = restClient;
	}

	@Override
	public Optional<Long> findSellerMemberId(long raceId) {
		RsData<SellerMemberBody> body;
		try {
			body = restClient.get()
				.uri(PATH, raceId)
				.retrieve()
				.body(new ParameterizedTypeReference<>() {
				});
		} catch (HttpClientErrorException exception) {
			if (exception.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
				return Optional.empty();
			}
			throw exception;
		}
		if (body == null || body.data() == null || body.data().sellerMemberId() == null) {
			throw new IllegalStateException("상품 서비스의 대회 판매자 응답이 비어 있습니다. raceId=" + raceId);
		}
		return Optional.of(body.data().sellerMemberId());
	}

	private static SimpleClientHttpRequestFactory requestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(CONNECT_TIMEOUT);
		factory.setReadTimeout(READ_TIMEOUT);
		return factory;
	}

	// 상품 서비스 응답(RaceSellerMemberDto)의 모양
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record SellerMemberBody(Long sellerMemberId) {
	}
}
