'use client'

import { useInfiniteQuery } from '@tanstack/react-query'
import Link from 'next/link'

import { AppHeader } from '@/widgets/app-header'
import {
  LedgerCategory,
  useMyBalance,
  walletLedgersQueryOptions,
  type WalletLedger,
} from '@/entities/wallet'
import { formatNumber, useSearchParamsState } from '@/shared/lib'
import {
  Alert,
  BottomCta,
  Button,
  buttonClassName,
  Chip,
  EmptyState,
  KeyValue,
  Skeleton,
} from '@/shared/ui'

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

// 피그마 v4 · 예치금 (+ M17-1 환급받는 중)
export function DepositPage({ refunding = false }: { refunding?: boolean }) {
  // 거르기는 URL(?filter=)에 담는다
  const [url, setUrl] = useSearchParamsState(
    { filter: 'all' },
    { filter: FILTERS.map((f) => f.value) },
  )
  const filter = url.filter as Filter
  const { balance, isPending, isError, loggedIn, refetch } = useMyBalance()
  const ledgers = useInfiniteQuery({
    ...walletLedgersQueryOptions(filter === 'all' ? undefined : filter),
    enabled: loggedIn,
  })
  const rows = uniqueLedgers(ledgers.data?.pages.flatMap((p) => p.content) ?? [])
  // 시안 2074:94477: 내역이 하나도 없으면 「내역」 제목과 거르기 칩 없이 안내만 보여 준다
  const noLedgerAtAll = ledgers.isSuccess && rows.length === 0 && filter === 'all'

  // 시안 2074:94544: 잔액을 불러오지 못하면 화면 전체를 안내로 바꾼다
  if (isError) {
    return (
      <>
        <AppHeader type="sub" title="예치금" />
        <main className="flex flex-1 flex-col justify-center">
          <EmptyState
            icon={null}
            role="alert"
            title="예치금을 불러오지 못했어요"
            action={
              <Button variant="secondary" onClick={() => void refetch()}>
                다시 불러오기
              </Button>
            }
          />
        </main>
      </>
    )
  }

  return (
    <>
      <AppHeader type="sub" title="예치금" />
      <main className="flex flex-1 flex-col pb-8">
        <section className="flex flex-col gap-3 px-4 py-6">
          <div className="flex flex-col gap-1">
            <p className="text-body1-regular text-text-secondary">보유 예치금</p>
            {isPending ? (
              <Skeleton shape="rect" className="h-9 w-32" />
            ) : (
              <p className="font-display text-number-s">
                {balance === undefined ? '-' : `${formatNumber(balance)}원`}
              </p>
            )}
          </div>
          {refunding && (
            <>
              <Alert
                title="5,000원 환급받는 중이에요"
                description="환급받는 중에는 다시 신청할 수 없어요."
              />
            </>
          )}
        </section>

        <section className="flex flex-col gap-3 px-4 py-4">
          {!noLedgerAtAll && (
            <>
              <h2 className="text-body1-bold">내역</h2>
              <div className="flex flex-wrap gap-2">
                {FILTERS.map((f) => (
                  <Chip
                    key={f.value}
                    selected={filter === f.value}
                    onClick={() => setUrl({ filter: f.value })}
                  >
                    {f.label}
                  </Chip>
                ))}
              </div>
            </>
          )}
          {ledgers.isPending ? (
            <div className="flex flex-col gap-3 py-3" aria-label="내역 불러오는 중">
              <Skeleton shape="rect" className="h-10 w-full" />
              <Skeleton shape="rect" className="h-10 w-full" />
              <Skeleton shape="rect" className="h-10 w-full" />
            </div>
          ) : ledgers.isError ? (
            <EmptyState
              icon={null}
              title="내역을 불러오지 못했어요"
              description="잠시 뒤 다시 시도해 주세요."
              action={
                <Button variant="secondary" onClick={() => void ledgers.refetch()}>
                  다시 불러오기
                </Button>
              }
            />
          ) : rows.length === 0 ? (
            <EmptyState
              icon={noLedgerAtAll ? 'Message' : 'Inbox'}
              title={noLedgerAtAll ? '아직 예치금 내역이 없어요' : '아직 내역이 없어요'}
            />
          ) : (
            <ul className="flex flex-col">
              {rows.map((row) => (
                <li key={ledgerKey(row)} className="flex items-center justify-between gap-2 py-3">
                  <div className="flex min-w-0 flex-1 flex-col gap-1">
                    <p className="text-body1-medium">{ledgerTitle(row)}</p>
                    <p className="text-caption-regular text-text-secondary">
                      {formatLedgerTime(row.createdAt)}
                    </p>
                    <ChargeRemainingLine row={row} />
                  </div>
                  <p className="text-body1-bold tabular-nums">{formatLedgerAmount(row.amount)}</p>
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
        {refunding && (
          <section className="flex flex-col gap-3 px-4 py-4">
            <h2 className="text-body1-bold">환급받는 방법</h2>
            <div className="rounded-md border border-border-default bg-bg-surface p-4">
              <KeyValue
                label="계좌로 환급받기"
                value="남은 예치금 전부"
                className="[&>span:first-child]:w-40"
              />
            </div>
          </section>
        )}
      </main>
      <BottomCta
        className="sticky bottom-0 z-10"
        secondary={
          refunding ? (
            <span
              className={buttonClassName({
                variant: 'ghost',
                size: 'l',
                className: 'bg-bg-disabled text-text-disabled inset-ring-border-disabled',
              })}
            >
              환급받기
            </span>
          ) : (
            <Link
              href="/me/deposit/refund"
              className={buttonClassName({ variant: 'ghost', size: 'l' })}
            >
              환급받기
            </Link>
          )
        }
        primary={
          <Link href="/me/deposit/charge" className={buttonClassName({ size: 'l' })}>
            충전하기
          </Link>
        }
      />
    </>
  )
}

/** 남은 금액이 있는 충전 줄에만 붙는 한 줄. 금액과 기한은 서버 값 그대로 쓴다 */
function ChargeRemainingLine({ row }: { row: WalletLedger }) {
  const left = chargeRemaining(row)
  if (!left) return null
  return (
    <p className="text-body1-regular text-text-secondary">
      {`남은 금액 ${left.remainingAmount.toLocaleString('ko-KR')}원 · `}
      {/* 기한이 지난 몫은 계좌로 가므로 「까지」라고 쓰면 아직 카드로 받을 수 있는 것처럼 읽힌다 */}
      {left.expired
        ? '카드 취소 기한 지남 · 계좌로 환급받아요'
        : `카드 취소 ${left.cardCancelDate}까지`}
    </p>
  )
}
