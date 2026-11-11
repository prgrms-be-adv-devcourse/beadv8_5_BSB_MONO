package com.bukang.cash.fakebank;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bukang.cash.fakebank.FakeBank.Mode;
import com.bukang.cash.fakebank.FakeBank.Status;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;

/**
 * 가짜 은행의 송금 API. 결제가 정산금을 보낼 때 HTTP로 부른다
 * 게이트웨이·Nginx가 /fakebank/**를 넘기지 않아 밖에서는 부를 수 없다. 은행 응답 형식을 흉내 내려고 RsData로 감싸지 않는다
 */
@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/fakebank/v1")
public class FakeBankController {
	private final FakeBank fakeBank;

	public record TransferRequest(String transferId, long sellerId, long amount) {
	}

	public record TransferResponse(String transferId, Status status) {
	}

	public record ModeRequest(Mode mode) {
	}

	@PostMapping("/transfers")
	public ResponseEntity<TransferResponse> transfer(@RequestBody TransferRequest request) {
		FakeBankTransfer transfer = fakeBank.transfer(request.transferId(), request.sellerId(), request.amount());
		return switch (transfer.getResultMode()) {
			case DROP -> ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).build();
			case PENDING_THEN_SUCCESS, PENDING_THEN_FAIL ->
				ResponseEntity.ok(new TransferResponse(transfer.getTransferId(), Status.PROCESSING));
			default -> ResponseEntity.ok(new TransferResponse(transfer.getTransferId(), transfer.getStatus()));
		};
	}

	@GetMapping("/transfers/{transferId}")
	public ResponseEntity<TransferResponse> find(@PathVariable String transferId) {
		return ResponseEntity.of(fakeBank.find(transferId)
			.map(transfer -> new TransferResponse(transfer.getTransferId(), transfer.getStatus())));
	}

	@PutMapping("/mode")
	public void changeMode(@RequestBody ModeRequest request) {
		fakeBank.changeMode(request.mode());
	}
}
