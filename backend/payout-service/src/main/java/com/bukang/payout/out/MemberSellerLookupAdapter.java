package com.bukang.payout.out;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.payout.domain.exception.PayoutSellerLookupUnavailableException;

import lombok.extern.slf4j.Slf4j;

/**
 * 회원 서비스의 내부 API로 회원의 판매자 신청을 묻는 어댑터 (판매자 정산 조회용, 임시 방식)
 * GET {회원 서비스}/internal/v1/member/members/{memberId}/seller → RsData.data {sellerId, status}
 *
 * - 404: 존재하지 않거나 탈퇴한 회원 → 빈 값
 * - 그 밖의 실패(타임아웃 · 연결 실패 · 5xx · 다른 4xx · 빈 응답): 판매자인지 알 수 없다 → 503
 * 모르는 채로 빈 목록을 주면 판매자가 "정산이 없다"로 잘못 읽으므로, 실패를 삼키지 않는다
 *
 * 판매자 화면을 열 때마다 부르므로 기다리는 사람이 있다. 그래서 정산 배치용 어댑터보다 짧게 끊는다
 * 판매자 복제본(queuing.seller.changed)을 두는 방식으로 바꾸면 이 어댑터를 빼고 SellerLookupPort의 다른 구현을 붙인다
 */
@Slf4j
@Component
public class MemberSellerLookupAdapter implements SellerLookupPort {
	static final String PATH = "/internal/v1/member/members/{memberId}/seller";
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(2);

	private final RestClient restClient;

	@Autowired
	public MemberSellerLookupAdapter(@Value("${payout.services.member}") String memberServiceUrl) {
		this(RestClient.builder()
			.baseUrl(memberServiceUrl)
			.requestFactory(requestFactory())
			.build());
	}

	// 테스트에서 MockRestServiceServer에 묶은 RestClient를 넣는다
	MemberSellerLookupAdapter(RestClient restClient) {
		this.restClient = restClient;
	}

	@Override
	public Optional<SellerOfMember> findSellerOf(long memberId) {
		RsData<MemberSellerResponse> body;
		try {
			body = restClient.get()
				.uri(PATH, memberId)
				.retrieve()
				.body(new ParameterizedTypeReference<>() {
				});
		} catch (HttpClientErrorException exception) {
			if (exception.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
				return Optional.empty();
			}
			log.error("회원 서비스가 판매자 조회를 거절했습니다. memberId={}, status={}", memberId,
				exception.getStatusCode(), exception);
			throw new PayoutSellerLookupUnavailableException();
		} catch (RestClientException exception) {
			log.error("회원 서비스에서 판매자 정보를 확인하지 못했습니다. memberId={}", memberId, exception);
			throw new PayoutSellerLookupUnavailableException();
		}

		if (body == null || body.data() == null) {
			log.error("회원 서비스의 판매자 조회 응답이 비어 있습니다. memberId={}", memberId);
			throw new PayoutSellerLookupUnavailableException();
		}
		return Optional.of(new SellerOfMember(body.data().sellerId(), body.data().status()));
	}

	private static SimpleClientHttpRequestFactory requestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(CONNECT_TIMEOUT);
		factory.setReadTimeout(READ_TIMEOUT);
		return factory;
	}

	// 회원 서비스 응답(MemberSellerDto)의 모양. 포트의 SellerOfMember가 응답 형식에 묶이지 않도록 따로 둔다
	private record MemberSellerResponse(Long sellerId, SellerStatus status) {
	}
}
