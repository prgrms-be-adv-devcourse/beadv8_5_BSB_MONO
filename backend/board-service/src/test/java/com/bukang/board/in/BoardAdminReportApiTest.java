package com.bukang.board.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.board.domain.exception.BoardExternalServiceUnavailableException;
import com.bukang.board.out.member.MemberAdminPort;
import com.bukang.board.support.BoardTestBase;

/**
 * 관리자 신고 관리. 관리자인지는 회원 서비스에 묻고(여기서는 가짜), 숨겨진 글·댓글을 되살리거나 지운다
 */
class BoardAdminReportApiTest extends BoardTestBase {
	private static final String REPORTS = "/api/v1/board/admin/reports";
	private static final long ADMIN = 99L;

	@MockitoBean
	private MemberAdminPort memberAdminPort;

	@BeforeEach
	void adminIs99() {
		when(memberAdminPort.isAdmin(ADMIN)).thenReturn(true);
	}

	private ResultActions list(Long memberId) throws Exception {
		return mockMvc.perform(as(get(REPORTS), memberId));
	}

	private ResultActions handle(Long memberId, String target, long id, String action) throws Exception {
		return mockMvc.perform(as(post(REPORTS + "/" + target + "/" + id + "/" + action), memberId));
	}

	private long hiddenPost(String title) throws Exception {
		long postId = newPost(ME, title);
		for (long id = 11; id < 16; id++) {
			reportPost(id, postId, id < 14 ? "AD" : "ABUSE").andExpect(status().isCreated());
		}
		return postId;
	}

	private long hiddenComment(long postId) throws Exception {
		long commentId = newComment(OTHER, postId, "욕설 댓글", null);
		for (long id = 11; id < 16; id++) {
			reportComment(id, commentId, "ABUSE").andExpect(status().isCreated());
		}
		return commentId;
	}

	@Test
	@DisplayName("로그인 없으면 401, 관리자가 아니면 403이다 (목록·되살리기·지우기 모두)")
	void onlyAdmin() throws Exception {
		long postId = hiddenPost("광고 글");

		list(null).andExpect(status().isUnauthorized());
		handle(null, "posts", postId, "restore").andExpect(status().isUnauthorized());
		list(OTHER).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BOARD_ADMIN_FORBIDDEN"));
		handle(OTHER, "posts", postId, "restore").andExpect(status().isForbidden());
		handle(OTHER, "posts", postId, "delete").andExpect(status().isForbidden());

		assertThat(jdbcTemplate.queryForObject("select status from board_post where id = ?", String.class, postId))
			.isEqualTo("HIDDEN");
	}

	@Test
	@DisplayName("관리자인지 모를 때(회원 서비스 장애)는 관리자로 보지 않고 503이다")
	void adminCheckUnavailable() throws Exception {
		when(memberAdminPort.isAdmin(ADMIN)).thenThrow(new BoardExternalServiceUnavailableException("연결 실패"));

		list(ADMIN).andExpect(status().isServiceUnavailable());
		handle(ADMIN, "posts", 1, "restore").andExpect(status().isServiceUnavailable());
	}

	@Test
	@DisplayName("목록은 숨겨진 글·댓글과 신고 사유별 건수, 위쪽 숫자를 프론트 계약 필드 이름대로 준다")
	void listShape() throws Exception {
		long postId = hiddenPost("러닝화 최저가 모음");
		hiddenComment(newPost(ME, "다른 글"));
		long pendingPost = newPost(ME, "아직 숨기지 않은 글");
		reportPost(11, pendingPost, "OTHER");
		reportPost(12, pendingPost, "OTHER");

		list(ADMIN).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.hiddenCount").value(2))
			.andExpect(jsonPath("$.data.pendingReportCount").value(2))
			.andExpect(jsonPath("$.data.handledTodayCount").value(0))
			.andExpect(jsonPath("$.data.items.length()").value(2))
			.andExpect(jsonPath("$.data.items[?(@.targetType=='POST')].targetId").value(postId))
			.andExpect(jsonPath("$.data.items[?(@.targetType=='POST')].writerNickname").value("러너하나"))
			.andExpect(jsonPath("$.data.items[?(@.targetType=='POST')].content").value("러닝화 최저가 모음"))
			.andExpect(jsonPath("$.data.items[?(@.targetType=='POST')].reportCount").value(5))
			.andExpect(jsonPath("$.data.items[?(@.targetType=='POST')].status").value("HIDDEN"))
			.andExpect(jsonPath("$.data.items[?(@.targetType=='POST')].reasons[?(@.reason=='AD')].count").value(3))
			.andExpect(jsonPath("$.data.items[?(@.targetType=='POST')].reasons[?(@.reason=='ABUSE')].count")
				.value(2))
			.andExpect(jsonPath("$.data.items[?(@.targetType=='COMMENT')].postTitle").value("다른 글"))
			.andExpect(jsonPath("$.data.items[?(@.targetType=='COMMENT')].content").value("욕설 댓글"))
			.andExpect(jsonPath("$.data.items[?(@.targetType=='COMMENT')].writerNickname").value("러너둘"));
	}

	@Test
	@DisplayName("되살리면 글이 다시 보이고, 목록에는 RESTORED와 처리 시각이 남는다")
	void restorePost() throws Exception {
		long postId = hiddenPost("글");

		handle(ADMIN, "posts", postId, "restore").andExpect(status().isOk());

		getPost(OTHER, postId).andExpect(status().isOk());
		list(ADMIN).andExpect(jsonPath("$.data.hiddenCount").value(0))
			.andExpect(jsonPath("$.data.handledTodayCount").value(1))
			.andExpect(jsonPath("$.data.items[0].status").value("RESTORED"))
			.andExpect(jsonPath("$.data.items[0].reportCount").value(5))
			.andExpect(jsonPath("$.data.items[0].handledAt").isNotEmpty());
	}

	@Test
	@DisplayName("되살린 뒤에는 새 신고 5건이 쌓여야 다시 숨긴다 (옛 신고 5건으로 바로 숨기지 않는다)")
	void restoredPostNeedsFreshReports() throws Exception {
		long postId = hiddenPost("글");
		handle(ADMIN, "posts", postId, "restore").andExpect(status().isOk());

		reportPost(21, postId, "AD").andExpect(status().isCreated());
		reportPost(22, postId, "AD").andExpect(status().isCreated());
		getPost(OTHER, postId).andExpect(status().isOk());

		reportPost(23, postId, "AD");
		reportPost(24, postId, "AD");
		reportPost(25, postId, "AD");
		getPost(OTHER, postId).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("글을 지우면 쓴 사람에게도 404이고, 사진 연결을 떼고, 목록에는 DELETED가 남는다")
	void deletePost() throws Exception {
		long postId = hiddenPost("글");

		handle(ADMIN, "posts", postId, "delete").andExpect(status().isOk());

		getPost(ME, postId).andExpect(status().isNotFound());
		verify(kafkaTemplate, timeout(2000).atLeastOnce()).send(any(ProducerRecord.class));
		list(ADMIN).andExpect(jsonPath("$.data.items[0].status").value("DELETED"))
			.andExpect(jsonPath("$.data.items[0].handledAt").isNotEmpty());
	}

	@Test
	@DisplayName("댓글을 되살리면 다시 보이고, 지우면 지움 상태가 된다")
	void handleComment() throws Exception {
		long postId = newPost(ME, "글");
		long restored = hiddenComment(postId);
		long deleted = hiddenComment(postId);

		handle(ADMIN, "comments", restored, "restore").andExpect(status().isOk());
		handle(ADMIN, "comments", deleted, "delete").andExpect(status().isOk());

		getComments(OTHER, postId).andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].commentId").value(restored))
			.andExpect(jsonPath("$.data[0].status").value("VISIBLE"));
		list(ADMIN).andExpect(jsonPath("$.data.items[?(@.targetId==" + deleted + ")].status").value("DELETED"))
			.andExpect(jsonPath("$.data.items[?(@.targetId==" + restored + ")].status").value("RESTORED"));
	}

	@Test
	@DisplayName("숨겨지지 않은 대상은 처리할 수 없고(409), 없는 대상은 404, 같은 처리를 두 번 하면 두 번째는 409다")
	void handleRules() throws Exception {
		long visible = newPost(ME, "보이는 글");
		long postId = hiddenPost("숨긴 글");

		handle(ADMIN, "posts", visible, "delete").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BOARD_REPORT_NOT_HANDLEABLE"));
		handle(ADMIN, "posts", 999999, "restore").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("BOARD_REPORT_NOT_FOUND"));
		handle(ADMIN, "comments", 999999, "restore").andExpect(status().isNotFound());
		handle(ADMIN, "likes", postId, "restore").andExpect(status().isNotFound());
		handle(ADMIN, "posts", postId, "approve").andExpect(status().isNotFound());

		handle(ADMIN, "posts", postId, "restore").andExpect(status().isOk());
		handle(ADMIN, "posts", postId, "restore").andExpect(status().isConflict());
		handle(ADMIN, "posts", postId, "delete").andExpect(status().isConflict());
	}
}
