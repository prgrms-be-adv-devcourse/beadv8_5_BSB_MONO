package com.bukang.order.in;

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

import com.bukang.order.domain.Gender;
import com.bukang.order.domain.ParticipantInfo;
import com.bukang.order.domain.Participation;
import com.bukang.order.out.ParticipationRepository;

// 참가자·비상 연락처 휴대폰 번호는 암호문으로 저장되고, 읽을 때는 원래 번호로 돌아온다 (BUK-92)
@SpringBootTest
@ActiveProfiles("test")
class ParticipantPhoneEncryptRunnerTest {
	private static final String PHONE = "010-1234-5678";
	private static final String EMERGENCY_PHONE = "010-9876-5432";

	@Autowired
	private ParticipationRepository participationRepository;
	@Autowired
	private ParticipantPhoneEncryptRunner runner;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@AfterEach
	void tearDown() {
		participationRepository.deleteAll();
	}

	@Test
	@DisplayName("새로 저장한 휴대폰 번호는 DB에 암호문으로 남고, 엔티티로 읽으면 원래 번호다")
	void savesEncrypted() {
		long id = participationRepository.save(participation(1)).getId();

		Map<String, Object> raw = rawPhones(id);
		assertThat(raw.get("participant_phone")).isNotEqualTo(PHONE);
		assertThat(raw.get("emergency_contact_phone")).isNotEqualTo(EMERGENCY_PHONE);
		ParticipantInfo read = participationRepository.findById(id).orElseThrow().getParticipant();
		assertThat(read.getPhone()).isEqualTo(PHONE);
		assertThat(read.getEmergencyContactPhone()).isEqualTo(EMERGENCY_PHONE);
	}

	@Test
	@DisplayName("평문으로 남은 번호만 암호화하고, 다시 돌리면 아무것도 바꾸지 않는다")
	void encryptsPlaintextOnce() {
		long plainId = participationRepository.save(participation(1)).getId();
		long encryptedId = participationRepository.save(participation(2)).getId();
		jdbcTemplate.update("update order_participation set participant_phone = ?, emergency_contact_phone = ? "
			+ "where id = ?", PHONE, EMERGENCY_PHONE, plainId);
		Map<String, Object> before = rawPhones(encryptedId);

		assertThat(runner.encryptPlaintextPhones()).isEqualTo(2);
		assertThat(runner.encryptPlaintextPhones()).isZero();

		assertThat(rawPhones(plainId).get("participant_phone")).isNotEqualTo(PHONE);
		assertThat(rawPhones(encryptedId)).isEqualTo(before);
		ParticipantInfo read = participationRepository.findById(plainId).orElseThrow().getParticipant();
		assertThat(read.getPhone()).isEqualTo(PHONE);
		assertThat(read.getEmergencyContactPhone()).isEqualTo(EMERGENCY_PHONE);
	}

	private Map<String, Object> rawPhones(long id) {
		return jdbcTemplate.queryForMap(
			"select participant_phone, emergency_contact_phone from order_participation where id = ?", id);
	}

	private static Participation participation(long memberId) {
		ParticipantInfo info = new ParticipantInfo("김러너", LocalDate.of(1994, 6, 25), Gender.MALE, PHONE, "김가족",
			EMERGENCY_PHONE, null, null);
		return Participation.individual(memberId, 100, 10, info, LocalDateTime.now());
	}
}
