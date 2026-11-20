package com.bukang.member.recordcert.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.bukang.common.shared.member.web.MemberIdHeader;
import com.bukang.member.out.MemberOutboxEventRepository;
import com.bukang.member.recordcert.domain.CourseClass;
import com.bukang.member.recordcert.domain.CourseRule;
import com.bukang.member.recordcert.out.CourseRulePort;
import com.bukang.member.recordcert.out.CourseRulePort.SellerCourseRule;
import com.bukang.member.recordcert.out.FileUploaderPort;
import com.bukang.member.recordcert.out.TicketPort;
import com.bukang.member.recordcert.out.TicketPort.Ticket;
import com.redis.testcontainers.RedisContainer;

/**
 * 같은 참가권으로 두 요청이 동시에 오면 하나는 201, 하나는 409이고 진행 중 제출이 하나만 남는다 (BUK-274, Docker 필요)
 * 잠금은 진짜 Redis(RedisSubmissionLock)를 쓰고, 다른 서비스 포트는 목으로 둔다.
 * 참가권 조회에서 두 요청이 모두 들어올 때까지 기다리게 해, 둘이 확실히 겹치게 만든다
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ApiV1RecordCertConcurrentSubmitTest {
	private static final long MEMBER_ID = 3;
	private static final long ORDER_ITEM_ID = 42;
	private static final String BODY = """
		{
			"orderItemId": 42, "fileId": 7,
			"roi": { "x": 10, "y": 20, "width": 300, "height": 400 },
			"netTimeSeconds": 10000, "raceDate": "2026-10-04", "courseClass": "FULL"
		}
		""";

	@Container
	private static final RedisContainer REDIS = new RedisContainer("redis:8.2");

	@DynamicPropertySource
	static void redis(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", REDIS::getRedisHost);
		registry.add("spring.data.redis.port", REDIS::getRedisPort);
	}

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private MemberOutboxEventRepository memberOutboxEventRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private TicketPort ticketPort;
	@MockitoBean
	private FileUploaderPort fileUploaderPort;
	@MockitoBean
	private CourseRulePort courseRulePort;

	@AfterEach
	void tearDown() {
		memberOutboxEventRepository.deleteAll();
		jdbcTemplate.update("delete from member_record_cert_submission");
		jdbcTemplate.update("delete from member_record_cert");
	}

	@Test
	@DisplayName("동시에 들어온 두 번째 요청은 409이고 제출 · 메시지는 한 벌만 남는다")
	void concurrentSubmit() throws Exception {
		CountDownLatch bothArrived = new CountDownLatch(2);
		willAnswer(invocation -> {
			bothArrived.countDown();
			bothArrived.await(3, TimeUnit.SECONDS);
			return Optional.of(new Ticket(MEMBER_ID, 11, 12, "PAID"));
		}).given(ticketPort).find(ORDER_ITEM_ID);
		willReturn(Optional.of(MEMBER_ID)).given(fileUploaderPort).findUploader(7);
		willReturn(new SellerCourseRule(9, new CourseRule(LocalDate.of(2025, 1, 1), 3 * 3600,
			Set.of(CourseClass.FULL)))).given(courseRulePort).find(12);

		// 잠금을 못 잡은 요청은 참가권 조회까지 오지 않으므로, 먼저 실패한 쪽이 기다림을 풀어 준다
		Callable<Integer> submit = () -> {
			int status = mockMvc.perform(post("/api/v1/member/record-certs")
					.header(MemberIdHeader.NAME, MEMBER_ID)
					.contentType(MediaType.APPLICATION_JSON)
					.content(BODY))
				.andReturn().getResponse().getStatus();
			bothArrived.countDown();
			return status;
		};
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<Future<Integer>> results = executor.invokeAll(List.of(submit, submit));

			assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(201, 409);
		} finally {
			executor.shutdown();
		}
		assertThat(jdbcTemplate.queryForObject("select count(*) from member_record_cert_submission", Integer.class))
			.isEqualTo(1);
		assertThat(memberOutboxEventRepository.count()).isEqualTo(2);
	}
}
