package com.bukang.member.crew.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.member.crew.domain.Crew;
import com.bukang.member.crew.domain.CrewStatus;

public interface CrewRepository extends JpaRepository<Crew, Long> {
	// 없앤 크루는 없는 크루로 본다 (BE-029)
	Optional<Crew> findByIdAndStatus(long id, CrewStatus status);

	List<Crew> findAllByIdInAndStatus(Collection<Long> ids, CrewStatus status);

	default Optional<Crew> findActiveById(long id) {
		return findByIdAndStatus(id, CrewStatus.ACTIVE);
	}
}
