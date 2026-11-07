import type { WalletLedger } from '@/entities/wallet'
import { formatWon } from '@/shared/lib'

/** 내역 한 줄에 보여 줄 제목. 백엔드 장부 유형(ledgerType)마다 시안의 "분류 · 내용" 모양으로 쓴다 */
export function ledgerTitle({ ledgerType, refId }: WalletLedger) {
  switch (ledgerType) {
    case '충전__PG결제_토스페이먼츠':
      return '충전 · 토스페이먼츠'
    case '사용__주문결제':
      return `결제 · 주문 #${refId}`
    case '환불__주문취소':
      return `환불 · 주문 #${refId} 취소`
    case '출금__통장입금':
      return '환급 · 계좌로 돌려받기'
    case '출금__PG결제취소_토스페이먼츠':
      return '환급 · 결제한 카드로 결제취소'
    case '출금취소__통장입금':
    case '출금취소__PG결제취소_토스페이먼츠':
      return '환급 취소 · 예치금으로 되돌림'
    case '정산수령__상품판매_대금':
      return '정산 · 판매 대금 입금'
    default:
      return '예치금 내역'
  }
}

/** 10000 → '+ ₩10,000', -45000 → '− ₩45,000' (시안의 빼기 기호는 U+2212) */
export function formatLedgerAmount(amount: number) {
  return `${amount < 0 ? '−' : '+'} ${formatWon(Math.abs(amount))}`
}

/**
 * '2026-10-05T14:22:03.31' → '10/5 14:22'. 서버가 시간대 없이(한국 시간) 보내므로
 * Date로 바꾸지 않고 글자에서 바로 꺼낸다 (브라우저 시간대에 따라 바뀌지 않게).
 */
export function formatLedgerTime(createdAt: string) {
  const m = /^\d{4}-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(createdAt)
  if (!m) return createdAt
  const [, month, day, hour, minute] = m
  return `${Number(month)}/${Number(day)} ${hour}:${minute}`
}

/**
 * 내역 한 줄을 가리키는 키. 응답에 장부 id가 없어 유형 · 참조 id · 시각 · 금액 · 거래 뒤 잔액을 묶는다.
 * 거래 뒤 잔액까지 같은 두 줄은 한 지갑 안에서 사실상 생기지 않는다.
 */
export function ledgerKey(row: WalletLedger) {
  return [row.ledgerType, row.refId, row.createdAt, row.amount, row.balanceAfter].join('|')
}

/**
 * 페이지를 이어 붙이며 같은 줄을 한 번만 남긴다. 서버가 최신순 offset으로 자르므로
 * 0페이지를 받은 뒤 새 내역이 생기면 전체가 한 칸씩 밀려 다음 페이지 첫 줄이 앞 페이지 끝 줄과 겹친다.
 */
export function uniqueLedgers(rows: WalletLedger[]) {
  const seen = new Set<string>()
  return rows.filter((row) => {
    const key = ledgerKey(row)
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}
