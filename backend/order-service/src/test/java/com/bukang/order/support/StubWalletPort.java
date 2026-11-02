package com.bukang.order.support;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.bukang.order.domain.exception.ExternalServiceRejectedException;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.domain.exception.OrderNotPayableException;
import com.bukang.order.out.wallet.WalletPayResult;
import com.bukang.order.out.wallet.WalletPort;

/**
 * 테스트용 예치금. 잔액을 정할 수 있고(기본은 넉넉함), 같은 주문은 한 번만 차감한다 (D의 멱등 처리 흉내)
 * 결제를 되돌리면(cancelPayment) 차감한 금액을 잔액에 돌려준다. 같은 주문을 여러 번 되돌려도 한 번만 돌려준다
 * 되돌린 주문으로 다시 결제하면 거절한다 (D가 결제 취소로 막아 둔 주문은 409, WalletPort.cancelPayment)
 * 환불(refund)은 그 주문에서 차감한 금액 전액을 주문마다 한 번만 돌려준다
 * 응답 시간 초과를 흉내 낼 수 있다: 결제는 차감까지 끝낸 뒤 예외를 던지고(D는 처리했지만 응답을 못 받음),
 * 결제 취소는 아무것도 하지 않고 예외를 던진다. 환불은 둘 다 흉내 낸다: 처리 전 장애(setRefundFails)와 처리 후 시간 초과(setRefundTimesOut)
 * 환불을 부른 순간 DB 트랜잭션 안이었는지 남긴다 (환불 HTTP 동안 잠금·커넥션을 쥐지 않는지 확인)
 * 결제를 차감 직전에 멈춰 둘 수 있다 (holdPay): 결제가 주문을 잠근 채 D를 기다리는 순간에 다른 작업을 끼워 넣을 때 쓴다
 */
public class StubWalletPort implements WalletPort {
	private static final long DEFAULT_BALANCE = 1_000_000;
	private static final String TIMEOUT_MESSAGE = "예치금 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.";

	private long balance = DEFAULT_BALANCE;
	// 차감한 채로 남아 있는 주문 ID → 차감 금액
	private final Map<Long, Long> paidOrders = new HashMap<>();
	private final List<Long> payCalls = new CopyOnWriteArrayList<>();
	private final List<Long> canceledOrders = new CopyOnWriteArrayList<>();
	private final List<Call> cancelCalls = new CopyOnWriteArrayList<>();
	private final List<Call> refundCalls = new CopyOnWriteArrayList<>();
	private final List<Long> refundedAmounts = new CopyOnWriteArrayList<>();
	private boolean payTimesOut;
	private boolean cancelFails;
	private boolean refundFails;
	private boolean refundRejects;
	private boolean refundTimesOut;
	private final List<Boolean> refundInTransaction = new CopyOnWriteArrayList<>();
	private volatile CountDownLatch payEntered;
	private volatile CountDownLatch payReleased;

	// 예치금 호출 한 번: 어느 주문을 누구 지갑으로
	public record Call(long orderId, long memberId) {
	}

	// 멈춰 둔 결제는 모니터를 잡기 전에 기다린다. 그동안 결제 취소 같은 다른 호출이 막히지 않는다
	@Override
	public WalletPayResult pay(long orderId, long memberId, long amount) {
		CountDownLatch entered = payEntered;
		CountDownLatch released = payReleased;
		if (entered != null && released != null) {
			entered.countDown();
			await(released);
		}
		return payNow(orderId, amount);
	}

	private synchronized WalletPayResult payNow(long orderId, long amount) {
		payCalls.add(amount);
		if (canceledOrders.contains(orderId)) {
			throw new OrderNotPayableException("이미 끝난 결제라 다시 결제할 수 없습니다.");
		}
		WalletPayResult result = charge(orderId, amount);
		if (payTimesOut) {
			throw new ExternalServiceUnavailableException(TIMEOUT_MESSAGE);
		}
		return result;
	}

	private WalletPayResult charge(long orderId, long amount) {
		if (paidOrders.containsKey(orderId)) {
			return WalletPayResult.paid();
		}
		if (balance < amount) {
			return WalletPayResult.insufficient(amount - balance);
		}
		balance -= amount;
		paidOrders.put(orderId, amount);
		return WalletPayResult.paid();
	}

	@Override
	public synchronized void cancelPayment(long orderId, long memberId) {
		cancelCalls.add(new Call(orderId, memberId));
		if (cancelFails) {
			throw new ExternalServiceUnavailableException(TIMEOUT_MESSAGE);
		}
		canceledOrders.add(orderId);
		Long amount = paidOrders.remove(orderId);
		if (amount != null) {
			balance += amount;
		}
	}

	@Override
	public synchronized void refund(long orderId, long memberId) {
		refundCalls.add(new Call(orderId, memberId));
		refundInTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
		if (refundFails) {
			throw new ExternalServiceUnavailableException(TIMEOUT_MESSAGE);
		}
		if (refundRejects) {
			// HttpWalletAdapter가 D의 4xx 거절을 던지는 예외와 같게 둔다
			throw new ExternalServiceRejectedException("환불할 수 없는 결제입니다.");
		}
		Long amount = paidOrders.remove(orderId);
		if (amount != null) {
			refundedAmounts.add(amount);
			balance += amount;
		}
		if (refundTimesOut) {
			throw new ExternalServiceUnavailableException(TIMEOUT_MESSAGE);
		}
	}

	// 다음 결제를 차감 직전에 멈춘다. 돌려준 래치로 결제가 들어왔는지 기다리고, releasePay()로 이어 가게 한다
	public CountDownLatch holdPay() {
		payEntered = new CountDownLatch(1);
		payReleased = new CountDownLatch(1);
		return payEntered;
	}

	public void releasePay() {
		CountDownLatch released = payReleased;
		if (released != null) {
			released.countDown();
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(30, TimeUnit.SECONDS)) {
				throw new IllegalStateException("멈춰 둔 결제가 30초 안에 풀리지 않았습니다.");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}

	// 결제 응답 시간 초과를 흉내 낸다. 차감은 되고 주문 쪽은 예외를 받는다
	public synchronized void setPayTimesOut(boolean payTimesOut) {
		this.payTimesOut = payTimesOut;
	}

	// 결제 취소가 실패하게 한다 (예치금 서비스 장애)
	public synchronized void setCancelFails(boolean cancelFails) {
		this.cancelFails = cancelFails;
	}

	// 환불이 실패하게 한다 (예치금 서비스 장애)
	public synchronized void setRefundFails(boolean refundFails) {
		this.refundFails = refundFails;
	}

	// 환불 응답 시간 초과를 흉내 낸다. 환불은 되고(D는 처리함) 주문 쪽은 예외를 받는다
	public synchronized void setRefundTimesOut(boolean refundTimesOut) {
		this.refundTimesOut = refundTimesOut;
	}

	// 환불을 부를 때마다 DB 트랜잭션 안이었는지
	public List<Boolean> refundInTransaction() {
		return refundInTransaction;
	}

	// 환불을 D가 거절하게 한다 (다시 해도 안 되는 실패, 409)
	public synchronized void setRefundRejects(boolean refundRejects) {
		this.refundRejects = refundRejects;
	}

	public List<Call> cancelCalls() {
		return cancelCalls;
	}

	public List<Call> refundCalls() {
		return refundCalls;
	}

	public List<Long> refundedAmounts() {
		return refundedAmounts;
	}

	public synchronized void setBalance(long balance) {
		this.balance = balance;
	}

	public synchronized long balance() {
		return balance;
	}

	public List<Long> payCalls() {
		return payCalls;
	}

	// 차감한 채로 남아 있는 주문 (결제를 되돌리거나 환불한 주문은 빠진다)
	public synchronized Set<Long> chargedOrders() {
		return Set.copyOf(paidOrders.keySet());
	}

	// 결제 취소가 성공한 주문
	public List<Long> canceledOrders() {
		return canceledOrders;
	}

	public synchronized void clear() {
		balance = DEFAULT_BALANCE;
		paidOrders.clear();
		payCalls.clear();
		canceledOrders.clear();
		cancelCalls.clear();
		refundCalls.clear();
		refundedAmounts.clear();
		payTimesOut = false;
		cancelFails = false;
		refundFails = false;
		refundRejects = false;
		refundTimesOut = false;
		refundInTransaction.clear();
		releasePay();
		payEntered = null;
		payReleased = null;
	}
}
