package com.bukang.board.out;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.bukang.board.domain.BoardReport;
import com.bukang.board.domain.ReportTargetType;

public interface BoardReportRepository extends JpaRepository<BoardReport, Long> {
	boolean existsByTargetTypeAndTargetIdAndReporterId(ReportTargetType targetType, long targetId, long reporterId);

	// 숨김 기준에 세는 신고 수. 되살린 뒤에는 되살린 시각 이후의 신고만 센다
	long countByTargetTypeAndTargetIdAndCreateDateAfter(ReportTargetType targetType, long targetId,
		LocalDateTime since);

	List<BoardReport> findByTargetTypeAndTargetIdIn(ReportTargetType targetType, Collection<Long> targetIds);

	// 아직 숨기지도 처리하지도 않은 글에 쌓인 신고 수 (되살린 글은 되살린 뒤의 신고만)
	@Query("select count(r) from BoardReport r, BoardPost p where r.targetType = "
		+ "com.bukang.board.domain.ReportTargetType.POST and r.targetId = p.id "
		+ "and p.status = com.bukang.board.domain.BoardPostStatus.VISIBLE "
		+ "and (p.handledAt is null or r.createDate > p.handledAt)")
	long countPendingOnPosts();

	@Query("select count(r) from BoardReport r, BoardComment c where r.targetType = "
		+ "com.bukang.board.domain.ReportTargetType.COMMENT and r.targetId = c.id "
		+ "and c.status = com.bukang.board.domain.BoardCommentStatus.VISIBLE "
		+ "and (c.handledAt is null or r.createDate > c.handledAt)")
	long countPendingOnComments();
}
