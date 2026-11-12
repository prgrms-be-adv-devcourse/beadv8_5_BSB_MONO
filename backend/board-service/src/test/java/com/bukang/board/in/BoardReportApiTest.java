package com.bukang.board.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.board.domain.BoardMember;
import com.bukang.board.support.BoardTestBase;

/**
 * 글·댓글 신고. 서로 다른 회원 5명이 신고하면 자동으로 숨기고, 같은 회원의 중복 신고는 1건으로 센다
 */
class BoardReportApiTest extends BoardTestBase {
	private static final long REPORTER_1 = 11L;

	private long postRows(long postId) {
		return jdbcTemplate.queryForObject(
			"select count(*) from board_report where target_type = 'POST' and target_id = ?", Long.class, postId);
	}

	private String postStatus(long postId) {
		return jdbcTemplate.queryForObject("select status from board_post where id = ?", String.class, postId);
	}

	private void reportByMembers(long postId, int count) throws Exception {
		for (long id = REPORTER_1; id < REPORTER_1 + count; id++) {
			reportPost(id, postId, "AD").andExpect(status().isCreated());
		}
	}

	@Test
	@DisplayName("글을 신고하면 201이고, 4명까지는 숨기지 않는다")
	void reportBelowThreshold() throws Exception {
		long postId = newPost(ME, "광고 글");

		reportPost(REPORTER_1, postId, "AD").andExpect(status().isCreated())
			.andExpect(jsonPath("$.message").value("신고했습니다."));
		reportByMembers(postId, 4);

		assertThat(postRows(postId)).isEqualTo(4);
		assertThat(postStatus(postId)).isEqualTo("VISIBLE");
		getPost(OTHER, postId).andExpect(status().isOk());
	}

	@Test
	@DisplayName("서로 다른 회원 5명이 신고하면 숨겨져, 다른 사람에게는 404이고 쓴 사람에게만 HIDDEN으로 보인다")
	void fiveReportsHide() throws Exception {
		long postId = newPost(ME, "광고 글");

		reportByMembers(postId, 5);

		assertThat(postStatus(postId)).isEqualTo("HIDDEN");
		getPost(OTHER, postId).andExpect(status().isNotFound());
		getPost(null, postId).andExpect(status().isNotFound());
		getPost(ME, postId).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("HIDDEN"));
	}

	@Test
	@DisplayName("같은 회원이 여러 번 신고해도 1건으로 세고, 같은 응답(201)이다")
	void duplicateReportCountsOnce() throws Exception {
		long postId = newPost(ME, "글");

		for (int i = 0; i < 6; i++) {
			reportPost(REPORTER_1, postId, i % 2 == 0 ? "AD" : "ABUSE").andExpect(status().isCreated());
		}

		assertThat(postRows(postId)).isEqualTo(1);
		assertThat(postStatus(postId)).isEqualTo("VISIBLE");
	}

	@Test
	@DisplayName("로그인 없이 신고하면 401")
	void needsLogin() throws Exception {
		long postId = newPost(ME, "글");

		reportPost(null, postId, "AD").andExpect(status().isUnauthorized());
		assertThat(postRows(postId)).isZero();
	}

	@Test
	@DisplayName("내 글은 신고할 수 없다 (400)")
	void cannotReportOwnPost() throws Exception {
		long postId = newPost(ME, "내 글");

		reportPost(ME, postId, "AD").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BOARD_SELF_REPORT"));
		assertThat(postRows(postId)).isZero();
	}

	@Test
	@DisplayName("없는 글·지운 글·숨겨진 글은 신고할 수 없다 (404)")
	void cannotReportUnavailablePost() throws Exception {
		long deleted = newPost(ME, "지운 글");
		deletePost(ME, deleted);
		long hidden = newPost(ME, "숨긴 글");
		hidePost(hidden);

		reportPost(OTHER, 999999, "AD").andExpect(status().isNotFound());
		reportPost(OTHER, deleted, "AD").andExpect(status().isNotFound());
		reportPost(OTHER, hidden, "AD").andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("사유가 없거나 목록에 없는 값이면 400이고 저장하지 않는다")
	void invalidReason() throws Exception {
		long postId = newPost(ME, "글");

		reportPost(OTHER, postId, "SPAM").andExpect(status().isBadRequest());
		mockMvc.perform(json(as(post(POSTS + "/" + postId + "/report"), OTHER), "{}"))
			.andExpect(status().isBadRequest());
		assertThat(postRows(postId)).isZero();
	}

	@Test
	@DisplayName("탈퇴한 회원은 신고할 수 없다 (403)")
	void withdrawnCannotReport() throws Exception {
		long postId = newPost(ME, "글");
		memberRepository.save(BoardMember.withdrawnOf(OTHER, LocalDateTime.now()));

		reportPost(OTHER, postId, "AD").andExpect(status().isForbidden());
		assertThat(postRows(postId)).isZero();
	}

	@Test
	@DisplayName("서로 다른 회원 5명이 동시에 신고해도 5건으로 세고 글은 숨겨진다")
	void concurrentReports() throws Exception {
		long postId = newPost(ME, "글");
		ExecutorService pool = Executors.newFixedThreadPool(5);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<?>> futures = new ArrayList<>();
		for (long id = REPORTER_1; id < REPORTER_1 + 5; id++) {
			long reporter = id;
			futures.add(pool.submit(() -> {
				start.await();
				return reportPost(reporter, postId, "AD").andReturn().getResponse().getStatus();
			}));
		}
		start.countDown();
		for (Future<?> future : futures) {
			assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo(201);
		}
		pool.shutdownNow();

		assertThat(postRows(postId)).isEqualTo(5);
		assertThat(postStatus(postId)).isEqualTo("HIDDEN");
	}

	@Test
	@DisplayName("댓글도 5명이 신고하면 숨겨져 목록에서 내용 없는 삭제 상태로 보인다 (답글이 달려 있을 때)")
	void commentHidden() throws Exception {
		long postId = newPost(ME, "글");
		long commentId = newComment(OTHER, postId, "욕설 댓글", null);
		newComment(ME, postId, "답글", commentId);

		for (long id = REPORTER_1; id < REPORTER_1 + 5; id++) {
			reportComment(id, commentId, "ABUSE").andExpect(status().isCreated());
		}

		getComments(ME, postId).andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].status").value("DELETED"))
			.andExpect(jsonPath("$.data[0].content").isEmpty())
			.andExpect(jsonPath("$.data[0].replies[0].content").value("답글"));
	}

	@Test
	@DisplayName("내 댓글은 신고할 수 없고(400), 없는·지운 댓글은 404, 같은 회원 중복은 1건이다")
	void commentReportRules() throws Exception {
		long postId = newPost(ME, "글");
		long commentId = newComment(OTHER, postId, "댓글", null);
		long deleted = newComment(OTHER, postId, "지울 댓글", null);
		deleteComment(OTHER, deleted);

		reportComment(OTHER, commentId, "AD").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BOARD_SELF_REPORT"));
		reportComment(REPORTER_1, 999999, "AD").andExpect(status().isNotFound());
		reportComment(REPORTER_1, deleted, "AD").andExpect(status().isNotFound());
		reportComment(null, commentId, "AD").andExpect(status().isUnauthorized());
		reportComment(REPORTER_1, commentId, "AD").andExpect(status().isCreated());
		reportComment(REPORTER_1, commentId, "OTHER").andExpect(status().isCreated());

		assertThat(jdbcTemplate.queryForObject(
			"select count(*) from board_report where target_type = 'COMMENT'", Long.class)).isEqualTo(1);
	}

	@Test
	@DisplayName("숨겨진 글의 댓글은 신고할 수 없다 (404)")
	void commentOnHiddenPost() throws Exception {
		long postId = newPost(ME, "글");
		long commentId = newComment(OTHER, postId, "댓글", null);
		hidePost(postId);

		reportComment(REPORTER_1, commentId, "AD").andExpect(status().isNotFound());
	}
}
