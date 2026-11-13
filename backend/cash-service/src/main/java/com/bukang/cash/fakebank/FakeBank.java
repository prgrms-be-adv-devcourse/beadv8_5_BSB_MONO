package com.bukang.cash.fakebank;

import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 실제 은행 송금 API처럼 움직이는 가짜 은행. 송금 기록은 DB에 남기고, 결과 설정만 메모리에 둔다 (앱을 다시 켜면 성공으로 돌아간다)
 * 같은 송금 ID로 다시 요청하면 새로 송금하지 않고 처음 기록을 돌려준다 (은행의 중복 송금 방지)
 */
@Component
@RequiredArgsConstructor
public class FakeBank {
	private final FakeBankTransferRepository fakeBankTransferRepository;
	private volatile Mode mode = Mode.SUCCESS;

	public enum Mode {
		SUCCESS,
		FAIL,
		PENDING_THEN_SUCCESS,
		PENDING_THEN_FAIL,
		// 은행은 송금을 마쳤는데 응답이 끊김
		DROP
	}

	public enum Status {
		PROCESSING,
		COMPLETED,
		FAILED
	}

	public void changeMode(Mode mode) {
		this.mode = mode;
	}

	// 같은 송금 ID가 동시에 오면 유니크 제약이 한쪽을 막아 500이 되고, 결제는 이를 결과 모름으로 받아 재확인이 맞춘다
	public FakeBankTransfer transfer(String transferId, long sellerId, long amount) {
		Mode current = mode;
		Status status = current == Mode.FAIL || current == Mode.PENDING_THEN_FAIL ? Status.FAILED : Status.COMPLETED;
		return fakeBankTransferRepository.findByTransferId(transferId).orElseGet(() ->
			fakeBankTransferRepository.save(new FakeBankTransfer(transferId, sellerId, amount, status, current)));
	}

	public Optional<FakeBankTransfer> find(String transferId) {
		return fakeBankTransferRepository.findByTransferId(transferId);
	}
}
