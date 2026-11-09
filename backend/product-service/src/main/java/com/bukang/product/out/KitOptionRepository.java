package com.bukang.product.out;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.product.domain.KitOption;

public interface KitOptionRepository extends JpaRepository<KitOption, Long> {
	// 대회의 키트 옵션을 판매자가 등록한 순서(옵션 ID 순)로 읽는다. 대회 엔티티는 불러오지 않는다
	List<KitOption> findByRaceIdOrderById(long raceId);
}
