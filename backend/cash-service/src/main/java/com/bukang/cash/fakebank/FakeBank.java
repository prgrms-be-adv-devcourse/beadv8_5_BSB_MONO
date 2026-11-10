package com.bukang.cash.fakebank;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 실제 은행 송금 API처럼 움직이는 가짜 은행. 송금 기록과 결과 설정을 메모리에만 둔다 (앱을 다시 켜면 사라진다)
 * 같은 송금 ID로 다시 요청하면 새로 송금하지 않고 처음 기록을 돌려준다 (은행의 중복 송금 방지)
 */
@Component
public class FakeBank {
	private final Map<String, Transfer> transfers = new ConcurrentHashMap<>();
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

	// status는 조회했을 때 보이는 최종 결과다
	public record Transfer(String transferId, long sellerId, long amount, Status status, Mode mode) {
	}

	public void changeMode(Mode mode) {
		this.mode = mode;
	}

	public Transfer transfer(String transferId, long sellerId, long amount) {
		Mode current = mode;
		Status status = current == Mode.FAIL || current == Mode.PENDING_THEN_FAIL ? Status.FAILED : Status.COMPLETED;
		return transfers.computeIfAbsent(transferId, id -> new Transfer(id, sellerId, amount, status, current));
	}

	public Optional<Transfer> find(String transferId) {
		return Optional.ofNullable(transfers.get(transferId));
	}
}
