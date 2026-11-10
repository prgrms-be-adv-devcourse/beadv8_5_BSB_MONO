package com.bukang.inquiry.out;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.inquiry.domain.Inquiry;
import com.bukang.inquiry.domain.InquiryAssignee;
import com.bukang.inquiry.domain.InquiryStatus;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {
	Page<Inquiry> findByMemberIdOrderByIdDesc(long memberId, Pageable pageable);

	Page<Inquiry> findBySellerMemberIdAndAssigneeOrderByIdDesc(long sellerMemberId, InquiryAssignee assignee,
		Pageable pageable);

	Page<Inquiry> findAllByOrderByIdDesc(Pageable pageable);

	// 답변 대기인 행만 바꾼다. 바뀐 행이 0이면 다른 요청이 먼저 답한 것이다 (동시 답변 중 하나만 저장)
	// 벌크 UPDATE는 감사(수정 시각)를 건너뛰므로 수정 시각도 함께 넣는다
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Inquiry i set i.status = :answered, i.answer = :answer, i.answeredBy = :answeredBy, "
		+ "i.answeredAt = :now, i.modifyDate = :now where i.id = :id and i.status = :waiting")
	int answerIfWaiting(@Param("id") long id, @Param("answer") String answer, @Param("answeredBy") long answeredBy,
		@Param("now") LocalDateTime now, @Param("answered") InquiryStatus answered,
		@Param("waiting") InquiryStatus waiting);
}
