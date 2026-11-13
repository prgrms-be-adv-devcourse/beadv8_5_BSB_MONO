package com.bukang.board.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bukang.board.domain.BoardMember;
import com.bukang.board.out.BoardCommentRepository;
import com.bukang.board.out.BoardInboxKafkaEventRepository;
import com.bukang.board.out.BoardMemberRepository;
import com.bukang.board.out.BoardPostLikeRepository;
import com.bukang.board.out.BoardPostRepository;
import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.member.web.MemberIdHeader;

/**
 * 게시판 테스트 공통 준비. 표는 Flyway SQL로 만들고(운영과 같은 제약), Redis·Kafka는 가짜로 바꿔
 * 도배 제한과 파일 연결 메시지를 확인한다. 도커 없이 돈다
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BoardTestBase {
	protected static final String POSTS = "/api/v1/board/posts";
	protected static final String COMMENTS = "/api/v1/board/comments";
	protected static final long ME = 1L;
	protected static final long OTHER = 2L;

	@Autowired
	protected MockMvc mockMvc;
	@Autowired
	protected JsonConverter jsonConverter;
	@Autowired
	protected JdbcTemplate jdbcTemplate;
	@Autowired
	protected BoardPostRepository postRepository;
	@Autowired
	protected BoardCommentRepository commentRepository;
	@Autowired
	protected BoardPostLikeRepository likeRepository;
	@Autowired
	protected BoardMemberRepository memberRepository;
	@Autowired
	protected BoardInboxKafkaEventRepository inboxRepository;

	@MockitoBean
	protected StringRedisTemplate redisTemplate;
	@MockitoBean
	protected KafkaTemplate<String, String> kafkaTemplate;

	protected ValueOperations<String, String> valueOperations;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void resetBoard() {
		jdbcTemplate.update("delete from board_report");
		likeRepository.deleteAll();
		commentRepository.deleteAll();
		postRepository.deleteAll();
		inboxRepository.deleteAll();
		memberRepository.deleteAll();
		LocalDateTime now = LocalDateTime.now();
		memberRepository.save(new BoardMember(ME, "러너하나", now, now));
		memberRepository.save(new BoardMember(OTHER, "러너둘", now, now));

		// 기본은 도배 제한에 걸리지 않고, Kafka 전송은 성공한다
		valueOperations = mock(ValueOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
		when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
	}

	protected static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, Long memberId) {
		return memberId == null ? builder : builder.header(MemberIdHeader.NAME, memberId);
	}

	protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
		return builder.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	protected String postBody(String title, String content) {
		return body("title", title, "content", content);
	}

	protected String body(Object... keyValues) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			map.put((String)keyValues[i], keyValues[i + 1]);
		}
		return jsonConverter.toJson(map);
	}

	protected ResultActions createPost(Long memberId, String body) throws Exception {
		return mockMvc.perform(json(as(post(POSTS), memberId), body));
	}

	/** 글을 만들고 ID를 돌려준다 */
	protected long newPost(long memberId, String title) throws Exception {
		String response = createPost(memberId, postBody(title, "본문")).andReturn().getResponse()
			.getContentAsString();
		return idOf(response, "postId");
	}

	protected ResultActions createComment(Long memberId, long postId, String body) throws Exception {
		return mockMvc.perform(json(as(post(POSTS + "/" + postId + "/comments"), memberId), body));
	}

	protected long newComment(long memberId, long postId, String content, Long parentId) throws Exception {
		String body = parentId == null ? body("content", content) : body("content", content, "parentId", parentId);
		String response = createComment(memberId, postId, body).andReturn().getResponse().getContentAsString();
		return idOf(response, "commentId");
	}

	protected ResultActions getPost(Long memberId, long postId) throws Exception {
		return mockMvc.perform(as(get(POSTS + "/" + postId), memberId));
	}

	protected ResultActions getComments(Long memberId, long postId) throws Exception {
		return mockMvc.perform(as(get(POSTS + "/" + postId + "/comments"), memberId));
	}

	protected ResultActions putPost(Long memberId, long postId, String body) throws Exception {
		return mockMvc.perform(json(as(put(POSTS + "/" + postId), memberId), body));
	}

	protected ResultActions deletePost(Long memberId, long postId) throws Exception {
		return mockMvc.perform(as(delete(POSTS + "/" + postId), memberId));
	}

	protected ResultActions putComment(Long memberId, long commentId, String body) throws Exception {
		return mockMvc.perform(json(as(put(COMMENTS + "/" + commentId), memberId), body));
	}

	protected ResultActions deleteComment(Long memberId, long commentId) throws Exception {
		return mockMvc.perform(as(delete(COMMENTS + "/" + commentId), memberId));
	}

	protected ResultActions putLike(Long memberId, long postId) throws Exception {
		return mockMvc.perform(as(put(POSTS + "/" + postId + "/like"), memberId));
	}

	protected ResultActions deleteLike(Long memberId, long postId) throws Exception {
		return mockMvc.perform(as(delete(POSTS + "/" + postId + "/like"), memberId));
	}

	protected ResultActions reportPost(Long memberId, long postId, String reason) throws Exception {
		return mockMvc.perform(json(as(post(POSTS + "/" + postId + "/report"), memberId), body("reason", reason)));
	}

	protected ResultActions reportComment(Long memberId, long commentId, String reason) throws Exception {
		return mockMvc.perform(
			json(as(post(COMMENTS + "/" + commentId + "/report"), memberId), body("reason", reason)));
	}

	/** 신고 자동 숨김(다음 이슈)이 하는 일을 흉내 낸다 */
	protected void hidePost(long postId) {
		jdbcTemplate.update("update board_post set status = 'HIDDEN' where id = ?", postId);
	}

	private long idOf(String response, String field) {
		Map<?, ?> data = (Map<?, ?>)jsonConverter.fromJson(response, Map.class).get("data");
		return ((Number)data.get(field)).longValue();
	}

	protected static String longText(int length) {
		return "가".repeat(length);
	}

	protected static List<Long> fileIds(long... ids) {
		return java.util.Arrays.stream(ids).boxed().toList();
	}
}
