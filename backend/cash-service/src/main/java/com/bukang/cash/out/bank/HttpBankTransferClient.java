package com.bukang.cash.out.bank;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class HttpBankTransferClient implements BankTransferClient {
	static final String TRANSFER_PATH = "/fakebank/v1/transfers";

	private final RestClient restClient;

	private record TransferRequest(String transferId, long sellerId, long amount) {
	}

	private record TransferResponse(String status) {
	}

	@Override
	public BankTransferResult transfer(String transferId, long sellerId, long amount) {
		try {
			TransferResponse response = restClient.post()
				.uri(TRANSFER_PATH)
				.body(new TransferRequest(transferId, sellerId, amount))
				.retrieve()
				.body(TransferResponse.class);
			return toResult(response);
		} catch (HttpClientErrorException e) {
			// 은행이 요청을 받아 주지 않았으니 돈이 나가지 않았다
			log.warn("은행이 송금을 거절했습니다. transferId={}, status={}", transferId, e.getStatusCode());
			return BankTransferResult.FAILED;
		} catch (RestClientException e) {
			// 5xx·시간 초과·응답 해석 실패는 은행이 송금했는지 알 수 없다
			log.warn("송금 결과를 알 수 없습니다. transferId={}", transferId, e);
			return BankTransferResult.UNKNOWN;
		}
	}

	@Override
	public BankTransferResult lookup(String transferId) {
		try {
			TransferResponse response = restClient.get()
				.uri(TRANSFER_PATH + "/{transferId}", transferId)
				.retrieve()
				.body(TransferResponse.class);
			return toResult(response);
		} catch (HttpClientErrorException e) {
			if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
				return BankTransferResult.NOT_FOUND;
			}
			// 404 말고는 은행에 송금이 있는지 알 수 없어 결과 모름으로 둔다
			log.warn("송금 조회가 거절됐습니다. transferId={}, status={}", transferId, e.getStatusCode());
			return BankTransferResult.UNKNOWN;
		} catch (RestClientException e) {
			log.warn("송금 조회에 실패했습니다. transferId={}", transferId, e);
			return BankTransferResult.UNKNOWN;
		}
	}

	private BankTransferResult toResult(TransferResponse response) {
		if (response == null || response.status() == null) {
			return BankTransferResult.UNKNOWN;
		}
		return switch (response.status()) {
			case "COMPLETED" -> BankTransferResult.COMPLETED;
			case "FAILED" -> BankTransferResult.FAILED;
			default -> BankTransferResult.UNKNOWN;
		};
	}
}
