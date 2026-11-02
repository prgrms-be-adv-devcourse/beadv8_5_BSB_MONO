package com.bukang.order.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.order.domain.Participation;
import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.domain.ParticipationType;

import jakarta.persistence.LockModeType;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {
	// 같은 대회에 살아 있는 신청의 ID (uk_participation_active와 같은 기준). 잠그기 전에 엔티티를 읽지 않으려고 ID만 찾는다
	@Query("select p.id from Participation p where p.raceId = :raceId and p.memberId = :memberId "
		+ "and p.activeFlag = true")
	Optional<Long> findActiveIdByRaceIdAndMemberId(@Param("raceId") long raceId, @Param("memberId") long memberId);

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

	// [결제하기]가 같은 신청서로 동시에 들어오면 한 요청씩 처리해, 상품 서비스에 자리를 두 번 잡지 않게 한다
	// 결제·참가자 정보 수정도 이걸로 읽어, 서로 겹쳐도 한쪽이 바꾼 상태를 다른 쪽이 옛 값으로 덮지 않게 한다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Participation p where p.id = :id")
	Optional<Participation> findByIdForUpdate(@Param("id") long id);
}
