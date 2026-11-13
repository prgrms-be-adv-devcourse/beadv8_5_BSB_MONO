package com.bukang.cash.fakebank;

import static jakarta.persistence.EnumType.STRING;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가짜 은행의 송금 기록. 진짜 은행처럼 앱을 다시 켜도 남아야, 재확인이 "없음"을 "은행에 닿지 않음"으로 믿을 수 있다
 * 송금 ID 유니크 제약으로 같은 송금이 두 번 기록되지 않는다
 */
@Entity
@NoArgsConstructor
@Getter
@Table(name = "CASH_FAKEBANK_TRANSFER", uniqueConstraints = {
	@UniqueConstraint(name = "uk_cash_fakebank_transfer_transfer_id", columnNames = {"transfer_id"})
})
public class FakeBankTransfer extends BaseIdAndTime {
	@Column(nullable = false, length = 100)
	private String transferId;

	@Column(nullable = false)
	private long sellerId;

	@Column(nullable = false)
	private long amount;

	// 조회했을 때 보이는 최종 결과
	@Enumerated(STRING)
	@Column(nullable = false, length = 20)
	private FakeBank.Status status;

	// 송금 때의 결과 설정. 같은 송금 ID로 다시 요청하면 처음과 같은 응답(처리 중, 응답 끊김 등)을 돌려준다
	@Enumerated(STRING)
	@Column(nullable = false, length = 20)
	private FakeBank.Mode resultMode;

	public FakeBankTransfer(
		String transferId, long sellerId, long amount, FakeBank.Status status, FakeBank.Mode resultMode
	) {
		this.transferId = transferId;
		this.sellerId = sellerId;
		this.amount = amount;
		this.status = status;
		this.resultMode = resultMode;
	}
}
