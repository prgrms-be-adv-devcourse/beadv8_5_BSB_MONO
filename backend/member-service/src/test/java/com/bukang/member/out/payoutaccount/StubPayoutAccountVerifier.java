package com.bukang.member.out.payoutaccount;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 테스트용 계좌 확인. 받은 조회 요청과 그때 DB 트랜잭션이 열려 있었는지를 남기고, 정해 둔 답을 돌려준다
 * 기본 답은 신청자 이름이라 본인 명의 확인이 통과한다. @Import(StubPayoutAccountVerifier.Config.class)로 바꿔 끼운다
 */
public class StubPayoutAccountVerifier implements PayoutAccountVerifier {
	private final List<PayoutAccountInquiry> inquiries = new CopyOnWriteArrayList<>();
	private final List<Boolean> transactionActive = new CopyOnWriteArrayList<>();
	private volatile Function<PayoutAccountInquiry, String> answer = PayoutAccountInquiry::applicantName;

	@Override
	public String verify(PayoutAccountInquiry inquiry) {
		inquiries.add(inquiry);
		transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
		return answer.apply(inquiry);
	}

	// 예금주 이름을 돌려주거나 예외를 던지는 답을 정한다
	public void answer(Function<PayoutAccountInquiry, String> answer) {
		this.answer = answer;
	}

	public List<PayoutAccountInquiry> inquiries() {
		return inquiries;
	}

	public List<Boolean> transactionActive() {
		return transactionActive;
	}

	public void reset() {
		inquiries.clear();
		transactionActive.clear();
		answer = PayoutAccountInquiry::applicantName;
	}

	@TestConfiguration
	public static class Config {
		@Bean
		@Primary
		public StubPayoutAccountVerifier stubPayoutAccountVerifier() {
			return new StubPayoutAccountVerifier();
		}
	}
}
