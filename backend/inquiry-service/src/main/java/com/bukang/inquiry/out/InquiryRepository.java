package com.bukang.inquiry.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.inquiry.domain.Inquiry;
import com.bukang.inquiry.domain.InquiryAssignee;
import com.bukang.inquiry.domain.InquiryStatus;

/**
 * 지운 문의(deleted_at이 찬 행)는 모든 조회에서 뺀다.
 * 판매자·관리자 목록의 「답변 대기 먼저, 최신순」은 status desc, id desc다. 상태 이름이 WAITING > ANSWERED 순서라서 성립한다
 * (이 순서여야 idx_inquiry_seller·idx_inquiry_status 인덱스 순서로 읽는다. 상태 이름을 바꾸면 목록 테스트가 먼저 실패한다)
 */
public interface InquiryRepository extends JpaRepository<Inquiry, Long> {
	Optional<Inquiry> findByIdAndDeletedAtIsNull(long id);

	Page<Inquiry> findByMemberIdAndDeletedAtIsNullOrderByIdDesc(long memberId, Pageable pageable);

	Page<Inquiry> findBySellerMemberIdAndAssigneeAndDeletedAtIsNullOrderByStatusDescIdDesc(long sellerMemberId,
		InquiryAssignee assignee, Pageable pageable);

	Page<Inquiry> findBySellerMemberIdAndAssigneeAndStatusAndDeletedAtIsNullOrderByIdDesc(long sellerMemberId,
		InquiryAssignee assignee, InquiryStatus status, Pageable pageable);

	Page<Inquiry> findByDeletedAtIsNullOrderByStatusDescIdDesc(Pageable pageable);

	Page<Inquiry> findByStatusAndDeletedAtIsNullOrderByIdDesc(InquiryStatus status, Pageable pageable);

	// 관리자 목록의 담당 필터 (idx_inquiry_assignee)
	Page<Inquiry> findByAssigneeAndDeletedAtIsNullOrderByStatusDescIdDesc(InquiryAssignee assignee, Pageable pageable);

	Page<Inquiry> findByAssigneeAndStatusAndDeletedAtIsNullOrderByIdDesc(InquiryAssignee assignee,
		InquiryStatus status, Pageable pageable);

	// 상태별 개수 (판매자 목록 범위)
	@Query("select i.status as status, count(i) as total from Inquiry i where i.sellerMemberId = :sellerMemberId "
		+ "and i.assignee = :assignee and i.deletedAt is null group by i.status")
	List<StatusCount> countBySeller(@Param("sellerMemberId") long sellerMemberId,
		@Param("assignee") InquiryAssignee assignee);

	// 상태별 개수 (관리자 목록 범위: 담당 필터를 쓰면 그 담당)
	@Query("select i.status as status, count(i) as total from Inquiry i where i.assignee = :assignee "
		+ "and i.deletedAt is null group by i.status")
	List<StatusCount> countByAssignee(@Param("assignee") InquiryAssignee assignee);

	// 상태별 개수 (관리자 목록 범위: 전체)
	@Query("select i.status as status, count(i) as total from Inquiry i where i.deletedAt is null group by i.status")
	List<StatusCount> countAll();

	// 답변 대기이고 지우지 않은 행만 바꾼다. 바뀐 행이 0이면 다른 요청이 먼저 답했거나 지운 것이다 (겹쳐도 한쪽만 성공)
	// 벌크 UPDATE는 감사(수정 시각)를 건너뛰므로 수정 시각도 함께 넣는다
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Inquiry i set i.status = :answered, i.answer = :answer, i.answeredBy = :answeredBy, "
		+ "i.answeredAt = :now, i.modifyDate = :now "
		+ "where i.id = :id and i.status = :waiting and i.deletedAt is null")
	int answerIfWaiting(@Param("id") long id, @Param("answer") String answer, @Param("answeredBy") long answeredBy,
		@Param("now") LocalDateTime now, @Param("answered") InquiryStatus answered,
		@Param("waiting") InquiryStatus waiting);

	// 보낸 회원이 답변 대기인 문의의 제목·본문을 고친다. 같은 조건이라 답변과 겹치면 한쪽만 성공한다
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Inquiry i set i.title = :title, i.body = :body, i.modifyDate = :now "
		+ "where i.id = :id and i.memberId = :memberId and i.status = :waiting and i.deletedAt is null")
	int editIfWaiting(@Param("id") long id, @Param("memberId") long memberId, @Param("title") String title,
		@Param("body") String body, @Param("now") LocalDateTime now, @Param("waiting") InquiryStatus waiting);

	// 보낸 회원이 답변 대기인 문의를 지운다 (지움 표시만 하고 행은 남긴다)
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Inquiry i set i.deletedAt = :now, i.modifyDate = :now "
		+ "where i.id = :id and i.memberId = :memberId and i.status = :waiting and i.deletedAt is null")
	int deleteIfWaiting(@Param("id") long id, @Param("memberId") long memberId, @Param("now") LocalDateTime now,
		@Param("waiting") InquiryStatus waiting);

	interface StatusCount {
		InquiryStatus getStatus();

		long getTotal();
	}
}
