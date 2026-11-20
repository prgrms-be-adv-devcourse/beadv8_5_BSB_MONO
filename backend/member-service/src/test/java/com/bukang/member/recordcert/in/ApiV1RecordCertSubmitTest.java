package com.bukang.member.recordcert.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.member.web.MemberIdHeader;
import com.bukang.member.domain.MemberOutboxEvent;
import com.bukang.member.out.MemberOutboxEventRepository;
import com.bukang.member.recordcert.domain.CourseClass;
import com.bukang.member.recordcert.domain.CourseRule;
import com.bukang.member.recordcert.domain.SubmissionStatus;
import com.bukang.member.recordcert.domain.exception.RecordCertCheckUnavailableException;
import com.bukang.member.recordcert.out.CourseRulePort;
import com.bukang.member.recordcert.out.CourseRulePort.SellerCourseRule;
import com.bukang.member.recordcert.out.FileUploaderPort;
import com.bukang.member.recordcert.out.SubmissionLock;
import com.bukang.member.recordcert.out.TicketPort;
import com.bukang.member.recordcert.out.TicketPort.Ticket;

/**
 * 기록증 제출 (BUK-274, submit-spec Testing Decisions의 심).
 * 참가권 · 올린 사람 · 종목 규칙 포트와 잠금을 목으로 두고 API를 바깥에서 부른다.
 * 응답, 내 제출 목록, 아웃박스(확인 요청 · 파일 연결 요청)만 본다
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiV1RecordCertSubmitTest {
	private static final String PATH = "/api/v1/member/record-certs";
	private static final long MEMBER_ID = 3;
	private static final long OTHER_MEMBER_ID = 4;
	private static final long SELLER_MEMBER_ID = 9;
	private static final long ORDER_ITEM_ID = 42;
	private static final long FILE_ID = 7;
	private static final long RACE_ID = 11;
	private static final long RACE_COURSE_ID = 12;
	private static final String LOCK_TOKEN = "token-1";
	private static final CourseRule RULE = new CourseRule(LocalDate.of(2025, 1, 1), 3 * 3600,
		Set.of(CourseClass.FULL));
	private static final String BODY = """
		{
			"orderItemId": 42,
			"fileId": 7,
			"roi": { "x": 10, "y": 20, "width": 300, "height": 400 },
			"netTimeSeconds": 10000,
			"raceDate": "2026-10-04",
			"courseClass": "FULL",
			"bib": "A123",
			"fieldBoxes": {
				"NET_TIME": [[1, 2], [3, 2], [3, 4], [1, 4]],
				"COURSE": [[5, 6], [7, 6], [7, 8], [5, 8]]
			},
			"specVersion": "1",
			"recModelSha256": "cd6e"
		}
		""";

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
	@MockitoBean
	private SubmissionLock submissionLock;

	// willReturn().given() 꼴로 써서, 앞에서 예외를 던지게 해 둔 목도 부르지 않고 다시 정한다
	@BeforeEach
	void setUp() {
		willReturn(Optional.of(new Ticket(MEMBER_ID, RACE_ID, RACE_COURSE_ID, "PAID")))
			.given(ticketPort).find(ORDER_ITEM_ID);
		willReturn(Optional.of(MEMBER_ID)).given(fileUploaderPort).findUploader(FILE_ID);
		willReturn(new SellerCourseRule(SELLER_MEMBER_ID, RULE)).given(courseRulePort).find(RACE_COURSE_ID);
		willReturn(Optional.of(LOCK_TOKEN)).given(submissionLock).tryLock(ORDER_ITEM_ID);
	}

	@AfterEach
	void tearDown() {
		memberOutboxEventRepository.deleteAll();
		jdbcTemplate.update("delete from member_record_cert_submission");
		jdbcTemplate.update("delete from member_record_cert");
	}

	@Test
	@DisplayName("제출하면 201로 확인 중을 돌려주고, 대회 · 종목은 참가권 값, 판매자는 규칙 조회 값으로 저장한다")
	void submit() throws Exception {
		submit(BODY)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.submissionId").isNumber())
			.andExpect(jsonPath("$.data.status").value("CHECKING"));

		mockMvc.perform(get(PATH).header(MemberIdHeader.NAME, MEMBER_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].orderItemId").value(ORDER_ITEM_ID))
			.andExpect(jsonPath("$.data[0].status").value("CHECKING"))
			.andExpect(jsonPath("$.data[0].reasons").isEmpty())
			.andExpect(jsonPath("$.data[0].submittedAt").isNotEmpty())
			.andExpect(jsonPath("$.data[0].netTimeCheck").doesNotExist());
		assertThat(jdbcTemplate.queryForMap(
			"select race_id, race_course_id, seller_member_id, member_id from member_record_cert_submission"))
			.containsEntry("race_id", RACE_ID)
			.containsEntry("race_course_id", RACE_COURSE_ID)
			.containsEntry("seller_member_id", SELLER_MEMBER_ID)
			.containsEntry("member_id", MEMBER_ID);
		verify(submissionLock).unlock(ORDER_ITEM_ID, LOCK_TOKEN);
	}

	@Test
	@DisplayName("제출하면 같은 트랜잭션에 확인 요청과 파일 연결 요청을 아웃박스에 남긴다")
	void submitAppendsOutbox() throws Exception {
		submit(BODY).andExpect(status().isCreated());

		List<MemberOutboxEvent> events = memberOutboxEventRepository.findAll();
		assertThat(events).extracting(MemberOutboxEvent::getTopic)
			.containsExactlyInAnyOrder(KafkaTopics.RECORD_CERT_CHECK_REQUESTED, KafkaTopics.RACE_FILE_LINK_REQUESTED);
		MemberOutboxEvent check = find(events, KafkaTopics.RECORD_CERT_CHECK_REQUESTED);
		assertThat(check.getPayload())
			.contains("\"fileId\":7", "\"specVersion\":\"1\"", "\"recModelSha256\":\"cd6e\"",
				"\"NET_TIME\":[[1.0,2.0]");
		MemberOutboxEvent fileLink = find(events, KafkaTopics.RACE_FILE_LINK_REQUESTED);
		assertThat(fileLink.getPayload())
			.contains("\"ownerId\":3", "\"refType\":\"RecordCert\"", "\"fileId\":7", "\"fileType\":\"RECORD_CERT\"");
	}

	@ParameterizedTest
	@ValueSource(strings = {"netTimeSeconds", "raceDate", "courseClass", "orderItemId", "fileId", "roi"})
	@DisplayName("확정 값 · 참가권 · 파일 · 읽을 부분 중 하나라도 없으면 400이고 아무것도 묻지 않는다")
	void missingValue(String field) throws Exception {
		submit(BODY.replace("\"" + field + "\"", "\"ignored\""))
			.andExpect(status().isBadRequest());

		verify(ticketPort, never()).find(anyLong());
		assertNothingSaved();
	}

	@Test
	@DisplayName("배번 · 칸별 상자 · 명세 버전이 없어도 제출된다")
	void optionalValues() throws Exception {
		submit("""
			{
				"orderItemId": 42, "fileId": 7,
				"roi": { "x": 10, "y": 20, "width": 300, "height": 400 },
				"netTimeSeconds": 10000, "raceDate": "2026-10-04", "courseClass": "FULL"
			}
			""")
			.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("없는 참가권이면 404, 남의 참가권이면 403")
	void ticketNotMine() throws Exception {
		given(ticketPort.find(ORDER_ITEM_ID)).willReturn(Optional.empty());
		submit(BODY)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RECORD_CERT_TICKET_NOT_FOUND"));

		given(ticketPort.find(ORDER_ITEM_ID))
			.willReturn(Optional.of(new Ticket(OTHER_MEMBER_ID, RACE_ID, RACE_COURSE_ID, "PAID")));
		submit(BODY)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("RECORD_CERT_TICKET_FORBIDDEN"));

		assertNothingSaved();
	}

	@Test
	@DisplayName("없거나 삭제된 파일이면 404, 남이 올린 파일이면 403")
	void fileNotMine() throws Exception {
		given(fileUploaderPort.findUploader(FILE_ID)).willReturn(Optional.empty());
		submit(BODY)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RECORD_CERT_FILE_NOT_FOUND"));

		given(fileUploaderPort.findUploader(FILE_ID)).willReturn(Optional.of(OTHER_MEMBER_ID));
		submit(BODY)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("RECORD_CERT_FILE_FORBIDDEN"));

		assertNothingSaved();
	}

	@ParameterizedTest
	@ValueSource(strings = {"ORDERED", "CANCELING", "CANCELED"})
	@DisplayName("결제 완료가 아닌 참가권이면 409")
	void ticketNotPaid(String ticketStatus) throws Exception {
		given(ticketPort.find(ORDER_ITEM_ID))
			.willReturn(Optional.of(new Ticket(MEMBER_ID, RACE_ID, RACE_COURSE_ID, ticketStatus)));

		submit(BODY)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RECORD_CERT_TICKET_NOT_PAID"));
		assertNothingSaved();
	}

	@Test
	@DisplayName("규칙이 없는 종목이면 409")
	void noRule() throws Exception {
		given(courseRulePort.find(RACE_COURSE_ID)).willReturn(new SellerCourseRule(SELLER_MEMBER_ID, null));

		submit(BODY)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RECORD_CERT_RULE_NOT_FOUND"));
		assertNothingSaved();
	}

	@ParameterizedTest
	@EnumSource(value = SubmissionStatus.class, names = {"CHECKING", "NEEDS_REVIEW", "AUTO_APPROVED", "APPROVED"})
	@DisplayName("진행 중이거나 승인된 제출이 있는 참가권이면 409이고 새 제출 · 메시지를 남기지 않는다")
	void submissionInProgress(SubmissionStatus existing) throws Exception {
		submit(BODY).andExpect(status().isCreated());
		jdbcTemplate.update("update member_record_cert_submission set status = ?", existing.name());
		memberOutboxEventRepository.deleteAll();

		submit(BODY)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RECORD_CERT_SUBMISSION_IN_PROGRESS"));
		assertThat(countSubmissions()).isEqualTo(1);
		assertThat(memberOutboxEventRepository.count()).isZero();
	}

	@ParameterizedTest
	@EnumSource(value = SubmissionStatus.class, names = {"REJECTED", "DENIED", "CANCELED"})
	@DisplayName("반려 · 승인 불가 · 취소됨 뒤에는 같은 참가권으로 다시 제출할 수 있다")
	void resubmit(SubmissionStatus ended) throws Exception {
		submit(BODY).andExpect(status().isCreated());
		jdbcTemplate.update("update member_record_cert_submission set status = ?", ended.name());

		submit(BODY).andExpect(status().isCreated());
		assertThat(countSubmissions()).isEqualTo(2);
	}

	@Test
	@DisplayName("같은 참가권의 제출이 처리 중이라 잠금을 못 잡으면 409이고 아무것도 묻지 않는다")
	void locked() throws Exception {
		given(submissionLock.tryLock(ORDER_ITEM_ID)).willReturn(Optional.empty());

		submit(BODY)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RECORD_CERT_SUBMIT_LOCKED"));
		verify(ticketPort, never()).find(anyLong());
		verify(submissionLock, never()).unlock(anyLong(), eq(LOCK_TOKEN));
		assertNothingSaved();
	}

	@Test
	@DisplayName("order · file · product · Redis가 응답하지 않으면 503이고 아무것도 저장하지 않는다")
	void unavailable() throws Exception {
		RecordCertCheckUnavailableException down = new RecordCertCheckUnavailableException("잠시 뒤 다시 시도해주세요.");

		willThrow(down).given(submissionLock).tryLock(ORDER_ITEM_ID);
		submit(BODY).andExpect(status().isServiceUnavailable());
		setUp();

		willThrow(down).given(ticketPort).find(ORDER_ITEM_ID);
		submit(BODY).andExpect(status().isServiceUnavailable());
		setUp();

		willThrow(down).given(fileUploaderPort).findUploader(FILE_ID);
		submit(BODY).andExpect(status().isServiceUnavailable());
		setUp();

		willThrow(down).given(courseRulePort).find(RACE_COURSE_ID);
		submit(BODY)
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("RECORD_CERT_CHECK_UNAVAILABLE"));

		assertNothingSaved();
	}

	@Test
	@DisplayName("확인에 실패해도 잡은 잠금은 푼다")
	void unlocksOnFailure() throws Exception {
		given(fileUploaderPort.findUploader(FILE_ID)).willReturn(Optional.of(OTHER_MEMBER_ID));

		submit(BODY).andExpect(status().isForbidden());
		verify(submissionLock).unlock(ORDER_ITEM_ID, LOCK_TOKEN);
	}

	@Test
	@DisplayName("내 제출 목록은 내 것만 최신순으로 보여 준다")
	void myList() throws Exception {
		submit(BODY).andExpect(status().isCreated());
		jdbcTemplate.update("update member_record_cert_submission set status = 'REJECTED', "
			+ "judgement_reasons = 'TOO_FAST,COURSE_OTHER'");
		submit(BODY).andExpect(status().isCreated());

		mockMvc.perform(get(PATH).header(MemberIdHeader.NAME, MEMBER_ID))
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].status").value("CHECKING"))
			.andExpect(jsonPath("$.data[1].status").value("REJECTED"))
			.andExpect(jsonPath("$.data[1].reasons[0]").value("TOO_FAST"))
			.andExpect(jsonPath("$.data[1].reasons[1]").value("COURSE_OTHER"));
		mockMvc.perform(get(PATH).header(MemberIdHeader.NAME, OTHER_MEMBER_ID))
			.andExpect(jsonPath("$.data.length()").value(0));
	}

	private ResultActions submit(String body) throws Exception {
		return mockMvc.perform(post(PATH)
			.header(MemberIdHeader.NAME, MEMBER_ID)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private void assertNothingSaved() {
		assertThat(countSubmissions()).isZero();
		assertThat(jdbcTemplate.queryForObject("select count(*) from member_record_cert", Integer.class)).isZero();
		assertThat(memberOutboxEventRepository.count()).isZero();
	}

	private int countSubmissions() {
		return jdbcTemplate.queryForObject("select count(*) from member_record_cert_submission", Integer.class);
	}

	private static MemberOutboxEvent find(List<MemberOutboxEvent> events, String topic) {
		return events.stream().filter(event -> event.getTopic().equals(topic)).findFirst().orElseThrow();
	}
}
