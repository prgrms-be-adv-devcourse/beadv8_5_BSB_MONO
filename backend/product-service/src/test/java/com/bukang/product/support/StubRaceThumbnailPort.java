package com.bukang.product.support;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.bukang.product.out.file.RaceThumbnailPort;

/**
 * 테스트용 파일 서비스. 테스트가 정한 대회에만 대표 이미지 주소가 있다 (CRU-141)
 */
public class StubRaceThumbnailPort implements RaceThumbnailPort {
	private final Map<Long, String> urls = new ConcurrentHashMap<>();

	@Override
	public Map<Long, String> findThumbnailUrls(List<Long> raceIds) {
		return raceIds.stream()
			.filter(urls::containsKey)
			.collect(Collectors.toMap(raceId -> raceId, urls::get));
	}

	public void thumbnail(long raceId, String url) {
		urls.put(raceId, url);
	}

	public void reset() {
		urls.clear();
	}
}
