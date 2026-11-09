'use client'

import { useInfiniteQuery } from '@tanstack/react-query'
import Link from 'next/link'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import {
  LedgerCategory,
  useMyBalance,
  walletLedgersQueryOptions,
  type WalletLedger,
} from '@/entities/wallet'
import { formatWon } from '@/shared/lib'
import { Alert, Button, buttonClassName, Chip, EmptyState, Skeleton } from '@/shared/ui'

import { chargeRemaining } from '../api/refund-contract'
import {
  formatLedgerAmount,
  formatLedgerTime,
  ledgerKey,
  ledgerTitle,
  uniqueLedgers,
} from '../lib/ledger'

type Filter = 'all' | LedgerCategory

// 정산 입금(PAYOUT)은 판매자에게만 생기고 탭은 따로 두지 않는다. 전체 탭에만 나온다.
const FILTERS: { value: Filter; label: string }[] = [
  { value: 'all', label: '전체' },
  { value: LedgerCategory.CHARGE, label: '충전' },
  { value: LedgerCategory.PAYMENT, label: '결제' },
  { value: LedgerCategory.REFUND, label: '환불' },
  { value: LedgerCategory.WITHDRAWAL, label: '환급' },
]

// 피그마 V2 · M17 예치금 (+ M17-1 돌려받는 중)
export function DepositPage({ refunding = false }: { refunding?: boolean }) {
  const [filter, setFilter] = useState<Filter>('all')
  const { balance, isPending, isError, loggedIn } = useMyBalance()
  const ledgers = useInfiniteQuery({
    ...walletLedgersQueryOptions(filter === 'all' ? undefined : filter),
    enabled: loggedIn,
  })
  const rows = uniqueLedgers(ledgers.data?.pages.flatMap((p) => p.content) ?? [])

  return (
    <>
      <AppHeader type="sub" title="예치금" />
      <main className="flex flex-1 flex-col gap-6 px-4 py-6">
        <section className="flex flex-col gap-3 rounded-lg bg-bg-surface p-5 shadow-sm inset-ring inset-ring-border-default">
          <div className="flex flex-col gap-1">
            <p className="text-body2-medium text-text-secondary">보유 예치금</p>
            {isPending ? (
              <Skeleton shape="rect" className="h-9 w-32" />
            ) : (
              <p className="font-display text-number-s">
                {isError || balance === undefined ? '-' : formatWon(balance)}
              </p>
            )}
            {isError && (
              <p className="text-caption-regular text-text-tertiary">잔액을 불러오지 못했어요.</p>
            )}
          </div>
          {refunding && (
            <Alert
              title="₩5,000 돌려받는 중이에요"
              description="국민은행 계좌로 보내면 '환급 완료'로 바뀌어요."
            />
          )}
          <div className="flex gap-2">
            <Link href="/me/deposit/charge" className={buttonClassName({ className: 'flex-1' })}>
              충전하기
            </Link>
            {refunding ? (
              <span
                className={buttonClassName({
                  variant: 'secondary',
                  className: 'flex-1 bg-bg-disabled text-text-disabled inset-ring-border-disabled',
                })}
              >
                돌려받기
              </span>
            ) : (
              <Link
                href="/me/deposit/refund"
                className={buttonClassName({ variant: 'secondary', className: 'flex-1' })}
              >
                돌려받기
              </Link>
            )}
          </div>
          <p className="text-caption-regular text-text-tertiary">
            {refunding
              ? '돌려받는 중에는 다시 신청할 수 없어요. 충전은 할 수 있어요.'
              : '남은 예치금은 언제든 전부 돌려받을 수 있어요.'}
          </p>
        </section>

        <section className="flex flex-col gap-3">
          <h2 className="text-body1-bold">내역</h2>
          <div className="flex flex-wrap gap-2">
            {FILTERS.map((f) => (
              <Chip key={f.value} selected={filter === f.value} onClick={() => setFilter(f.value)}>
                {f.label}
              </Chip>
            ))}
          </div>
          {ledgers.isPending ? (
            <div className="flex flex-col gap-3 py-3" aria-label="내역 불러오는 중">
              <Skeleton shape="rect" className="h-10 w-full" />
              <Skeleton shape="rect" className="h-10 w-full" />
              <Skeleton shape="rect" className="h-10 w-full" />
            </div>
          ) : ledgers.isError ? (
            <EmptyState
              title="내역을 불러오지 못했어요"
              description="잠시 뒤 다시 시도해 주세요."
              action={
                <Button variant="secondary" onClick={() => void ledgers.refetch()}>
                  다시 불러오기
                </Button>
              }
            />
          ) : rows.length === 0 ? (
            <EmptyState title="아직 내역이 없어요" />
          ) : (
            <ul className="flex flex-col">
              {rows.map((row) => (
                <li
                  key={ledgerKey(row)}
                  className="flex items-center justify-between gap-2 border-b border-border-default py-3"
                >
                  <div className="flex min-w-0 flex-1 flex-col gap-1">
                    <p className="text-body2-medium">{ledgerTitle(row)}</p>
                    <p className="text-caption-regular text-text-tertiary">
                      {formatLedgerTime(row.createdAt)}
                    </p>
                    <ChargeRemainingLine row={row} />
                  </div>
                  <p className="text-body2-bold tabular-nums">{formatLedgerAmount(row.amount)}</p>
                </li>
              ))}
            </ul>
          )}
          {ledgers.hasNextPage && (
            <Button
              variant="secondary"
              onClick={() => void ledgers.fetchNextPage()}
              disabled={ledgers.isFetchingNextPage}
            >
              {ledgers.isFetchingNextPage ? '불러오는 중' : '더 보기'}
            </Button>
          )}
        </section>

        <section className="flex flex-col gap-3 rounded-lg bg-bg-subtle p-5">
          <h2 className="text-body1-bold">돌려받는 방법</h2>
          <p className="text-body2-regular text-text-secondary">
            결제한 카드로 먼저 돌려드리고, 카드 취소 기한이 지난 몫만 계좌로 보내 드려요.
          </p>
        </section>
      </main>
    </>
  )
}

/** 남은 금액이 있는 충전 줄에만 붙는 한 줄. 금액과 기한은 서버 값 그대로 쓴다 */
function ChargeRemainingLine({ row }: { row: WalletLedger }) {
  const left = chargeRemaining(row)
  if (!left) return null
  return (
    <p className="text-caption-regular text-text-tertiary">
      {`남은 금액 ${left.remainingAmount.toLocaleString('ko-KR')}원 · `}
      {/* 기한이 지난 몫은 계좌로 가므로 「까지」라고 쓰면 아직 카드로 받을 수 있는 것처럼 읽힌다 */}
      {left.expired
        ? `카드 취소 기한(${left.cardCancelDate})이 지났어요`
        : `카드 취소 ${left.cardCancelDate}까지`}
    </p>
  )
}
