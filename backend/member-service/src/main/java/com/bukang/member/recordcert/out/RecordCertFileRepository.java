package com.bukang.member.recordcert.out;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.bukang.member.recordcert.domain.RecordCert;
import com.bukang.member.recordcert.domain.SubmissionStatus;

// 기록증 사진 삭제(BUK-278)에 쓰는 조회 · 표시. 같은 사진(file_id)을 쓰는 기록증이 여럿일 수 있어 사진 단위로 본다
public interface RecordCertFileRepository extends Repository<RecordCert, Long> {
	// 아직 지우지 않은 사진 중, 그 사진을 쓴 제출이 모두 끝났고 끝난 지 cutoff보다 오래된 것
	@Query("""
		select distinct c.fileId from RecordCert c
		where c.fileDeletedAt is null
			and exists (select 1 from RecordCertSubmission s where s.recordCertId = c.id)
			and not exists (
				select 1 from RecordCertSubmission s2, RecordCert c2
				where s2.recordCertId = c2.id and c2.fileId = c.fileId
					and (s2.status in :inProgress or s2.updatedAt >= :cutoff))
		order by c.fileId
		""")
	List<Long> findDeletableFileIds(@Param("inProgress") Collection<SubmissionStatus> inProgress,
		@Param("cutoff") LocalDateTime cutoff, Limit limit);

	@Modifying
	@Query("update RecordCert c set c.fileDeletedAt = :now where c.fileId = :fileId")
	int markFileDeleted(@Param("fileId") long fileId, @Param("now") LocalDateTime now);
}
