package com.bukang.board.app;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.board.domain.BoardComment;
import com.bukang.board.domain.BoardCommentStatus;
import com.bukang.board.domain.BoardMember;
import com.bukang.board.domain.BoardPost;
import com.bukang.board.dto.CommentDto;
import com.bukang.board.dto.PostDetailDto;
import com.bukang.board.dto.WriterDto;
import com.bukang.board.out.BoardCommentRepository;
import com.bukang.board.out.BoardMemberRepository;
import com.bukang.board.out.BoardPostLikeRepository;

import lombok.RequiredArgsConstructor;

/**
 * 글 상세, 댓글 목록 읽기. 로그인 안 한 사람도 읽는다 (viewerId가 null)
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BoardQueryUseCase {
	private final BoardPostFinder boardPostFinder;
	private final BoardCommentRepository boardCommentRepository;
	private final BoardMemberRepository boardMemberRepository;
	private final BoardPostLikeRepository boardPostLikeRepository;

	public PostDetailDto getPost(Long viewerId, long postId) {
		BoardPost post = boardPostFinder.findViewable(postId, viewerId);
		WriterDto writer = WriterDto.of(boardMemberRepository.findById(post.getMemberId()).orElse(null));
		boolean mine = viewerId != null && post.isWrittenBy(viewerId);
		boolean liked = viewerId != null && boardPostLikeRepository.existsByPostIdAndMemberId(postId, viewerId);
		return PostDetailDto.of(post, writer, mine, liked);
	}

	// 댓글 아래에 답글을 묶어 준다. 지운 답글은 빼고, 지운 댓글은 지우지 않은 답글이 달려 있을 때만 지움 상태로 남긴다
	// shortcut: 글의 댓글을 모두 읽는다 (페이지 없음). 댓글이 수백 개를 넘는 글이 생기면 커서 방식으로 바꾼다
	public List<CommentDto> getComments(Long viewerId, long postId) {
		boardPostFinder.findViewable(postId, viewerId);
		List<BoardComment> comments = boardCommentRepository.findByPostIdOrderByIdAsc(postId);

		Map<Long, List<BoardComment>> repliesByParent = comments.stream()
			.filter(comment -> comment.getParentId() != null && !comment.isDeleted())
			.collect(Collectors.groupingBy(BoardComment::getParentId));
		Set<Long> writerIds = comments.stream().map(BoardComment::getMemberId).collect(Collectors.toSet());
		Map<Long, BoardMember> writers = new HashMap<>();
		boardMemberRepository.findAllById(writerIds).forEach(member -> writers.put(member.getId(), member));

		return comments.stream()
			.filter(comment -> comment.getParentId() == null)
			.map(parent -> toDto(parent, repliesByParent.getOrDefault(parent.getId(), List.of()), writers, viewerId))
			.filter(dto -> dto.status() != BoardCommentStatus.DELETED || !dto.replies().isEmpty())
			.toList();
	}

	private CommentDto toDto(BoardComment parent, List<BoardComment> replies, Map<Long, BoardMember> writers,
		Long viewerId) {
		List<CommentDto> replyDtos = replies.stream()
			.map(reply -> visibleDto(reply, List.of(), writers, viewerId))
			.toList();
		if (parent.isDeleted()) {
			return CommentDto.deleted(parent, replyDtos);
		}
		return visibleDto(parent, replyDtos, writers, viewerId);
	}

	private CommentDto visibleDto(BoardComment comment, List<CommentDto> replies, Map<Long, BoardMember> writers,
		Long viewerId) {
		boolean mine = viewerId != null && comment.isWrittenBy(viewerId);
		return CommentDto.visible(comment, WriterDto.of(writers.get(comment.getMemberId())), mine, replies);
	}
}
