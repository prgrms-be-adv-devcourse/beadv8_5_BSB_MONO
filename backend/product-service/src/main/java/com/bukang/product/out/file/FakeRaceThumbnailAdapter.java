package com.bukang.product.out.file;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 가짜 어댑터: 파일 서비스를 띄우지 않고 개발할 때 대표 이미지 주소를 주지 않는다 (카드는 기본 그림)
 * product.clients.file.mode=fake라고 적었을 때만 쓴다. 운영(prod)은 http라 HttpRaceThumbnailAdapter가 파일 서비스에 묻는다
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "product.clients.file", name = "mode", havingValue = "fake")
public class FakeRaceThumbnailAdapter implements RaceThumbnailPort {
	public FakeRaceThumbnailAdapter() {
		log.warn("대회 대표 이미지 주소는 가짜 응답(FakeRaceThumbnailAdapter)을 씁니다. 목록에 이미지가 없습니다. "
			+ "실제 파일 서비스는 product.clients.file.mode=http");
	}

	@Override
	public Map<Long, String> findThumbnailUrls(List<Long> raceIds) {
		return Map.of();
	}
}
