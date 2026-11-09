package com.bukang.order.out.kit;

import java.util.List;
import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.out.InternalHttpClients;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 상품 서비스(B)의 키트 옵션 목록을 묻는다 (GET /api/v1/product/races/{raceId}/kit-options, 호빈님 #359)
 * 내부 경로가 없어 공개 경로를 게이트웨이를 거치지 않고 상품 서비스 주소(order.clients.product.base-url)로 바로 부른다.
 * 옵션이 없는 대회는 빈 목록으로 온다. 회원 ID 헤더 없이 부르므로 취소된 대회는 404로 오고, 이때는 빈 값을 돌려
 * 부르는 쪽이 취소인지 계약 어긋남인지 가린다 (KitOptionPort)
 * 응답이 없거나 형식이 다르면 판정할 수 없으므로 503으로 다시 시도하게 한다
 * 등록은 ProductClientConfig가 한다 (order.clients.product.mode=http)
 */
@Slf4j
@RequiredArgsConstructor
public class HttpKitOptionAdapter implements KitOptionPort {
	static final String PATH = "/api/v1/product/races/{raceId}/kit-options";
	static final String UNAVAILABLE_MESSAGE = "상품 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.";

	private static final ParameterizedTypeReference<RsData<Body>> RESPONSE_TYPE = new ParameterizedTypeReference<>() {
	};

	private final RestClient restClient;

	@Override
	public Optional<List<KitOption>> findKitOptions(long raceId) {
		RsData<Body> response;
		try {
			response = restClient.get()
				.uri(PATH, raceId)
				.retrieve()
				.body(RESPONSE_TYPE);
		} catch (HttpClientErrorException exception) {
			if (exception.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
				return Optional.empty();
			}
			log.error("상품 서비스가 키트 옵션 요청을 거절했습니다. 계약(경로)을 확인해야 합니다. status={}, raceId={}, body={}",
				exception.getStatusCode(), raceId, InternalHttpClients.abbreviate(exception.getResponseBodyAsString()));
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		} catch (RestClientException exception) {
			log.warn("상품 서비스에 키트 옵션을 묻지 못했습니다. raceId={}, cause={}", raceId, exception.toString());
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		Body body = response == null ? null : response.data();
		if (body == null || !body.isComplete()) {
			log.error("상품 서비스의 키트 옵션 응답 형식이 계약과 다릅니다. raceId={}, response={}", raceId, response);
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		return Optional.of(body.kitOptions().stream()
			.flatMap(group -> group.values().stream()
				.map(value -> new KitOption(value.kitOptionId(), group.name(), value.value(), value.stock())))
			.toList());
	}

	// 상품 KitOptionListResponseDto. 옵션 이름마다 값 목록으로 묶여 온다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Body(List<Group> kitOptions) {
		boolean isComplete() {
			return kitOptions != null && kitOptions.stream().allMatch(Group::isComplete);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Group(String name, List<Value> values) {
		boolean isComplete() {
			return name != null && values != null
				&& values.stream().allMatch(value -> value.kitOptionId() != null && value.value() != null);
		}
	}

	// stock은 null일 수 있다 (맞춤 제작)
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Value(Long kitOptionId, String value, Integer stock) {
	}
}
