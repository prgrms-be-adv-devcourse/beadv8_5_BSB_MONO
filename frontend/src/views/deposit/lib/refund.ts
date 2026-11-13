import { z } from 'zod'

export { BANKS, bankName, type BankCode } from '@/shared/config'

/**
 * 계좌번호 가운데를 가린다. 앞 6자리와 끝 3자리 숫자만 남기고 입력한 하이픈은 그대로 둔다.
 * 123456-01-234567 → 123456-**-***567 (시안 M22-1)
 */
export function maskAccount(account: string) {
  const digits = account.replace(/\D/g, '').length
  let seen = 0
  return account.replace(/\d/g, (d) => {
    seen += 1
    return seen <= 6 || seen > digits - 3 ? d : '*'
  })
}

const KST_PARTS = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  month: 'numeric',
  day: 'numeric',
  weekday: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

/** 한국 시간으로 10/6 (화) 14:20 (시안 M22-1). 브라우저 시간대와 상관없이 같다 */
export function formatKstShort(ms: number) {
  const part = (type: Intl.DateTimeFormatPartTypes) =>
    KST_PARTS.formatToParts(ms).find((p) => p.type === type)?.value ?? ''
  return `${part('month')}/${part('day')} (${part('weekday')}) ${part('hour')}:${part('minute')}`
}

/**
 * 환급 완료 화면(M22-1)에 보여 줄 값. 계좌번호를 주소에 넣지 않으려고 sessionStorage로 넘긴다.
 * 계좌번호는 가린 값만 둔다.
 */
const LastWithdrawal = z.object({
  amount: z.number(),
  balance: z.number(),
  /** 서버가 나눈 몫: 카드 결제취소 요청 · 계좌로 보냄 · 결과 확인 중 · 카드 결제취소가 거절돼 예치금에 되돌림 */
  cardAmount: z.number(),
  accountAmount: z.number(),
  processingAmount: z.number(),
  revertedAmount: z.number(),
  /** 계좌로 보낼 몫이 없어 계좌 없이 신청했으면 세 값 모두 없다 */
  bankName: z.string().optional(),
  maskedAccount: z.string().optional(),
  accountHolder: z.string().optional(),
  /** 신청한 시각(ms). 서버 응답에 시각이 없어 브라우저 시각을 쓴다 */
  at: z.number(),
})

export type LastWithdrawal = z.infer<typeof LastWithdrawal>

const KEY = 'crewrun:last-withdrawal'

export function saveLastWithdrawal(withdrawal: LastWithdrawal) {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(withdrawal))
  } catch {
    // 저장이 막혀 있으면 완료 화면이 예치금 화면으로 보낸다
  }
}

/** 저장된 원문. useSyncExternalStore 스냅샷으로 쓰려고 매번 같은 문자열을 돌려준다 */
export function readLastWithdrawalRaw(): string | null {
  try {
    return sessionStorage.getItem(KEY)
  } catch {
    return null
  }
}

export function parseLastWithdrawal(raw: string | null): LastWithdrawal | null {
  if (raw === null) return null
  try {
    const parsed = LastWithdrawal.safeParse(JSON.parse(raw))
    return parsed.success ? parsed.data : null
  } catch {
    return null
  }
}
