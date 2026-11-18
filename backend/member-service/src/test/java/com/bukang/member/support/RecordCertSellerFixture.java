package com.bukang.member.support;

import java.time.LocalDateTime;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.member.app.MemberFacade;
import com.bukang.member.domain.PayoutAccountHolderType;
import com.bukang.member.domain.Seller;
import com.bukang.member.domain.VerifiedPayoutAccount;
import com.bukang.member.out.MemberRepository;
import com.bukang.member.out.SellerRepository;
import com.bukang.member.out.TermsAgreementRepository;
import com.bukang.member.out.identity.InMemoryIdentityVerificationStore;
import com.bukang.member.recordcert.domain.FieldCheck;
import com.bukang.member.recordcert.domain.SubmissionStatus;

import lombok.RequiredArgsConstructor;

/**
 * 판매자 기록증 심사 테스트(BUK-276)의 Given. 회원 · 판매자는 API로, 기록증 · 제출은 SQL로 넣는다
 * 테스트 클래스에 @Import(RecordCertSellerFixture.class, InMemoryIdentityVerificationStore.Config.class)로 붙인다
 */
@TestComponent
@RequiredArgsConstructor
public class RecordCertSellerFixture {
	private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 18, 9, 0);

	private final MemberFacade memberFacade;
	private final InMemoryIdentityVerificationStore identityStore;
	private final MemberRepository memberRepository;
	private final SellerRepository sellerRepository;
	private final TermsAgreementRepository termsAgreementRepository;
	private final JdbcTemplate jdbcTemplate;
	private final TransactionTemplate transactionTemplate;

	public long member() {
		return memberFacade.join(MemberFixture.joinRequest().build(identityStore)).getId();
	}

	// 승인된 판매자의 회원 ID
	public long approvedSeller() {
		long memberId = member();
		// 승인은 아웃박스에 판매자 변경 메시지를 남겨 트랜잭션이 필요하다
		transactionTemplate.executeWithoutResult(status -> {
			Seller seller = sellerRepository.save(new Seller(memberRepository.getReferenceById(memberId),
				SellerType.INDIVIDUAL, null, null, "김러너",
				new VerifiedPayoutAccount("004", "1101230000678", "김러너", PayoutAccountHolderType.PERSONAL, CREATED),
				CREATED));
			seller.approve(1L, CREATED);
		});
		return memberId;
	}

	// 서버 확인은 네 칸 모두 check 값으로 둔다
	public long submission(long sellerMemberId, long raceId, SubmissionStatus status, FieldCheck check) {
		jdbcTemplate.update("""
			insert into member_record_cert (member_id, file_id, roi_x, roi_y, roi_width, roi_height,
				net_time_seconds, race_date, course_class, bib, field_boxes, created_at)
			values (7, 77, 10, 20, 300, 400, 5405, '2026-04-12', 'HALF', 'M-8363', '{"NET_TIME":[]}', ?)
			""", CREATED);
		long certId = jdbcTemplate.queryForObject("select max(record_cert_id) from member_record_cert", Long.class);
		jdbcTemplate.update("""
			insert into member_record_cert_submission (record_cert_id, member_id, order_item_id, race_id,
				race_course_id, seller_member_id, status, judgement_reasons, net_time_check, net_label_check,
				race_date_check, course_check, version, created_at, updated_at)
			values (?, 7, 700, ?, 70, ?, ?, 'TOO_FAST', ?, ?, ?, ?, 0, ?, ?)
			""", certId, raceId, sellerMemberId, status.name(), check.name(), check.name(), check.name(),
			check.name(), CREATED, CREATED);
		return jdbcTemplate.queryForObject("select max(submission_id) from member_record_cert_submission",
			Long.class);
	}

	public long submission(long sellerMemberId, long raceId, SubmissionStatus status) {
		return submission(sellerMemberId, raceId, status, FieldCheck.MATCH);
	}

	public void clear() {
		jdbcTemplate.update("delete from member_record_cert_submission");
		jdbcTemplate.update("delete from member_record_cert");
		jdbcTemplate.update("delete from member_outbox_event");
		sellerRepository.deleteAll();
		termsAgreementRepository.deleteAll();
		memberRepository.deleteAll();
	}
}
