package com.bukang.payout.out;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.payout.domain.PayoutStage;

/**
 * 캐시 서비스의 정산 지급 내부 API를 HTTP로 부르는 어댑터 (PRO-252)
 * POST {캐시 서비스}/internal/v1/cash/payouts/{payoutId} {sellerId, payoutStage, settlementAmount, feeAmount} → 2xx면 성공
 *
 * 캐시 서비스의 API는 김소희님이 만든다 (PRO-256, PR #198의 CashPayoutRequestDto). 필드 이름은 그쪽에 맞춘다
 * 응답 본문은 읽지 않는다. 같은 정산 ID로 다시 요청해도 캐시 서비스가 돈을 한 번만 옮기고 성공으로 답한다
 *
 * 결과를 둘로 나눈다. 지급 배치가 정산 내역을 되돌릴지(돈이 안 나감), 지급 중으로 둘지(모름)가 이것으로 갈린다
 * - 4xx: 캐시 서비스가 처리하지 않은 것이 확실하다 → PayoutTransferRejectedException
 * - 타임아웃 · 연결 실패 · 5xx: 캐시 서비스가 옮겼는지 모른다 → PayoutTransferUnknownException
 * 5xx를 '거절'로 보지 않는 이유: 캐시 서비스가 돈을 옮기고 커밋한 뒤 응답을 만들다 실패해도 5xx가 나올 수 있다
 *
 * 테스트 프로파일에서는 FakePayoutTransferAdapter가 대신 뜬다. dev(로컬)와 prod는 이 어댑터로 실제 캐시 서비스를 부른다
 */
@Component
@Profile("!test")
public class CashPayoutTransferAdapter implements PayoutTransferPort {
	static final String PATH = "/internal/v1/cash/payouts/{payoutId}";
	// 새벽 배치라 사용자를 기다리게 하지 않지만, 응답이 없을 때 한 건이 배치 전체를 붙잡지 않게 한다
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
	// 오류 본문을 예외 메시지에 남길 최대 길이. dev 오류 응답은 스택 트레이스를 통째로 담을 수 있다
	private static final int LOGGED_BODY_LIMIT = 300;

	private final RestClient restClient;

	@Autowired
	public CashPayoutTransferAdapter(@Value("${payout.services.cash}") String cashServiceUrl) {
		this(RestClient.builder()
			.baseUrl(cashServiceUrl)
			.requestFactory(requestFactory())
			.build());
	}

	// 테스트에서 MockRestServiceServer에 묶은 RestClient를 넣는다
	CashPayoutTransferAdapter(RestClient restClient) {
		this.restClient = restClient;
	}

	@Override
	public void transfer(PayoutTransfer transfer) {
		try {
			restClient.post()
				.uri(PATH, transfer.payoutId())
				.contentType(MediaType.APPLICATION_JSON)
				.body(TransferRequest.from(transfer))
				.retrieve()
				.toBodilessEntity();
		} catch (HttpClientErrorException exception) {
			throw new PayoutTransferRejectedException(
				"캐시 서비스가 정산금 이동을 거절했습니다. payoutId=%d, status=%s, body=%s".formatted(
					transfer.payoutId(), exception.getStatusCode(),
					abbreviate(exception.getResponseBodyAsString())), exception);
		} catch (RestClientException exception) {
			throw new PayoutTransferUnknownException(
				"캐시 서비스의 정산금 이동 결과를 확인하지 못했습니다. payoutId=%d, cause=%s".formatted(
					transfer.payoutId(), exception), exception);
		}
	}

	static String abbreviate(String body) {
		if (body == null || body.length() <= LOGGED_BODY_LIMIT) {
			return body;
		}
		return body.substring(0, LOGGED_BODY_LIMIT) + "…(" + body.length() + "자)";
	}

	private static SimpleClientHttpRequestFactory requestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(CONNECT_TIMEOUT);
		factory.setReadTimeout(READ_TIMEOUT);
		return factory;
	}

	// 캐시 서비스 요청 본문 (cash CashPayoutRequestDto). sellerId는 판매자 ID(판매자 신청서 ID)이고 회원 ID가 아니다
	// 금액은 정산 쪽 타입(long) 그대로 보낸다. JSON 숫자라 캐시 서비스의 Integer로 읽히고,
	// int 범위를 넘으면 캐시 서비스가 400으로 거절해 정산 예정으로 남는다 (금액 타입 맞추기는 PRO-253)
	record TransferRequest(long sellerId, PayoutStage payoutStage, long settlementAmount, long feeAmount) {
		static TransferRequest from(PayoutTransfer transfer) {
			return new TransferRequest(transfer.sellerId(), transfer.stage(), transfer.payoutAmount(),
				transfer.feeAmount());
		}
	}
}
