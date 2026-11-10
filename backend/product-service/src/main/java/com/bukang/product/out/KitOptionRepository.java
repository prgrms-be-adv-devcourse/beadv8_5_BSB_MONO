package com.bukang.product.out;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.product.domain.KitOption;

public interface KitOptionRepository extends JpaRepository<KitOption, Long> {
	// 대회의 키트 옵션을 판매자가 등록한 순서(옵션 ID 순)로 읽는다. 대회 엔티티는 불러오지 않는다
	List<KitOption> findByRaceIdOrderById(long raceId);

	// 대회에 키트 옵션이 이미 등록되어 있는지 (등록 API는 옵션이 없는 대회만 받는다)
	boolean existsByRaceId(long raceId);
}
