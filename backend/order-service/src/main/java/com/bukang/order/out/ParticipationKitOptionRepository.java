package com.bukang.order.out;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.order.domain.ParticipationKitOption;
import com.bukang.order.dto.KitOptionChoiceDto;

public interface ParticipationKitOptionRepository extends JpaRepository<ParticipationKitOption, Long> {
	List<ParticipationKitOption> findByParticipationIdOrderByIdAsc(long participationId);

	List<ParticipationKitOption> findByParticipationIdInOrderByIdAsc(Collection<Long> participationIds);

	// 참가 신청마다 고른 키트 옵션 사본 (고른 순). 목록 화면·내려받기가 한 번에 읽어 붙인다
	default Map<Long, List<KitOptionChoiceDto>> findChoicesByParticipationIds(Collection<Long> participationIds) {
		if (participationIds.isEmpty()) {
			return Map.of();
		}
		return findByParticipationIdInOrderByIdAsc(participationIds).stream()
			.collect(Collectors.groupingBy(ParticipationKitOption::getParticipationId,
				Collectors.mapping(KitOptionChoiceDto::from, Collectors.toList())));
	}
}
