package com.bukang.board.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.board.domain.BoardComment;
import com.bukang.board.domain.BoardCommentStatus;
import com.bukang.board.support.BoardTestBase;

/**
 * 댓글·답글(한 단계)·고치기·지우기. 지운 댓글에 답글이 있으면 지움 상태로 남는지, 도배 제한을 확인한다
 */
class BoardCommentApiTest extends BoardTestBase {
	@Test
	@DisplayName("댓글을 달면 201이고, 목록은 로그인 없이 볼 수 있다")
	void createAndList() throws Exception {
		long postId = newPost(ME, "글");

		createComment(OTHER, postId, body("content", "첫 댓글")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.commentId").isNumber());

		getComments(null, postId).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].content").value("첫 댓글"))
			.andExpect(jsonPath("$.data[0].status").value("VISIBLE"))
			.andExpect(jsonPath("$.data[0].writer.nickname").value("러너둘"))
			.andExpect(jsonPath("$.data[0].mine").value(false))
			.andExpect(jsonPath("$.data[0].edited").value(false))
			.andExpect(jsonPath("$.data[0].replies.length()").value(0));
		getComments(OTHER, postId).andExpect(jsonPath("$.data[0].mine").value(true));
	}

	@Test
	@DisplayName("답글은 부모 댓글 아래에 묶여 나온다")
	void replyIsNested() throws Exception {
		long postId = newPost(ME, "글");
		long parent = newComment(OTHER, postId, "댓글", null);
		newComment(ME, postId, "답글", parent);

		getComments(null, postId).andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].replies.length()").value(1))
			.andExpect(jsonPath("$.data[0].replies[0].content").value("답글"))
			.andExpect(jsonPath("$.data[0].replies[0].parentId").value(parent));
	}

	@Test
	@DisplayName("답글의 답글은 400 BOARD_REPLY_DEPTH이고 저장되지 않는다")
	void replyDepth() throws Exception {
		long postId = newPost(ME, "글");
		long parent = newComment(OTHER, postId, "댓글", null);
		long reply = newComment(ME, postId, "답글", parent);

		createComment(OTHER, postId, body("content", "답글의 답글", "parentId", reply))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BOARD_REPLY_DEPTH"));

		assertThat(commentRepository.count()).isEqualTo(2);
	}

	@Test
	@DisplayName("다른 글의 댓글이나 없는 댓글을 부모로 지정하면 404 BOARD_COMMENT_NOT_FOUND")
	void parentMustBelongToSamePost() throws Exception {
		long postA = newPost(ME, "글A");
		long postB = newPost(ME, "글B");
		long commentOfA = newComment(OTHER, postA, "A의 댓글", null);

		createComment(OTHER, postB, body("content", "답글", "parentId", commentOfA))
			.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BOARD_COMMENT_NOT_FOUND"));
		createComment(OTHER, postB, body("content", "답글", "parentId", 999999))
			.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BOARD_COMMENT_NOT_FOUND"));
	}

	@Test
	@DisplayName("지운 댓글에는 답글을 달 수 없다 (404)")
	void cannotReplyToDeletedComment() throws Exception {
		long postId = newPost(ME, "글");
		long parent = newComment(OTHER, postId, "댓글", null);
		deleteComment(OTHER, parent);

		createComment(ME, postId, body("content", "답글", "parentId", parent)).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("없는 글·지운 글에는 댓글을 달 수 없다 (404 BOARD_POST_NOT_FOUND)")
	void commentOnMissingPost() throws Exception {
		long postId = newPost(ME, "글");
		deletePost(ME, postId);

		createComment(OTHER, postId, body("content", "댓글")).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("BOARD_POST_NOT_FOUND"));
		createComment(OTHER, 999999, body("content", "댓글")).andExpect(status().isNotFound());
		getComments(null, postId).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("숨겨진 글에는 남은 404, 쓴 사람은 409 BOARD_POST_HIDDEN으로 댓글·답글이 막힌다")
	void commentOnHiddenPost() throws Exception {
		long postId = newPost(ME, "글");
		long comment = newComment(OTHER, postId, "숨기기 전 댓글", null);
		hidePost(postId);

		createComment(OTHER, postId, body("content", "댓글")).andExpect(status().isNotFound());
		getComments(OTHER, postId).andExpect(status().isNotFound());
		createComment(ME, postId, body("content", "댓글")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BOARD_POST_HIDDEN"));
		createComment(ME, postId, body("content", "답글", "parentId", comment)).andExpect(status().isConflict());
		assertThat(commentRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("숨겨진 글의 댓글은 글쓴이가 아니면 고치기·지우기가 404, 글쓴이면 409 BOARD_POST_HIDDEN")
	void editCommentOfHiddenPost() throws Exception {
		long postId = newPost(ME, "글");
		long comment = newComment(OTHER, postId, "댓글", null);
		hidePost(postId);

		putComment(OTHER, comment, body("content", "바꿈")).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("BOARD_COMMENT_NOT_FOUND"));
		deleteComment(OTHER, comment).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("BOARD_COMMENT_NOT_FOUND"));
		putComment(ME, comment, body("content", "바꿈")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BOARD_POST_HIDDEN"));
		deleteComment(ME, comment).andExpect(status().isConflict());

		BoardComment saved = commentRepository.findById(comment).orElseThrow();
		assertThat(saved.getContent()).isEqualTo("댓글");
		assertThat(saved.getStatus()).isEqualTo(BoardCommentStatus.VISIBLE);
	}

	@Test
	@DisplayName("로그인 없이 댓글을 달면 401")
	void commentNeedsLogin() throws Exception {
		long postId = newPost(ME, "글");

		createComment(null, postId, body("content", "댓글")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("댓글은 500자까지, 비어 있으면 400")
	void commentLength() throws Exception {
		long postId = newPost(ME, "글");

		createComment(OTHER, postId, body("content", longText(501))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		createComment(OTHER, postId, body("content", " ")).andExpect(status().isBadRequest());
		createComment(OTHER, postId, body("content", longText(500))).andExpect(status().isCreated());
		long comment = newComment(OTHER, postId, "댓글", null);
		putComment(OTHER, comment, body("content", longText(501))).andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("댓글을 고치면 수정됨이 붙고, 남의 댓글은 403 BOARD_NOT_WRITER")
	void editComment() throws Exception {
		long postId = newPost(ME, "글");
		long comment = newComment(OTHER, postId, "댓글", null);

		putComment(ME, comment, body("content", "바꿈")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("BOARD_NOT_WRITER"));
		assertThat(commentRepository.findById(comment).orElseThrow().getContent()).isEqualTo("댓글");

		putComment(OTHER, comment, body("content", "바꾼 댓글")).andExpect(status().isOk());
		getComments(null, postId).andExpect(jsonPath("$.data[0].content").value("바꾼 댓글"))
			.andExpect(jsonPath("$.data[0].edited").value(true));
	}

	@Test
	@DisplayName("남의 댓글은 지울 수 없고(403), 없는 댓글은 404 BOARD_COMMENT_NOT_FOUND")
	void deleteGuards() throws Exception {
		long postId = newPost(ME, "글");
		long comment = newComment(OTHER, postId, "댓글", null);

		deleteComment(ME, comment).andExpect(status().isForbidden());
		deleteComment(null, comment).andExpect(status().isUnauthorized());
		deleteComment(OTHER, 999999).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("BOARD_COMMENT_NOT_FOUND"));
		putComment(OTHER, 999999, body("content", "x")).andExpect(status().isNotFound());
		assertThat(commentRepository.findById(comment).orElseThrow().getStatus())
			.isEqualTo(BoardCommentStatus.VISIBLE);
	}

	@Test
	@DisplayName("답글이 없는 댓글을 지우면 목록에서 사라지고, 행은 지움 상태로 남는다")
	void deleteWithoutReplies() throws Exception {
		long postId = newPost(ME, "글");
		long comment = newComment(OTHER, postId, "댓글", null);

		deleteComment(OTHER, comment).andExpect(status().isOk());

		getComments(null, postId).andExpect(jsonPath("$.data.length()").value(0));
		assertThat(commentRepository.findById(comment).orElseThrow().getStatus())
			.isEqualTo(BoardCommentStatus.DELETED);
	}

	@Test
	@DisplayName("답글이 달린 댓글을 지우면 내용·글쓴이 없이 지움 상태로 남아 답글이 이어진다")
	void deleteWithReplies() throws Exception {
		long postId = newPost(ME, "글");
		long parent = newComment(OTHER, postId, "비밀 내용", null);
		newComment(ME, postId, "답글", parent);

		deleteComment(OTHER, parent).andExpect(status().isOk());

		getComments(null, postId).andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].status").value("DELETED"))
			.andExpect(jsonPath("$.data[0].content").value(org.hamcrest.Matchers.nullValue()))
			.andExpect(jsonPath("$.data[0].writer").value(org.hamcrest.Matchers.nullValue()))
			.andExpect(jsonPath("$.data[0].replies.length()").value(1));
		putComment(OTHER, parent, body("content", "되살리기")).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("지운 답글은 목록에서 빠진다")
	void deletedReplyIsOmitted() throws Exception {
		long postId = newPost(ME, "글");
		long parent = newComment(OTHER, postId, "댓글", null);
		long reply = newComment(ME, postId, "답글", parent);

		deleteComment(ME, reply);

		getComments(null, postId).andExpect(jsonPath("$.data[0].replies.length()").value(0));
	}

	@Test
	@DisplayName("지운 글의 댓글은 고치거나 지울 수 없다 (404)")
	void commentOfDeletedPost() throws Exception {
		long postId = newPost(ME, "글");
		long comment = newComment(OTHER, postId, "댓글", null);
		deletePost(ME, postId);

		putComment(OTHER, comment, body("content", "x")).andExpect(status().isNotFound());
		deleteComment(OTHER, comment).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("댓글 도배 제한은 members:{회원ID}:board-comment-cooldown 키로 10초를 걸고, 걸리면 429 BOARD_TOO_FAST")
	void commentCooldown() throws Exception {
		long postId = newPost(ME, "글");
		when(valueOperations.setIfAbsent(eq("members:2:board-comment-cooldown"), anyString(), any(Duration.class)))
			.thenReturn(true, false);

		createComment(OTHER, postId, body("content", "첫 댓글")).andExpect(status().isCreated());
		createComment(OTHER, postId, body("content", "둘째 댓글")).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("BOARD_TOO_FAST"));

		verify(valueOperations, org.mockito.Mockito.times(2))
			.setIfAbsent(eq("members:2:board-comment-cooldown"), anyString(), eq(Duration.ofSeconds(10)));
		assertThat(commentRepository.findAll()).extracting(BoardComment::getContent).containsExactly("첫 댓글");
	}

	@Test
	@DisplayName("글 도배 제한과 댓글 도배 제한은 따로 센다 (글 직후 댓글은 막히지 않는다)")
	void cooldownsAreSeparate() throws Exception {
		when(valueOperations.setIfAbsent(eq("members:1:board-post-cooldown"), anyString(), any(Duration.class)))
			.thenReturn(true, false);

		long postId = newPost(ME, "글");

		createComment(ME, postId, body("content", "댓글")).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("Redis가 멈추면 제한 없이 댓글을 통과시킨다")
	void redisDownAllowsComment() throws Exception {
		long postId = newPost(ME, "글");
		when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
			.thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("redis down"));

		createComment(OTHER, postId, body("content", "댓글1")).andExpect(status().isCreated());
		createComment(OTHER, postId, body("content", "댓글2")).andExpect(status().isCreated());
	}
}
