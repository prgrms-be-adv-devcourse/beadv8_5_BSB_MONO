package com.bukang.order.out;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.order.domain.KitOptionSale;

import jakarta.persistence.LockModeType;

public interface KitOptionSaleRepository extends JpaRepository<KitOptionSale, Long> {
	// 판매 수 줄을 잠근다. 같은 옵션의 동시 결제·취소는 이 트랜잭션이 끝날 때까지 기다린 뒤 바뀐 판매 수를 읽는다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from KitOptionSale s where s.id = :kitOptionId")
	Optional<KitOptionSale> findByIdForUpdate(@Param("kitOptionId") long kitOptionId);

	// 여러 줄을 옵션 ID 오름차순으로 잠근다 (결제와 같은 순서)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from KitOptionSale s where s.id in :kitOptionIds order by s.id")
	List<KitOptionSale> findAllByIdForUpdate(@Param("kitOptionIds") Collection<Long> kitOptionIds);

	List<KitOptionSale> findByRaceId(long raceId);

	// 판매 수 줄(0)을 만든다. 이미 있으면 기본 키 중복으로 실패한다
	// save는 ID를 정한 엔티티를 merge로 저장해, 다른 결제가 막 만든 줄을 판매 수 0으로 덮어쓸 수 있어 쓰지 않는다
	@Modifying
	@Query(nativeQuery = true, value = "insert into order_kit_option_sale"
		+ " (kit_option_id, race_id, sold, create_date, modify_date) values (:kitOptionId, :raceId, 0, :now, :now)")
	void insertEmpty(@Param("kitOptionId") long kitOptionId, @Param("raceId") long raceId,
		@Param("now") LocalDateTime now);
}
