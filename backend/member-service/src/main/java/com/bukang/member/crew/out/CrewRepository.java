package com.bukang.member.crew.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.member.crew.domain.Crew;
import com.bukang.member.crew.domain.CrewStatus;

import jakarta.persistence.LockModeType;

public interface CrewRepository extends JpaRepository<Crew, Long> {
	// 없앤 크루는 없는 크루로 본다 (BE-029)
	Optional<Crew> findByIdAndStatus(long id, CrewStatus status);

	List<Crew> findAllByIdInAndStatus(Collection<Long> ids, CrewStatus status);

	default Optional<Crew> findActiveById(long id) {
		return findByIdAndStatus(id, CrewStatus.ACTIVE);
	}

	// 크루원 구성을 바꾸는 요청(가입, 탈퇴, 내보내기, 크루장 넘기기, 없애기, 이름 바꾸기)은 크루 행을 먼저 잠가 차례로 처리한다.
	// 잠금 읽기라 다른 요청이 커밋한 최신 상태를 보고, 뒤이은 일반 읽기도 잠금을 얻은 뒤의 값을 본다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Crew c where c.id = :id and c.status = com.bukang.member.crew.domain.CrewStatus.ACTIVE")
	Optional<Crew> findActiveByIdForUpdate(@Param("id") long id);
}
