import { http, HttpResponse } from 'msw'

import type { WalletLedgerItemDto } from '@/shared/api/generated/cash/cash.schemas'

import { freshBalanceOf, freshMemberOf, issueMockBalanceCookie } from './mock-member'

/**
 * 가짜 응답 모드의 예치금 지갑. 진짜 서버처럼 충전하면 늘고 결제하면 준다.
 * 처음 잔액은 시안(M17 · M21)의 ₩5,000이라, 주문(50,000원)을 결제하려면 모자란 만큼 충전해야 한다.
 * 그래서 결제 → 부족 → 충전 → 다시 결제 흐름을 가짜 응답 모드에서도 끝까지 볼 수 있다.
 * 주문 1 상태(handlers.ts)처럼 서버 메모리에 둔다. 서버를 다시 켜면 처음으로 돌아간다.
 */
export const MOCK_INITIAL_BALANCE = 5_000

/** 정책 4장 예치금-1, cash-service와 같은 최소 충전 금액 */
const MIN_CHARGE = 10_000

let balance = MOCK_INITIAL_BALANCE
/** 충전 확정 결과(orderId = 멱등키). 같은 orderId로 다시 오면 처음 결과를 돌려준다 */
const charges = new Map<string, 'COMPLETED' | 'FAILED'>()

/** 테스트 사이에 잔액 · 충전 기록이 넘어가지 않게 처음으로 돌린다. */
export function resetMockWalletForTesting() {
  balance = MOCK_INITIAL_BALANCE
  charges.clear()
  withdrawals.clear()
}

/**
 * 잔액. 방금 가입한 회원은 쿠키(mockBalance)의 값이고, 시연용 기존 회원은 서버 메모리 변수다.
 * 새 회원 잔액을 메모리에 두면 같은 인스턴스를 쓰는 새 회원끼리 잔액을 함께 쓰게 된다.
 */
const currentBalance = (cookies: Record<string, string>) =>
  freshMemberOf(cookies) ? freshBalanceOf(cookies) : balance

/** 잔액이 바뀐 응답에 붙일 쿠키. 새 회원만 새 잔액을 쿠키로 들고 간다 */
export const balanceCookieHeaders = (cookies: Record<string, string>, next: number) =>
  freshMemberOf(cookies) ? { 'Set-Cookie': issueMockBalanceCookie(next) } : undefined

/**
 * 주문 결제 목이 쓴다. 모자라면 차감하지 않고 모자란 금액을 돌려준다.
 * next는 결제 뒤 잔액이다(새 회원은 이 값을 쿠키에 심는다).
 */
export function payFromMockWallet(
  amount: number,
  cookies: Record<string, string> = {},
): { shortage: number; next: number } {
  const current = currentBalance(cookies)
  if (current < amount) return { shortage: amount - current, next: current }
  const next = current - amount
  if (!freshMemberOf(cookies)) balance = next
  return { shortage: 0, next }
}

// shortcut: 충전 줄의 charge가 openapi/cash.json에 아직 없어 덧붙인 타입, 명세를 다시 받으면 지운다
type LedgerRow = WalletLedgerItemDto & {
  charge?: {
    chargeId: number
    chargedAt: string
    amount: number
    remainingAmount: number
    lastCancelableDate: string
    expired: boolean
  } | null
}
type MockLedger = Omit<LedgerRow, 'balanceAfter'>

/**
 * 예치금 내역 목. 시안(M17)의 예시를 장부 모양으로 옮기고, 더 보기를 볼 수 있게 지난 충전을 더 붙였다.
 * 최신순이다. 잔액(balanceAfter)은 가장 오래된 줄부터 더해 만든다. 충전·결제를 해도 내역은 늘지 않는다(목 단순화).
 */
const MOCK_LEDGERS: LedgerRow[] = withBalanceAfter([
  {
    category: 'PAYMENT',
    ledgerType: '사용__주문결제',
    amount: -45_000,
    createdAt: '2026-10-05T14:22:00',
    refId: 12,
  },
  {
    category: 'CHARGE',
    ledgerType: '충전__PG결제_토스페이먼츠',
    amount: 10_000,
    createdAt: '2026-10-05T14:21:00',
    refId: 31,
    // 오래된 충전부터 썼다고 보아 이 충전만 남은 금액이 있다 (시안 M17 환급 변경안)
    charge: {
      chargeId: 31,
      chargedAt: '2026-10-05T14:20:58',
      amount: 10_000,
      remainingAmount: 5_000,
      lastCancelableDate: '2027-10-04',
      expired: false,
    },
  },
  {
    category: 'PAYOUT',
    ledgerType: '정산수령__상품판매_대금',
    amount: 40_000,
    createdAt: '2026-10-03T10:00:00',
    refId: 4,
  },
  {
    category: 'PAYMENT',
    ledgerType: '사용__주문결제',
    amount: -45_000,
    createdAt: '2026-10-02T11:30:00',
    refId: 11,
  },
  {
    category: 'WITHDRAWAL',
    ledgerType: '출금__통장입금',
    amount: -5_000,
    createdAt: '2026-10-01T09:00:00',
    refId: 2,
  },
  {
    category: 'REFUND',
    ledgerType: '환불__주문취소',
    amount: 60_000,
    createdAt: '2026-09-30T09:10:00',
    refId: 10,
  },
  {
    category: 'PAYMENT',
    ledgerType: '사용__주문결제',
    amount: -60_000,
    createdAt: '2026-09-12T20:05:00',
    refId: 10,
  },
  ...Array.from({ length: 20 }, (_, i): MockLedger => ({
    category: 'CHARGE',
    ledgerType: '충전__PG결제_토스페이먼츠',
    amount: 10_000,
    createdAt: `2026-09-${String(10 - Math.floor(i / 2)).padStart(2, '0')}T${i % 2 ? '09' : '18'}:40:00`,
    refId: 30 - i,
  })),
])

function withBalanceAfter(rows: MockLedger[]): LedgerRow[] {
  let balanceAfter = 0
  return rows
    .toReversed()
    .map((row) => ({ ...row, balanceAfter: (balanceAfter += row.amount) }))
    .toReversed()
}

// 돌려받기 화면 조회 목 (GET /wallets/refundable-charges, 백엔드 BUK-21 응답 모양).
// 화면 주소에 ?case=card(카드만) 또는 ?case=account(계좌만)를 붙이면 그 경우로 답한다(기본은 카드 + 계좌).
// ?case=seller는 충전 건 없이 정산 대금만 남은 판매자 회원이다(전부 계좌).
// 금액은 시안 예시라 지갑 잔액과 맞추지 않는다. 충전 건은 최근 충전부터 온다.
const OLD_CHARGE = {
  chargeId: 101,
  chargedAt: '2025-09-01T18:40:00',
  amount: 85_000,
  lastCancelableDate: '2026-08-31',
  expired: true,
}
const NEW_CHARGE = {
  chargeId: 214,
  chargedAt: '2026-10-13T14:21:00',
  amount: 60_000,
  remainingAmount: 60_000,
  lastCancelableDate: '2027-10-12',
  expired: false,
}
const REFUND_PREVIEW_CASES = {
  mixed: {
    balance: 65_000,
    earliestLastCancelableDate: '2027-10-12',
    charges: [NEW_CHARGE, { ...OLD_CHARGE, remainingAmount: 5_000 }],
  },
  card: {
    balance: 65_000,
    earliestLastCancelableDate: '2027-10-09',
    charges: [
      NEW_CHARGE,
      {
        chargeId: 210,
        chargedAt: '2026-10-10T09:00:00',
        amount: 10_000,
        remainingAmount: 5_000,
        lastCancelableDate: '2027-10-09',
        expired: false,
      },
    ],
  },
  account: {
    balance: 65_000,
    earliestLastCancelableDate: null,
    charges: [{ ...OLD_CHARGE, remainingAmount: 65_000 }],
  },
  seller: { balance: 30_000, earliestLastCancelableDate: null, charges: [] },
}

function refundPreviewCase(referer: string | null) {
  try {
    const value = new URL(referer ?? '').searchParams.get('case')
    return value === 'card' || value === 'account' || value === 'seller' ? value : 'mixed'
  } catch {
    return 'mixed'
  }
}

/** 환급 결과(Idempotency-Key 헤더 = 멱등키). 같은 키로 다시 오면 처음 결과를 돌려준다 */
const withdrawals = new Map<string, { amount: number; toAccount: boolean }>()

/** 가짜 응답은 나누지 않는다. 계좌를 보냈으면 전부 계좌로, 아니면 전부 카드 결제취소로 답한다 */
const withdrawalResult = (amount: number, balance: number, toAccount: boolean) => ({
  withdrawnAmount: amount,
  balance,
  cardAmount: toAccount ? 0 : amount,
  accountAmount: toAccount ? amount : 0,
  switchedToAccountAmount: 0,
  processingAmount: 0,
  revertedAmount: 0,
  accountRequired: false,
})

export const walletHandlers = [
  http.get('*/api/v1/cash/wallets/refundable-charges', ({ request }) =>
    HttpResponse.json({
      status: 200,
      message: '환급 화면용 충전 내역을 조회했습니다.',
      data: REFUND_PREVIEW_CASES[refundPreviewCase(request.headers.get('referer'))],
    }),
  ),
  /**
   * 예치금 전액 환급 목. 진짜 서버처럼 신청하면 바로 완료되고, 잔액이 0원이면 400(code 없음)이다.
   */
  http.post('*/api/v1/cash/withdrawals', async ({ request, cookies }) => {
    const key = request.headers.get('Idempotency-Key') ?? ''
    const done = withdrawals.get(key)
    if (done !== undefined) {
      return HttpResponse.json({
        status: 200,
        message: '환급 신청이 완료되었습니다.',
        data: withdrawalResult(done.amount, currentBalance(cookies), done.toAccount),
      })
    }
    if (currentBalance(cookies) <= 0) {
      return HttpResponse.json(
        { status: 400, message: '환급할 잔액이 없습니다.', data: null },
        { status: 400 },
      )
    }
    const amount = currentBalance(cookies)
    if (!freshMemberOf(cookies)) balance = 0
    const body = (await request.json().catch(() => ({}))) as { accountNumber?: string }
    const toAccount = Boolean(body.accountNumber?.trim())
    withdrawals.set(key, { amount, toAccount })
    return HttpResponse.json(
      {
        status: 200,
        message: '환급 신청이 완료되었습니다.',
        data: withdrawalResult(amount, 0, toAccount),
      },
      { headers: balanceCookieHeaders(cookies, 0) },
    )
  }),
  /** 예치금 내역 목. 진짜 서버처럼 category로 거르고 page · size(최대 100)로 자른다 */
  http.get('*/api/v1/cash/wallets/ledgers', ({ request }) => {
    const params = new URL(request.url).searchParams
    const category = params.get('category')
    const page = Number(params.get('page') ?? 0)
    const size = Number(params.get('size') ?? 20)
    if (size > 100) {
      return HttpResponse.json(
        {
          status: 400,
          code: 'INVALID_REQUEST',
          message: '한 번에 100개까지 조회할 수 있습니다.',
          data: null,
        },
        { status: 400 },
      )
    }
    const all = category ? MOCK_LEDGERS.filter((l) => l.category === category) : MOCK_LEDGERS
    return HttpResponse.json({
      status: 200,
      message: '예치금 내역을 조회했습니다.',
      data: {
        content: all.slice(page * size, (page + 1) * size),
        page,
        size,
        totalElements: all.length,
        totalPages: Math.ceil(all.length / size),
      },
    })
  }),
  http.get('*/api/v1/cash/wallets/balance', ({ request, cookies }) =>
    HttpResponse.json({
      status: 200,
      message: '잔액 조회 성공',
      data: {
        holderId: Number(request.headers.get('X-Member-Id') ?? 0),
        balance: currentBalance(cookies),
      },
    }),
  ),
  /**
   * 충전 확정 목. paymentKey에 "fail"이 들어 있으면 PG 승인 거절(200 · FAILED)로 답한다.
   * 진짜 서버처럼 1만 원 미만은 400(code 없음), 같은 orderId는 처음 결과를 돌려준다.
   */
  http.post('*/api/v1/cash/charges/confirm', async ({ request, cookies }) => {
    const body = (await request.json()) as {
      paymentKey?: string
      orderId?: string
      amount?: number
    }
    const orderId = body.orderId ?? ''
    const done = charges.get(orderId)
    if (done) {
      return HttpResponse.json({
        status: 200,
        message: done === 'COMPLETED' ? '충전했습니다.' : '충전에 실패했습니다.',
        data: { outcome: done, balance: currentBalance(cookies) },
      })
    }
    const amount = body.amount ?? 0
    if (amount < MIN_CHARGE) {
      return HttpResponse.json(
        { status: 400, message: '최소 충전 금액은 10,000원입니다.', data: null },
        { status: 400 },
      )
    }
    const outcome = body.paymentKey?.includes('fail') ? 'FAILED' : 'COMPLETED'
    charges.set(orderId, outcome)
    const next = currentBalance(cookies) + (outcome === 'COMPLETED' ? amount : 0)
    if (outcome === 'COMPLETED' && !freshMemberOf(cookies)) balance = next
    return HttpResponse.json(
      {
        status: 200,
        message: outcome === 'COMPLETED' ? '충전했습니다.' : '충전에 실패했습니다.',
        data: { outcome, balance: next },
      },
      { headers: balanceCookieHeaders(cookies, next) },
    )
  }),
]
