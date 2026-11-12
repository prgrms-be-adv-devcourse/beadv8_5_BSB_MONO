package com.bukang.board.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.bukang.board.support.BoardTestBase;

/**
 * 글 목록·검색 (GET /api/v1/board/posts?category&sort&q&cursor&size). 응답은 글 배열이고, 다음 쪽이 있으면 X-Next-Cursor 헤더를 준다
 */
class BoardPostListApiTest extends BoardTestBase {
	private static final String NEXT = "X-Next-Cursor";

	private MvcResult list(Long memberId, String query) throws Exception {
		return mockMvc.perform(as(get(POSTS + query), memberId)).andReturn();
	}

	private List<Number> ids(MvcResult result) throws Exception {
		List<?> data = (List<?>)jsonConverter.fromJson(result.getResponse().getContentAsString(), Map.class)
			.get("data");
		List<Number> ids = new ArrayList<>();
		for (Object item : data) {
			ids.add((Number)((Map<?, ?>)item).get("postId"));
		}
		return ids;
	}

	private long postIn(long memberId, String category, String title, String content) throws Exception {
		String response = createPost(memberId, body("category", category, "title", title, "content", content))
			.andReturn().getResponse().getContentAsString();
		return ((Number)((Map<?, ?>)jsonConverter.fromJson(response, Map.class).get("data"))
			.get("postId")).longValue();
	}

	private void setLikes(long postId, long likes) {
		jdbcTemplate.update("update board_post set like_count = ? where id = ?", likes, postId);
	}

	@Test
	@DisplayName("로그인 없이 최신순 목록을 읽고, 필드 이름은 프론트 계약(PostSummary)과 같다")
	void latestShape() throws Exception {
		long first = postIn(ME, "FREE", "첫 글", "첫 본문");
		long second = postIn(OTHER, "QUESTION", "둘째 글", "가".repeat(300));
		newComment(OTHER, first, "댓글", null);
		long deletedComment = newComment(OTHER, first, "지울 댓글", null);
		deleteComment(OTHER, deletedComment);
		putLike(OTHER, second);

		mockMvc.perform(get(POSTS)).andExpect(status().isOk())
			.andExpect(header().doesNotExist(NEXT))
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].postId").value((int)second))
			.andExpect(jsonPath("$.data[0].category").value("QUESTION"))
			.andExpect(jsonPath("$.data[0].categoryLabel").value("질문"))
			.andExpect(jsonPath("$.data[0].title").value("둘째 글"))
			.andExpect(jsonPath("$.data[0].excerpt").value("가".repeat(100)))
			.andExpect(jsonPath("$.data[0].writer.nickname").value("러너둘"))
			.andExpect(jsonPath("$.data[0].writer.seller").value(false))
			.andExpect(jsonPath("$.data[0].writer.withdrawn").value(false))
			.andExpect(jsonPath("$.data[0].createdAt").isNotEmpty())
			.andExpect(jsonPath("$.data[0].likeCount").value(1))
			.andExpect(jsonPath("$.data[0].commentCount").value(0))
			.andExpect(jsonPath("$.data[0].thumbnailUrl").isEmpty())
			.andExpect(jsonPath("$.data[1].postId").value((int)first))
			.andExpect(jsonPath("$.data[1].excerpt").value("첫 본문"))
			.andExpect(jsonPath("$.data[1].commentCount").value(1));
	}

	@Test
	@DisplayName("말머리로 거른다")
	void filterByCategory() throws Exception {
		postIn(ME, "FREE", "자유", "본문");
		long review = postIn(ME, "RACE_REVIEW", "후기", "본문");

		assertThat(ids(list(null, "?category=RACE_REVIEW"))).containsExactly((int)review);
	}

	@Test
	@DisplayName("지운 글과 신고로 숨겨진 글은 쓴 사람에게도 목록에 나오지 않는다")
	void hiddenAndDeletedExcluded() throws Exception {
		long visible = newPost(ME, "보이는 글");
		long deleted = newPost(ME, "지운 글");
		deletePost(ME, deleted);
		long hidden = newPost(ME, "숨긴 글");
		hidePost(hidden);

		assertThat(ids(list(null, ""))).containsExactly((int)visible);
		assertThat(ids(list(ME, ""))).containsExactly((int)visible);
		assertThat(ids(list(null, "?q=글"))).containsExactly((int)visible);
	}

	@Test
	@DisplayName("탈퇴한 회원의 글은 글쓴이를 「탈퇴한 회원」으로 보여 준다")
	void withdrawnWriter() throws Exception {
		newPost(ME, "글");
		jdbcTemplate.update("update board_member set withdrawn = true where id = ?", ME);

		mockMvc.perform(get(POSTS)).andExpect(jsonPath("$.data[0].writer.nickname").value("탈퇴한 회원"))
			.andExpect(jsonPath("$.data[0].writer.withdrawn").value(true));
	}

	@Test
	@DisplayName("인기순은 최근 7일 안에 쓴 글만 좋아요 많은 순으로 주고, 같으면 최근 글이 먼저다")
	void popular() throws Exception {
		long old = newPost(ME, "오래된 인기 글");
		setLikes(old, 100);
		jdbcTemplate.update("update board_post set create_date = ? where id = ?", LocalDateTime.now().minusDays(8), old);
		long low = newPost(ME, "낮음");
		setLikes(low, 1);
		long tieOlder = newPost(ME, "동점 먼저 쓴 글");
		setLikes(tieOlder, 5);
		long tieNewer = newPost(ME, "동점 나중에 쓴 글");
		setLikes(tieNewer, 5);

		assertThat(ids(list(null, "?sort=POPULAR"))).containsExactly((int)tieNewer, (int)tieOlder, (int)low);
	}

	@Test
	@DisplayName("제목이나 본문에 검색어가 들어 있는 글을 찾고, %·_는 글자 그대로 찾는다")
	void search() throws Exception {
		long inTitle = postIn(ME, "FREE", "한강 코스", "본문");
		long inContent = postIn(ME, "FREE", "제목", "여의도 한강 공원");
		postIn(ME, "FREE", "다른 글", "관계 없음");
		long percent = postIn(ME, "QUESTION", "100% 완주", "본문");
		postIn(ME, "FREE", "100 완주", "본문");

		assertThat(ids(list(null, "?q=한강"))).containsExactly((int)inContent, (int)inTitle);
		assertThat(ids(list(null, "?q=" + "%25"))).containsExactly((int)percent);
		assertThat(ids(list(null, "?q=" + "_"))).isEmpty();
		assertThat(ids(list(null, "?q=없는말"))).isEmpty();
		assertThat(ids(list(null, "?q=한강&category=QUESTION"))).isEmpty();
		assertThat(ids(list(null, "?q=%20%20"))).hasSize(5);
	}

	@Test
	@DisplayName("기본은 20개씩이고 다음 쪽이 있으면 X-Next-Cursor를 준다. 이어 부르면 겹치지 않는다")
	void paging() throws Exception {
		List<Long> created = new ArrayList<>();
		for (int i = 0; i < 25; i++) {
			created.add(newPost(ME, "글" + i));
		}

		MvcResult first = list(null, "");
		assertThat(ids(first)).hasSize(20);
		String cursor = first.getResponse().getHeader(NEXT);
		assertThat(cursor).isNotBlank();

		// 첫 쪽을 본 뒤 새 글이 올라와도 다음 쪽에 같은 글이 나오지 않는다
		newPost(ME, "새 글");
		MvcResult second = list(null, "?cursor=" + cursor);
		assertThat(ids(second)).hasSize(5);
		assertThat(second.getResponse().getHeader(NEXT)).isNull();
		List<Number> all = new ArrayList<>(ids(first));
		all.addAll(ids(second));
		assertThat(all).doesNotHaveDuplicates().hasSize(25);
	}

	@Test
	@DisplayName("size로 개수를 정하고, 딱 맞게 끝나면 X-Next-Cursor가 없다")
	void sizeAndExactEnd() throws Exception {
		for (int i = 0; i < 4; i++) {
			newPost(ME, "글" + i);
		}

		MvcResult exact = list(null, "?size=4");
		assertThat(ids(exact)).hasSize(4);
		assertThat(exact.getResponse().getHeader(NEXT)).isNull();
		MvcResult partial = list(null, "?size=3");
		assertThat(ids(partial)).hasSize(3);
		assertThat(partial.getResponse().getHeader(NEXT)).isNotBlank();
	}

	@Test
	@DisplayName("인기순도 같은 좋아요 수가 쪽 경계에 걸려도 빠지거나 겹치지 않는다")
	void popularPaging() throws Exception {
		List<Long> created = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			long id = newPost(ME, "글" + i);
			setLikes(id, i < 4 ? 3 : 1);
			created.add(id);
		}

		MvcResult first = list(null, "?sort=POPULAR&size=2");
		MvcResult second = list(null, "?sort=POPULAR&size=2&cursor=" + first.getResponse().getHeader(NEXT));
		MvcResult third = list(null, "?sort=POPULAR&size=2&cursor=" + second.getResponse().getHeader(NEXT));

		List<Number> all = new ArrayList<>(ids(first));
		all.addAll(ids(second));
		all.addAll(ids(third));
		assertThat(all).containsExactly(created.get(3).intValue(), created.get(2).intValue(), created.get(1).intValue(),
			created.get(0).intValue(), created.get(5).intValue(), created.get(4).intValue());
		assertThat(third.getResponse().getHeader(NEXT)).isNull();
	}

	@Test
	@DisplayName("잘못된 sort·category·cursor·size는 400이다")
	void invalidParams() throws Exception {
		newPost(ME, "글");

		mockMvc.perform(get(POSTS + "?sort=HOT")).andExpect(status().isBadRequest());
		mockMvc.perform(get(POSTS + "?category=NEWS")).andExpect(status().isBadRequest());
		mockMvc.perform(get(POSTS + "?cursor=abc")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BOARD_INVALID_LIST_REQUEST"));
		mockMvc.perform(get(POSTS + "?sort=POPULAR&cursor=5")).andExpect(status().isBadRequest());
		mockMvc.perform(get(POSTS + "?size=0")).andExpect(status().isBadRequest());
		mockMvc.perform(get(POSTS + "?size=51")).andExpect(status().isBadRequest());
		mockMvc.perform(get(POSTS + "?q=" + "가".repeat(51))).andExpect(status().isBadRequest());
	}
}
