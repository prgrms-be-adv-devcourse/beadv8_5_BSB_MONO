package com.bukang.board.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.bukang.board.domain.BoardComment;
import com.bukang.board.domain.BoardCommentStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "댓글 또는 답글. 답글은 부모 댓글의 replies에 들어 있다 (한 단계만)")
public record CommentDto(
	@Schema(description = "댓글 ID", example = "21")
	long commentId,

	@Schema(description = "답글이면 부모 댓글 ID, 댓글이면 null", example = "null")
	Long parentId,

	@Schema(description = "VISIBLE 또는 DELETED. 답글이 달린 댓글을 지우면 DELETED로 남는다 (화면에 「삭제된 댓글이에요」)",
		example = "VISIBLE")
	BoardCommentStatus status,

	@Schema(description = "내용. DELETED면 null", example = "저는 반포 한강공원 코스를 좋아해요.")
	String content,

	@Schema(description = "글쓴이. DELETED면 null")
	WriterDto writer,

	@Schema(description = "내가 쓴 댓글인지", example = "false")
	boolean mine,

	@Schema(description = "고친 적이 있는지 (「수정됨」 표시)", example = "false")
	boolean edited,

	@Schema(description = "쓴 시각", example = "2026-11-10T21:30:00")
	LocalDateTime createdAt,

	@Schema(description = "답글 목록 (오래된 순). 답글은 항상 빈 목록")
	List<CommentDto> replies
) {
	public static CommentDto visible(BoardComment comment, WriterDto writer, boolean mine, List<CommentDto> replies) {
		return new CommentDto(comment.getId(), comment.getParentId(), comment.getStatus(), comment.getContent(),
			writer, mine, comment.isEdited(), comment.getCreateDate(), replies);
	}

	// 지운 댓글과 신고로 숨긴 댓글은 내용과 글쓴이를 내보내지 않고, 둘 다 DELETED로 보여 준다
	public static CommentDto deleted(BoardComment comment, List<CommentDto> replies) {
		return new CommentDto(comment.getId(), comment.getParentId(), BoardCommentStatus.DELETED, null, null, false,
			false,
			comment.getCreateDate(), replies);
	}
}
