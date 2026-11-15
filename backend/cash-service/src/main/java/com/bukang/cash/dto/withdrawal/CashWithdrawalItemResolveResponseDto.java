package com.bukang.cash.dto.withdrawal;

import com.bukang.cash.domain.withdrawal.WithdrawalItemStatus;
import com.bukang.cash.domain.withdrawal.WithdrawalStatus;

// 정리한 몫의 새 상태와, 그 몫이 속한 환급의 상태(마지막 몫이면 COMPLETED)
public record CashWithdrawalItemResolveResponseDto(
	long itemId, WithdrawalItemStatus itemStatus, long withdrawalId, WithdrawalStatus withdrawalStatus
) {
}
