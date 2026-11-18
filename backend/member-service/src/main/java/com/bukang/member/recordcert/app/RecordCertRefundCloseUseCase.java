package com.bukang.member.recordcert.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.member.recordcert.domain.RecordCertSubmission;
import com.bukang.member.recordcert.out.RecordCertRefundCloseRepository;

import lombok.RequiredArgsConstructor;

/**
 * 참가권 환불로 그 참가권의 기록증 제출을 취소됨으로 닫는다 (BUK-277, refund-close-spec)
 * 제출이 없으면 아무것도 하지 않는다. 결과 반영 · 판매자 심사와 동시에 저장하면 낙관적 잠금 예외가 커밋 때 그대로 나가고,
 * Kafka 재시도가 다시 읽어 닫는다 (judgement-spec 3장, 11/18)
 */
@Service
@RequiredArgsConstructor
public class RecordCertRefundCloseUseCase {
	private final RecordCertRefundCloseRepository recordCertRefundCloseRepository;

	@Transactional
	public void close(long orderItemId, LocalDateTime now) {
		for (RecordCertSubmission submission : recordCertRefundCloseRepository.findByOrderItemId(orderItemId)) {
			submission.cancel(now);
		}
	}
}
