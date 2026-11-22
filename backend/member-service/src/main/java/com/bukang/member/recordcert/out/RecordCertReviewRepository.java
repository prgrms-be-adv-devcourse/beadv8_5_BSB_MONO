package com.bukang.member.recordcert.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.bukang.member.recordcert.domain.RecordCert;
import com.bukang.member.recordcert.domain.RecordCertSubmission;
import com.bukang.member.recordcert.domain.SubmissionStatus;
import com.bukang.member.recordcert.dto.RecordCertReviewCountDto;

// 판매자 기록증 심사(BUK-276)의 조회 · 잠금. 소유는 제출에 저장된 판매자 회원 ID로만 본다(product를 부르지 않음)
public interface RecordCertReviewRepository extends Repository<RecordCertSubmission, Long> {
	Optional<RecordCertSubmission> findById(long id);

	// 심사 저장을 이 자리에서 밀어 넣어, 버전이 바뀌었으면(환불 닫기가 먼저 저장) UseCase가 409로 바꾼다
	void flush();

	// 대회 하나의 제출 전부, 최신순 (idx_record_cert_submission_race)
	List<RecordCertSubmission> findByRaceIdOrderByIdDesc(long raceId);

	// 대회별 건수. 많은 순, 같으면 최근 대회 순 (idx_record_cert_submission_seller)
	@Query("""
		select new com.bukang.member.recordcert.dto.RecordCertReviewCountDto$RaceCount(s.raceId, count(s))
		from RecordCertSubmission s
		where s.sellerMemberId = :sellerMemberId and s.status = :status
		group by s.raceId
		order by count(s) desc, s.raceId desc
		""")
	List<RecordCertReviewCountDto.RaceCount> countByRace(@Param("sellerMemberId") long sellerMemberId,
		@Param("status") SubmissionStatus status);

	@Query("select c from RecordCert c where c.id in :ids")
	List<RecordCert> findCertsByIdIn(@Param("ids") Collection<Long> ids);
}
