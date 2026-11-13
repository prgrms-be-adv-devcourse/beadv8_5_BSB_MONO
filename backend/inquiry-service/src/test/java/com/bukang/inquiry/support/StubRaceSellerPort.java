package com.bukang.inquiry.support;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.bukang.inquiry.domain.exception.InquiryExternalServiceUnavailableException;
import com.bukang.inquiry.out.race.RaceSellerPort;

/**
 * 테스트용 상품 서비스. 테스트가 정한 대회만 있고, 상품 서비스가 멈춘 상황도 만들 수 있다
 */
public class StubRaceSellerPort implements RaceSellerPort {
	private final Map<Long, Long> sellerByRace = new ConcurrentHashMap<>();
	private volatile boolean unavailable;

	@Override
	public Optional<Long> findSellerMemberId(long raceId) {
		if (unavailable) {
			throw new InquiryExternalServiceUnavailableException("상품 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.");
		}
		return Optional.ofNullable(sellerByRace.get(raceId));
	}

	public void race(long raceId, long sellerMemberId) {
		sellerByRace.put(raceId, sellerMemberId);
	}

	public void setUnavailable(boolean unavailable) {
		this.unavailable = unavailable;
	}

	public void clear() {
		sellerByRace.clear();
		unavailable = false;
	}
}
