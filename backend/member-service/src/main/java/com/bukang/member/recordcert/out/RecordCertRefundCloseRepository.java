package com.bukang.member.recordcert.out;

import java.util.List;

import org.springframework.data.repository.Repository;

import com.bukang.member.recordcert.domain.RecordCertSubmission;

// 참가권 환불로 제출 닫기(BUK-277)의 조회. 승인 불가 뒤 새로 낸 제출이 있을 수 있어 한 참가권에 여러 개가 나온다
public interface RecordCertRefundCloseRepository extends Repository<RecordCertSubmission, Long> {
	// idx_record_cert_submission_order_item
	List<RecordCertSubmission> findByOrderItemId(long orderItemId);
}
