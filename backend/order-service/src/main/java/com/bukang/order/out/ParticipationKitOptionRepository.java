package com.bukang.order.out;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.order.domain.ParticipationKitOption;

public interface ParticipationKitOptionRepository extends JpaRepository<ParticipationKitOption, Long> {
	List<ParticipationKitOption> findByParticipationIdOrderByIdAsc(long participationId);

	List<ParticipationKitOption> findByParticipationIdInOrderByIdAsc(Collection<Long> participationIds);
}
