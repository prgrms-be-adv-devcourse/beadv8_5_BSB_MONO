package com.bukang.queue.out;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.queue.domain.QueueRace;

import jakarta.persistence.LockModeType;

public interface QueueRaceRepository extends JpaRepository<QueueRace, Long> {
	// 대회 복제본 행을 잠그고 읽는다 (SELECT … FOR UPDATE). 대회 동기화가 쓴다
	// 재시도 리스너와 본 리스너가 같은 대회의 메시지를 동시에 처리하면 차례로 처리되어, 나중 메시지가 방금 커밋된 수정 일시와 비교한다
	// 잠그지 않고 읽으면 둘 다 같은 옛 값과 비교해 통과하고, 늦게 커밋한 쪽이 옛 메시지면 옛 값이 남는다
	// 행이 아직 없으면 잠글 행이 없어 둘 다 INSERT하고, 실패한 쪽이 재시도 토픽을 거쳐 이 조회로 다시 비교한다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from QueueRace r where r.id = :id")
	Optional<QueueRace> findByIdForUpdate(@Param("id") long id);
}
