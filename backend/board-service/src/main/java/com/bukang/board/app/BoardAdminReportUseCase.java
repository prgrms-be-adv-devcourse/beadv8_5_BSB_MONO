package com.bukang.board.app;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.board.domain.BoardComment;
import com.bukang.board.domain.BoardMember;
import com.bukang.board.domain.BoardPost;
import com.bukang.board.domain.BoardReport;
import com.bukang.board.domain.ReportHandledAction;
import com.bukang.board.domain.ReportReason;
import com.bukang.board.domain.ReportTargetType;
import com.bukang.board.domain.exception.BoardAdminForbiddenException;
import com.bukang.board.domain.exception.BoardReportNotFoundException;
import com.bukang.board.domain.exception.BoardReportNotHandleableException;
import com.bukang.board.dto.AdminReportDto;
import com.bukang.board.dto.WriterDto;
import com.bukang.board.out.BoardCommentRepository;
import com.bukang.board.out.BoardMemberRepository;
import com.bukang.board.out.BoardPostRepository;
import com.bukang.board.out.BoardReportRepository;
import com.bukang.board.out.member.MemberAdminPort;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 신고 관리: 숨겨진 글·댓글 목록, 되살리기, 지우기. 부른 회원이 관리자인지는 회원 서비스에 묻는다
 * 처리는 숨겨진 것에만 한다. 대상 행을 잠근 뒤 상태를 확인하므로 두 관리자가 동시에 눌러도 한 번만 처리된다
 */
@Service
@RequiredArgsConstructor
public class BoardAdminReportUseCase {
	private final MemberAdminPort memberAdminPort;
	private final BoardPostRepository boardPostRepository;
	private final BoardCommentRepository boardCommentRepository;
	private final BoardReportRepository boardReportRepository;
	private final BoardMemberRepository boardMemberRepository;
	private final BoardPostUseCase boardPostUseCase;
	private final BoardOutboxKafkaAppendUseCase boardOutboxKafkaAppendUseCase;

	// shortcut: 숨겨졌거나 처리한 글·댓글을 모두 읽는다 (페이지 없음). 수백 건을 넘으면 페이지로 나눈다
	@Transactional(readOnly = true)
	public AdminReportDto list(long adminId) {
		requireAdmin(adminId);
		List<BoardPost> posts = boardPostRepository.findReported();
		List<BoardComment> comments = boardCommentRepository.findReported();

		Map<Long, BoardPost> commentPosts = new HashMap<>();
		boardPostRepository.findAllById(comments.stream().map(BoardComment::getPostId).distinct().toList())
			.forEach(post -> commentPosts.put(post.getId(), post));
		Map<Long, BoardMember> writers = new HashMap<>();
		List<Long> writerIds = new ArrayList<>(posts.stream().map(BoardPost::getMemberId).toList());
		writerIds.addAll(comments.stream().map(BoardComment::getMemberId).toList());
		boardMemberRepository.findAllById(writerIds).forEach(member -> writers.put(member.getId(), member));
		Map<ReportTargetType, Map<Long, List<BoardReport>>> reports = new EnumMap<>(ReportTargetType.class);
		reports.put(ReportTargetType.POST, reportsOf(ReportTargetType.POST, posts.stream().map(BoardPost::getId)));
		reports.put(ReportTargetType.COMMENT,
			reportsOf(ReportTargetType.COMMENT, comments.stream().map(BoardComment::getId)));

		List<AdminReportDto.Item> items = new ArrayList<>();
		for (BoardPost post : posts) {
			items.add(item(ReportTargetType.POST, post.getId(), writers.get(post.getMemberId()), post.getTitle(), null,
				reports, post.isHidden(), post.getHandledAction(), post.getHandledAt()));
		}
		for (BoardComment comment : comments) {
			BoardPost parent = commentPosts.get(comment.getPostId());
			items.add(item(ReportTargetType.COMMENT, comment.getId(), writers.get(comment.getMemberId()),
				comment.getContent(), parent != null ? parent.getTitle() : null, reports, comment.isHidden(),
				comment.getHandledAction(), comment.getHandledAt()));
		}
		// 숨겨진 것 먼저, 그다음 최근에 처리한 순
		items.sort(Comparator.comparing((AdminReportDto.Item item) -> item.status() != AdminReportDto.Status.HIDDEN)
			.thenComparing(AdminReportDto.Item::handledAt, Comparator.nullsLast(Comparator.reverseOrder())));

		LocalDateTime todayStart = LocalDate.now().atStartOfDay();
		long hidden = items.stream().filter(item -> item.status() == AdminReportDto.Status.HIDDEN).count();
		long handledToday = items.stream()
			.filter(item -> item.handledAt() != null && !item.handledAt().isBefore(todayStart))
			.count();
		long pending = boardReportRepository.countPendingOnPosts() + boardReportRepository.countPendingOnComments();
		return new AdminReportDto(hidden, pending, handledToday, items);
	}

	// 숨겨지지 않은 대상, 이미 처리한 대상은 409, 없는 대상은 404
	@Transactional
	public void handle(long adminId, ReportTargetType type, long targetId, ReportHandledAction action) {
		requireAdmin(adminId);
		LocalDateTime now = LocalDateTime.now();
		if (type == ReportTargetType.POST) {
			BoardPost post = boardPostRepository.findLockedById(targetId)
				.orElseThrow(BoardReportNotFoundException::new);
			if (!post.isHidden()) {
				throw new BoardReportNotHandleableException();
			}
			post.handle(action, now);
			// 되살리면 검색에 다시 나오고 지우면 빠지므로 색인 메시지를 같은 트랜잭션으로 남긴다 (BUK-179)
			boardOutboxKafkaAppendUseCase.appendPostChanged(post.getId());
			if (action == ReportHandledAction.DELETED) {
				boardPostUseCase.releaseFiles(post);
			}
			return;
		}
		BoardComment comment = boardCommentRepository.findLockedById(targetId)
			.orElseThrow(BoardReportNotFoundException::new);
		if (!comment.isHidden()) {
			throw new BoardReportNotHandleableException();
		}
		comment.handle(action, now);
	}

	private void requireAdmin(long memberId) {
		if (!memberAdminPort.isAdmin(memberId)) {
			throw new BoardAdminForbiddenException();
		}
	}

	private Map<Long, List<BoardReport>> reportsOf(ReportTargetType type, Stream<Long> ids) {
		return boardReportRepository.findByTargetTypeAndTargetIdIn(type, ids.toList())
			.stream()
			.collect(Collectors.groupingBy(BoardReport::getTargetId));
	}

	private AdminReportDto.Item item(ReportTargetType type, long targetId, BoardMember writer, String content,
		String postTitle, Map<ReportTargetType, Map<Long, List<BoardReport>>> reports, boolean hidden,
		ReportHandledAction action, LocalDateTime handledAt) {
		List<BoardReport> found = reports.get(type).getOrDefault(targetId, List.of());
		Map<ReportReason, Long> byReason = found.stream()
			.collect(Collectors.groupingBy(BoardReport::getReason, () -> new EnumMap<>(ReportReason.class),
				Collectors.counting()));
		List<AdminReportDto.ReasonCount> reasons = byReason.entrySet()
			.stream()
			.map(entry -> new AdminReportDto.ReasonCount(entry.getKey(), entry.getValue()))
			.toList();
		AdminReportDto.Status status = hidden ? AdminReportDto.Status.HIDDEN
			: action == ReportHandledAction.DELETED ? AdminReportDto.Status.DELETED : AdminReportDto.Status.RESTORED;
		return new AdminReportDto.Item(type, targetId, WriterDto.of(writer).nickname(), content, postTitle,
			found.size(), reasons, status, hidden ? null : handledAt);
	}
}
