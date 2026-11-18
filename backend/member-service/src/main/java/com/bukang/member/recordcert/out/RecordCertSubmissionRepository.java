package com.bukang.member.recordcert.out;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.member.recordcert.domain.RecordCertSubmission;
import com.bukang.member.recordcert.domain.SubmissionStatus;

public interface RecordCertSubmissionRepository extends JpaRepository<RecordCertSubmission, Long> {
	boolean existsByOrderItemIdAndStatusIn(long orderItemId, Collection<SubmissionStatus> statuses);

	// 최신순 전부 (submit-spec: 한 사람에 수십 줄을 넘기 어렵다)
	List<RecordCertSubmission> findByMemberIdOrderByIdDesc(long memberId);
}
