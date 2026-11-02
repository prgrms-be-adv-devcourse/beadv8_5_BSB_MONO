package com.bukang.member.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.member.domain.Seller;

import jakarta.persistence.LockModeType;

public interface SellerRepository extends JpaRepository<Seller, Long> {
	// 대기·승인 상태 신청이 있으면 다시 신청할 수 없다 (idx_member_seller_member)
	boolean existsByMemberIdAndStatusIn(long memberId, Collection<SellerStatus> statuses);

	// 가장 최근 신청. ID가 신청 순서대로 늘어난다
	Optional<Seller> findFirstByMemberIdOrderByIdDesc(long memberId);

	List<Seller> findAllByOrderByIdDesc();

	List<Seller> findAllByStatusOrderByIdDesc(SellerStatus status);

	// 심사할 신청 줄을 잠그고 읽는다. 관리자 두 명이 같은 신청을 동시에 심사하면 뒤 요청은 앞 요청이 끝난 뒤
	// 바뀐 상태(APPROVED·REJECTED)를 읽어 409가 된다 (이벤트가 두 번 나가지 않게)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Seller s where s.id = :id")
	Optional<Seller> findByIdForUpdate(@Param("id") long id);

	// 결과 확인을 남길 가장 최근 신청. 잠가서 같은 요청이 동시에 와도 처음 기록한 시각을 덮어쓰지 않게 한다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Seller s where s.member.id = :memberId order by s.id desc limit 1")
	Optional<Seller> findLatestByMemberIdForUpdate(@Param("memberId") long memberId);

	// 탈퇴할 때 반려할 대기 중 신청. 잠가서 같은 신청을 동시에 승인하면 뒤 요청이 409가 되게 한다 (PRO-217)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Seller s where s.member.id = :memberId and s.status = :status")
	List<Seller> findAllByMemberIdAndStatusForUpdate(@Param("memberId") long memberId,
		@Param("status") SellerStatus status);
}
