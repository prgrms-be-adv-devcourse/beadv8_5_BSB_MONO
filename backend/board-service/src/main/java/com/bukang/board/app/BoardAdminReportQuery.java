package com.bukang.board.app;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.board.domain.BoardComment;
import com.bukang.board.domain.BoardMember;
import com.bukang.board.domain.BoardPost;
import com.bukang.board.domain.BoardReport;
import com.bukang.board.domain.ReportHandledAction;
import com.bukang.board.domain.ReportReason;
import com.bukang.board.domain.ReportTargetType;
import com.bukang.board.dto.AdminReportDto;
import com.bukang.board.dto.WriterDto;
import com.bukang.board.out.BoardCommentRepository;
import com.bukang.board.out.BoardMemberRepository;
import com.bukang.board.out.BoardPostRepository;
import com.bukang.board.out.BoardReportRepository;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 신고 목록: 숨겨진 글·댓글과 관리자가 처리한 글·댓글. 부른 회원이 관리자인지는 회원 서비스에 묻는다
 */
@Service
@RequiredArgsConstructor
public class BoardAdminReportQuery {
	private final BoardAdminGuard boardAdminGuard;
	private final BoardPostRepository boardPostRepository;
	private final BoardCommentRepository boardCommentRepository;
	private final BoardReportRepository boardReportRepository;
	private final BoardMemberRepository boardMemberRepository;
	private final Clock clock;

	// 글이든 댓글이든 목록에서는 같은 모양으로 다룬다
	private record Target(ReportTargetType type, long id, long writerId, String content, String postTitle,
		boolean hidden, ReportHandledAction action, LocalDateTime handledAt) {
		static Target of(BoardPost post) {
			return new Target(ReportTargetType.POST, post.getId(), post.getMemberId(), post.getTitle(), null,
				post.isHidden(), post.getHandledAction(), post.getHandledAt());
		}

		// parent: 댓글이 달린 글 (없으면 null)
		static Target of(BoardComment comment, BoardPost parent) {
			return new Target(ReportTargetType.COMMENT, comment.getId(), comment.getMemberId(), comment.getContent(),
				parent != null ? parent.getTitle() : null, comment.isHidden(), comment.getHandledAction(),
				comment.getHandledAt());
		}
	}

	// shortcut: 숨겨졌거나 처리한 글·댓글을 모두 읽는다 (페이지 없음). 수백 건을 넘으면 페이지로 나눈다
	@Transactional(readOnly = true)
	public AdminReportDto list(long adminId) {
		boardAdminGuard.requireAdmin(adminId);
		List<AdminReportDto.Item> items = toItems(loadTargets());
		// 숨겨진 것 먼저, 그다음 최근에 처리한 순
		items.sort(Comparator.comparing((AdminReportDto.Item item) -> item.status() != AdminReportDto.Status.HIDDEN)
			.thenComparing(AdminReportDto.Item::handledAt, Comparator.nullsLast(Comparator.reverseOrder())));
		long pending = boardReportRepository.countPendingOnPosts() + boardReportRepository.countPendingOnComments();
		return AdminReportDto.of(items, pending, LocalDate.now(clock).atStartOfDay());
	}

	// 글 먼저, 댓글 나중
	private List<Target> loadTargets() {
		List<BoardPost> posts = boardPostRepository.findReported();
		List<BoardComment> comments = boardCommentRepository.findReported();
		Map<Long, BoardPost> parents = boardPostRepository
			.findAllById(comments.stream().map(BoardComment::getPostId).distinct().toList())
			.stream()
			.collect(Collectors.toMap(BoardPost::getId, Function.identity()));
		List<Target> targets = new ArrayList<>();
		posts.forEach(post -> targets.add(Target.of(post)));
		comments.forEach(comment -> targets.add(Target.of(comment, parents.get(comment.getPostId()))));
		return targets;
	}

	private List<AdminReportDto.Item> toItems(List<Target> targets) {
		Map<Long, BoardMember> writers = boardMemberRepository
			.writersOf(targets.stream().map(Target::writerId).toList());
		Map<ReportTargetType, Map<Long, List<BoardReport>>> reports = new EnumMap<>(ReportTargetType.class);
		for (ReportTargetType type : ReportTargetType.values()) {
			reports.put(type, reportsOf(type, targets));
		}
		return targets.stream()
			.map(target -> item(target, writers.get(target.writerId()),
				reports.get(target.type()).getOrDefault(target.id(), List.of())))
			.collect(Collectors.toCollection(ArrayList::new));
	}

	private Map<Long, List<BoardReport>> reportsOf(ReportTargetType type, List<Target> targets) {
		List<Long> ids = targets.stream().filter(target -> target.type() == type).map(Target::id).toList();
		return boardReportRepository.findByTargetTypeAndTargetIdIn(type, ids)
			.stream()
			.collect(Collectors.groupingBy(BoardReport::getTargetId));
	}

	private AdminReportDto.Item item(Target target, BoardMember writer, List<BoardReport> found) {
		Map<ReportReason, Long> byReason = found.stream()
			.collect(Collectors.groupingBy(BoardReport::getReason, () -> new EnumMap<>(ReportReason.class),
				Collectors.counting()));
		List<AdminReportDto.ReasonCount> reasons = byReason.entrySet()
			.stream()
			.map(entry -> new AdminReportDto.ReasonCount(entry.getKey(), entry.getValue()))
			.toList();
		AdminReportDto.Status status = target.hidden() ? AdminReportDto.Status.HIDDEN
			: target.action() == ReportHandledAction.DELETED ? AdminReportDto.Status.DELETED
			: AdminReportDto.Status.RESTORED;
		return new AdminReportDto.Item(target.type(), target.id(), WriterDto.of(writer).nickname(), target.content(),
			target.postTitle(), found.size(), reasons, status, target.hidden() ? null : target.handledAt());
	}
}
