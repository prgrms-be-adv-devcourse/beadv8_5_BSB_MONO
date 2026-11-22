package com.bukang.product.out.file;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.common.global.rsdata.RsData;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 파일 서비스의 대표 이미지 주소 내부 API를 부른다 (GET /internal/v1/file/refs/Race/thumbnails?refIds=1,2,3)
 * 대표 이미지는 보조 정보라, 시간 초과 · 연결 실패 · 오류 응답이면 로그만 남기고 빈 Map을 돌려준다
 * (목록 전체를 503으로 만들면 이미지 때문에 대회를 못 찾게 된다)
 * 등록은 FileClientConfig가 한다 (product.clients.file.mode=http)
 */
@Slf4j
@RequiredArgsConstructor
public class HttpRaceThumbnailAdapter implements RaceThumbnailPort {
	static final String PATH = "/internal/v1/file/refs/Race/thumbnails?refIds={refIds}";
	private static final ParameterizedTypeReference<RsData<List<ThumbnailResponse>>> RESPONSE_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient restClient;

	@Override
	public Map<Long, String> findThumbnailUrls(List<Long> raceIds) {
		if (raceIds.isEmpty()) {
			return Map.of();
		}
		String refIds = raceIds.stream().map(String::valueOf).collect(Collectors.joining(","));
		RsData<List<ThumbnailResponse>> response;
		try {
			response = restClient.get()
				.uri(PATH, refIds)
				.retrieve()
				.body(RESPONSE_TYPE);
		} catch (RestClientException exception) {
			log.warn("파일 서비스에서 대회 대표 이미지 주소를 받지 못해 이미지 없이 돌려줍니다. 대회 수={}", raceIds.size(), exception);
			return Map.of();
		}
		if (response == null || response.data() == null) {
			log.warn("파일 서비스의 대표 이미지 주소 응답에 값이 없습니다. 대회 수={}", raceIds.size());
			return Map.of();
		}
		return response.data().stream()
			.filter(thumbnail -> thumbnail.refId() != null && thumbnail.url() != null)
			.collect(Collectors.toMap(ThumbnailResponse::refId, ThumbnailResponse::url, (first, second) -> first));
	}

	// 대표 이미지는 대상마다 1장이지만, 혹시 겹치면 먼저 온 주소를 쓴다 (위 toMap의 세 번째 인자)
	@JsonIgnoreProperties(ignoreUnknown = true)
	record ThumbnailResponse(Long refId, String url) {
	}
}
