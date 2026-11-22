package com.bukang.product.out.file;

import java.util.List;
import java.util.Map;

/**
 * 대회 대표 이미지 보기 주소를 파일 서비스에서 받는다 (CRU-141)
 * 주소는 30분 뒤 만료되는 서명 주소라 저장하지 않고, 목록을 줄 때마다 대회 여러 개를 한 번에 받는다
 * 대표 이미지는 보조 정보라 받지 못해도 예외를 던지지 않는다. 빈 Map이면 카드는 기본 그림(색 포스터)을 보여 준다
 */
public interface RaceThumbnailPort {
	// 대회 ID → 대표 이미지 주소. 대표 이미지가 없거나 받지 못한 대회는 Map에 없다
	Map<Long, String> findThumbnailUrls(List<Long> raceIds);
}
