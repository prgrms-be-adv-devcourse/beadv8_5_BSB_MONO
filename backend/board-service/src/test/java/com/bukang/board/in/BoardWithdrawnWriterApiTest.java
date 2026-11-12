package com.bukang.board.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.board.domain.BoardMember;
import com.bukang.board.support.BoardTestBase;

/**
 * 탈퇴한 회원은 Access Token이 만료될 때까지(최대 30분) 게이트웨이를 통과하므로 게시판이 직접 쓰기를 막는다
 */
class BoardWithdrawnWriterApiTest extends BoardTestBase {
	private static final LocalDateTime WITHDRAWN_AT = LocalDateTime.of(2026, 11, 10, 12, 0);

	@Test
	@DisplayName("탈퇴한 회원이 글을 쓰면 403이고 저장하지 않으며, 도배 제한 시간도 쓰지 않는다")
	void withdrawnCannotCreatePost() throws Exception {
		memberRepository.save(BoardMember.withdrawnOf(ME, WITHDRAWN_AT));

		createPost(ME, postBody("제목", "본문")).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("BOARD_MEMBER_WITHDRAWN"));

		assertThat(postRepository.count()).isZero();
		verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
	}

	@Test
	@DisplayName("탈퇴한 회원은 댓글 쓰기·글 고치기·지우기·좋아요가 모두 403이다")
	void withdrawnCannotWriteAnything() throws Exception {
		long postId = newPost(ME, "내 글");
		long commentId = newComment(ME, postId, "내 댓글", null);
		memberRepository.save(BoardMember.withdrawnOf(ME, WITHDRAWN_AT));

		createComment(ME, postId, body("content", "댓글")).andExpect(status().isForbidden());
		putPost(ME, postId, postBody("고친 제목", "고친 본문")).andExpect(status().isForbidden());
		deletePost(ME, postId).andExpect(status().isForbidden());
		putComment(ME, commentId, body("content", "고친 댓글")).andExpect(status().isForbidden());
		deleteComment(ME, commentId).andExpect(status().isForbidden());
		putLike(ME, postId).andExpect(status().isForbidden());

		assertThat(commentRepository.count()).isEqualTo(1);
		assertThat(likeRepository.count()).isZero();
	}

	@Test
	@DisplayName("회원 복사본이 아직 없는 회원은 글을 쓸 수 있다")
	void missingReplicaCanWrite() throws Exception {
		long noReplicaMember = 3L;

		createPost(noReplicaMember, postBody("제목", "본문")).andExpect(status().isCreated());
	}
}
