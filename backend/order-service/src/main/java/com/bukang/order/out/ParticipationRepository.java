package com.bukang.order.out;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.order.domain.Participation;
import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.domain.ParticipationType;

import jakarta.persistence.LockModeType;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {
	/**
	 * [결제하기]가 잠그기 전에 거르는 값 (BUK-213). 엔티티를 올리지 않아 뒤의 잠금 조회가 옛 상태를 돌려받지 않는다
	 * 바깥 트랜잭션 없이 불려도 연결을 바로 돌려주도록 짧은 트랜잭션으로 읽는다
	 */
	@Transactional(readOnly = true)
	@Query("select new com.bukang.order.out.ParticipationRepository$CreateTarget(p.memberId, p.raceCourseId, "
		+ "p.participationType, p.status, p.paymentDueAt) from Participation p where p.id = :id")
	Optional<CreateTarget> findCreateTarget(@Param("id") long id);

	record CreateTarget(long memberId, long raceCourseId, ParticipationType participationType,
		ParticipationStatus status, LocalDateTime paymentDueAt) {
	}

	/**
	 * [결제하기]가 상품에 자리를 잡으려다 주문을 만들지 못한 채 기한이 지난 개인 신청서 (BUK-213)
	 * 확보 전(PENDING)인데 결제 기한 칸이 찼으면 자리를 잡으러 갔던 신청서다 (Participation.markHoldRequested)
	 */
	@Query("""
		select p.id from Participation p
		where p.participationType = com.bukang.order.domain.ParticipationType.INDIVIDUAL
		and p.status = com.bukang.order.domain.ParticipationStatus.PENDING and p.paymentDueAt <= :now
		and p.id > :afterId
		order by p.id
		""")
	List<Long> findAbandonedHoldIds(@Param("now") LocalDateTime now, @Param("afterId") long afterId,
		Pageable pageable);

	// 같은 대회에 살아 있는 신청의 ID (uk_participation_active와 같은 기준). 잠그기 전에 엔티티를 읽지 않으려고 ID만 찾는다
	@Query("select p.id from Participation p where p.raceId = :raceId and p.memberId = :memberId "
		+ "and p.activeFlag = true")
	Optional<Long> findActiveIdByRaceIdAndMemberId(@Param("raceId") long raceId, @Param("memberId") long memberId);

	// 파티에서 나갈 때 끝낼 그 사람의 파티원 신청서
	@Query("select p.id from Participation p where p.partyId = :partyId and p.memberId = :memberId "
		+ "and p.activeFlag = true")
	Optional<Long> findActiveIdByPartyIdAndMemberId(@Param("partyId") long partyId, @Param("memberId") long memberId);

	// 파티에서 결제를 마친(참가 확정) 신청서 수와 내가 결제했는지 (내 파티 상태, BUK-91)
	long countByPartyIdAndStatus(long partyId, ParticipationStatus status);

	boolean existsByPartyIdAndMemberIdAndStatus(long partyId, long memberId, ParticipationStatus status);

	// 다음 신청 때 채울 최근 신청 (idx_participation_member 인덱스). 상태와 상관없이 마지막으로 쓴 정보를 쓴다
	Optional<Participation> findFirstByMemberIdOrderByIdDesc(long memberId);

	// 배송지를 적은 최근 신청. 마지막 신청이 키트 없는 종목이라 배송지가 비어 있을 때 다음 신청서에 채울 배송지를 찾는다
	Optional<Participation> findFirstByMemberIdAndParticipantShippingAddressAddressIsNotNullOrderByIdDesc(
		long memberId);

	// 티셔츠 사이즈를 고른 최근 신청 (티셔츠가 없는 종목은 사이즈를 남기지 않으므로 다음 신청서에 채울 때 쓴다)
	Optional<Participation> findFirstByMemberIdAndParticipantTshirtSizeIsNotNullOrderByIdDesc(long memberId);

	// 장바구니 종목으로 저장한 개인 신청서 (idx_participation_member 인덱스). 한 대회에 살아 있는 신청은 1건이라 종목마다 많아야 1건이다
	List<Participation> findByMemberIdAndParticipationTypeAndStatusAndRaceCourseIdIn(long memberId,
		ParticipationType participationType, ParticipationStatus status, Collection<Long> raceCourseIds);

	// 파티 조회 (BUK-39): 파티원 신청서. 나갔다 다시 합류하면 한 사람에 여러 장이라 부르는 쪽이 가장 최근 것을 고른다
	List<Participation> findByPartyIdIn(Collection<Long> partyIds);

	// 파티 신청 (BUK-37): 파티원 신청서를 모두 잠가 읽는다. 파티 → 파티원 → 참가 신청 순으로 잠근다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Participation p where p.partyId = :partyId and p.activeFlag = true")
	List<Participation> findActiveByPartyIdForUpdate(@Param("partyId") long partyId);

	// [결제하기]가 같은 신청서로 동시에 들어오면 한 요청씩 처리해, 상품 서비스에 자리를 두 번 잡지 않게 한다
	// 결제·참가자 정보 수정도 이걸로 읽어, 서로 겹쳐도 한쪽이 바꾼 상태를 다른 쪽이 옛 값으로 덮지 않게 한다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Participation p where p.id = :id")
	Optional<Participation> findByIdForUpdate(@Param("id") long id);
}
