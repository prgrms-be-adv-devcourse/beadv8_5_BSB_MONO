package com.bukang.member.recordcert.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.kafka.dlt.KafkaDltMessage;
import com.bukang.common.shared.recordcert.event.RecordCertCheckedEvent;
import com.bukang.member.app.MemberKafkaRetry;
import com.bukang.member.out.MemberDltKafkaEventRepository;
import com.bukang.member.recordcert.domain.CertValues;
import com.bukang.member.recordcert.domain.CourseClass;
import com.bukang.member.recordcert.domain.RecordCert;
import com.bukang.member.recordcert.domain.RecordCertSubmission;
import com.bukang.member.recordcert.domain.Roi;
import com.bukang.member.recordcert.domain.SubmissionStatus;
import com.bukang.member.recordcert.out.RecordCertRepository;
import com.bukang.member.recordcert.out.RecordCertSubmissionRepository;

/**
 * 실패 토픽으로 간 서버 확인 결과 처리 (BUK-275, server-check-spec Testing Decisions의 심).
 * 실패 토픽 메시지 하나를 처리한 뒤 제출 상태와 실패 기록만 본다
 */
@SpringBootTest
@ActiveProfiles("test")
class RecordCertCheckDltTest {
	private static final String DLT_TOPIC = KafkaTopics.RECORD_CERT_CHECKED + MemberKafkaRetry.DLT_TOPIC_SUFFIX;

	@Autowired
	private RecordCertFacade recordCertFacade;
	@Autowired
	private RecordCertRepository recordCertRepository;
	@Autowired
	private RecordCertSubmissionRepository recordCertSubmissionRepository;
	@Autowired
	private MemberDltKafkaEventRepository memberDltKafkaEventRepository;
	@Autowired
	private JsonConverter jsonConverter;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@AfterEach
	void tearDown() {
		memberDltKafkaEventRepository.deleteAll();
		recordCertSubmissionRepository.deleteAll();
		recordCertRepository.deleteAll();
	}

	@Test
	@DisplayName("실패 토픽으로 간 결과는 그 제출을 심사 필요로 넘기고 실패를 남긴다")
	void dltMovesSubmissionToReview() {
		long id = submission();

		recordCertFacade.handleCheckResultDlt(dlt(id));

		assertThat(recordCertSubmissionRepository.findById(id).orElseThrow().getStatus())
			.isEqualTo(SubmissionStatus.NEEDS_REVIEW);
		assertThat(memberDltKafkaEventRepository.findAll()).singleElement()
			.satisfies(event -> assertThat(event.getOriginalTopic()).isEqualTo(KafkaTopics.RECORD_CERT_CHECKED));
	}

	@Test
	@DisplayName("확인 중이 아닌 제출은 상태를 바꾸지 않고 실패만 남긴다")
	void dltKeepsNonCheckingSubmission() {
		long id = submission();
		jdbcTemplate.update("update member_record_cert_submission set status = 'CANCELED' where submission_id = ?",
			id);

		recordCertFacade.handleCheckResultDlt(dlt(id));

		assertThat(recordCertSubmissionRepository.findById(id).orElseThrow().getStatus())
			.isEqualTo(SubmissionStatus.CANCELED);
		assertThat(memberDltKafkaEventRepository.findAll()).hasSize(1);
	}

	private KafkaDltMessage dlt(long submissionId) {
		String payload = jsonConverter.toJson(RecordCertCheckedEvent.read(submissionId, Map.of()));
		return new KafkaDltMessage(KafkaTopics.RECORD_CERT_CHECKED, DLT_TOPIC, 0, 0, String.valueOf(submissionId),
			payload, null, "RecordCertCheckUnavailableException", "product 응답 없음");
	}

	private long submission() {
		LocalDateTime now = LocalDateTime.now();
		RecordCert cert = recordCertRepository.save(RecordCert.create(3, 7, new Roi(0, 0, 10, 10),
			new CertValues(10_000, LocalDate.of(2025, 10, 4), CourseClass.FULL, null), "{}", "1", "sha", now));
		return recordCertSubmissionRepository.save(RecordCertSubmission.submit(cert.getId(), 3, 42, 11, 12, 9, now))
			.getId();
	}
}
