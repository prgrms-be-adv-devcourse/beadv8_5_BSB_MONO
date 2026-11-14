package com.bukang.order.out;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.order.domain.ParticipationKitOption;

public interface ParticipationKitOptionRepository extends JpaRepository<ParticipationKitOption, Long> {
	List<ParticipationKitOption> findByParticipationIdOrderByIdAsc(long participationId);

	// C안 시제품: 결제 중을 되돌릴 때 미리 남긴 옵션 사본을 지운다
	void deleteByParticipationId(long participationId);

	List<ParticipationKitOption> findByParticipationIdInOrderByIdAsc(Collection<Long> participationIds);
}
