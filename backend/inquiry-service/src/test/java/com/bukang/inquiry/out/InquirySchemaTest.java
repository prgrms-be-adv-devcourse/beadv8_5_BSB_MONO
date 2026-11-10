package com.bukang.inquiry.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.bukang.inquiry.domain.Inquiry;
import com.bukang.inquiry.domain.InquiryType;
import com.bukang.inquiry.support.InquiryTestBase;

/**
 * 테이블을 Flyway SQL로 만드는지(전용 기록 테이블), 글자 수 제한이 DB에서도 걸리는지 확인한다.
 * 엔티티와 컬럼이 맞는지는 ddl-auto: validate로 앱이 뜰 때 확인한다
 */
class InquirySchemaTest extends InquiryTestBase {
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("Flyway가 문의 전용 기록 테이블(flyway_schema_history_inquiry)에 V1을 남긴다")
	void flywayHistory() {
		Integer applied = jdbcTemplate.queryForObject(
			"select count(*) from flyway_schema_history_inquiry where version = '1' and success = true", Integer.class);

		assertThat(applied).isEqualTo(1);
	}

	@Test
	@DisplayName("제목 100자·본문 2000자를 넘는 값은 DB가 거절한다")
	void columnLengths() {
		inquiryRepository.saveAndFlush(Inquiry.create(ME, RACE, InquiryType.ETC, SELLER, "가".repeat(100), "나".repeat(2000)));

		assertThatThrownBy(() -> inquiryRepository.saveAndFlush(
			Inquiry.create(ME, RACE, InquiryType.ETC, SELLER, "가".repeat(101), "본문")))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> inquiryRepository.saveAndFlush(
			Inquiry.create(ME, RACE, InquiryType.ETC, SELLER, "제목", "나".repeat(2001))))
			.isInstanceOf(DataIntegrityViolationException.class);
	}
}
