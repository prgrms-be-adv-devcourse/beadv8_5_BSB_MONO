/** 정책 4장 예치금-1: 충전 최소 금액 */
export const MIN_CHARGE = 10_000

/** 정책 4장 2차 2-1: 1회 충전 한도. 넘으면 서버가 충전 전체를 거절한다 */
export const MAX_CHARGE = 300_000

/** 하루(한국 시간 자정 기준) 충전 한도. 서버 CashChargePolicy.MAX_PER_DAY와 같다 */
export const MAX_CHARGE_PER_DAY = 1_000_000

/** 서버가 일일 한도에 세는 충전 내역 종류 (PG 결제로 들어온 충전) */
const PG_CHARGE_TYPE = '충전__PG결제_토스페이먼츠'

type ChargeLedger = { ledgerType: string; amount: number; createdAt: string }

/** 한국 시간 기준 오늘 날짜(YYYY-MM-DD). 서버의 createdAt은 한국 시간이라 앞 10글자와 비교한다 */
export function koreaToday(now: number) {
  return new Date(now + 9 * 3_600_000).toISOString().slice(0, 10)
}

/** 오늘 PG 결제로 충전한 합계 */
export function todayChargedTotal(rows: ChargeLedger[], now: number) {
  const today = koreaToday(now)
  return rows
    .filter((r) => r.ledgerType === PG_CHARGE_TYPE && r.createdAt.startsWith(today))
    .reduce((sum, r) => sum + r.amount, 0)
}

const won = (n: number) => n.toLocaleString('ko-KR')

/**
 * 이 금액으로 충전할 수 없으면 그 이유, 할 수 있으면 null.
 * 오늘 충전 합계를 아직 모르면(undefined) 일일 한도는 서버에 맡긴다.
 */
export function chargeLimitMessage(amount: number, todayTotal: number | undefined) {
  if (amount < MIN_CHARGE) return '1만 원부터 충전할 수 있어요.'
  if (amount > MAX_CHARGE) return `한 번에 ${won(MAX_CHARGE)}원까지 충전할 수 있어요.`
  if (todayTotal !== undefined && todayTotal + amount > MAX_CHARGE_PER_DAY) {
    const left = Math.max(0, MAX_CHARGE_PER_DAY - todayTotal)
    return left === 0
      ? `오늘은 더 충전할 수 없어요. 하루 ${won(MAX_CHARGE_PER_DAY)}원까지 충전할 수 있어요.`
      : `오늘은 ${won(left)}원까지 더 충전할 수 있어요.`
  }
  return null
}
