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
	@DisplayName("제목 100자·본문 1000자·답변 2000자를 넘는 값은 DB가 거절한다")
	void columnLengths() {
		inquiryRepository.saveAndFlush(
			Inquiry.create(ME, RACE, ORDER, InquiryType.ETC, SELLER, "가".repeat(100), "나".repeat(1000)));

		assertThatThrownBy(() -> inquiryRepository.saveAndFlush(
			Inquiry.create(ME, RACE, ORDER, InquiryType.ETC, SELLER, "가".repeat(101), "본문")))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> inquiryRepository.saveAndFlush(
			Inquiry.create(ME, RACE, ORDER, InquiryType.ETC, SELLER, "제목", "나".repeat(1001))))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("답변 2000자는 들어가고 2001자는 DB가 거절한다")
	void answerLength() {
		Inquiry saved = saveSellerInquiry(ME, RACE, SELLER);

		jdbcTemplate.update("update inquiry_inquiry set answer = ? where id = ?", "다".repeat(2000), saved.getId());
		assertThatThrownBy(() -> jdbcTemplate.update("update inquiry_inquiry set answer = ? where id = ?",
			"다".repeat(2001), saved.getId())).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("판매자 문의는 대회·주문·판매자 회원 ID가 비어 있어도 저장된다")
	void sellerSupportColumnsNullable() {
		Inquiry saved = saveSellerSupportInquiry(APPROVED_SELLER);

		Inquiry found = inquiryRepository.findById(saved.getId()).orElseThrow();
		assertThat(found.getRaceId()).isNull();
		assertThat(found.getOrderId()).isNull();
		assertThat(found.getSellerMemberId()).isNull();
		assertThat(found.getDeletedAt()).isNull();
	}

	@Test
	@DisplayName("목록 쿼리가 인덱스를 쓴다: 판매자 목록은 idx_inquiry_seller, 관리자 목록은 idx_inquiry_status, 관리자 목록의 담당 필터는 idx_inquiry_assignee")
	void listQueriesUseIndexes() {
		for (int i = 0; i < 300; i++) {
			jdbcTemplate.update("insert into inquiry_inquiry (member_id, race_id, order_id, type, assignee, "
				+ "seller_member_id, title, body, status) values (?, 100, 500, 'RACE_VENUE', ?, ?, 't', 'b', ?)",
				i % 7, i % 4 == 0 ? "ADMIN" : "SELLER", 10 + i % 5, i % 3 == 0 ? "ANSWERED" : "WAITING");
		}
		jdbcTemplate.execute("analyze");

		String sellerPlan = String.join(" ", jdbcTemplate.queryForList(
			"explain select * from inquiry_inquiry where seller_member_id = 10 and assignee = 'SELLER' "
				+ "and deleted_at is null order by status desc, id desc limit 20", String.class));
		String adminPlan = String.join(" ", jdbcTemplate.queryForList(
			"explain select * from inquiry_inquiry where deleted_at is null order by status desc, id desc limit 20",
			String.class));
		String filteredPlan = String.join(" ", jdbcTemplate.queryForList(
			"explain select * from inquiry_inquiry where status = 'WAITING' and deleted_at is null "
				+ "order by id desc limit 20", String.class));

		String adminAssigneePlan = String.join(" ", jdbcTemplate.queryForList(
			"explain select * from inquiry_inquiry where assignee = 'ADMIN' and deleted_at is null "
				+ "order by status desc, id desc limit 20", String.class));
		String adminAssigneeStatusPlan = String.join(" ", jdbcTemplate.queryForList(
			"explain select * from inquiry_inquiry where assignee = 'ADMIN' and status = 'WAITING' "
				+ "and deleted_at is null order by id desc limit 20", String.class));

		assertThat(adminAssigneePlan.toLowerCase()).contains("idx_inquiry_assignee");
		assertThat(adminAssigneeStatusPlan.toLowerCase()).contains("idx_inquiry_assignee");
		assertThat(sellerPlan.toLowerCase()).contains("idx_inquiry_seller");
		assertThat(adminPlan.toLowerCase()).contains("idx_inquiry_status");
		assertThat(filteredPlan.toLowerCase()).contains("idx_inquiry_status");
	}
}
