import { chargeAmountFor } from '@/features/deposit-charge'

/**
 * 참가비를 예치금으로 내고, 모자라면 충전한다. 충전 금액은 실제 충전 요청과 같은 규칙(chargeAmountFor)으로 정한다.
 * 화면에 보여 주는 계산이다. 실제로 충전할 금액은 결제 API가 돌려준 부족 금액(shortage)으로 정한다.
 * 1회 충전 한도보다 많이 모자라면 balanceAfter가 0보다 작다 (한 번 충전으로는 결제할 수 없다).
 */
export function splitPayment(price: number, balance: number) {
  const shortfall = Math.max(0, price - balance)
  const charge = shortfall === 0 ? 0 : chargeAmountFor(shortfall)
  return {
    fromDeposit: Math.min(price, balance),
    charge,
    balanceAfter: balance + charge - price,
  }
}
