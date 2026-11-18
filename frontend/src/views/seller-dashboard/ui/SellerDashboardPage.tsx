'use client'

import { useQuery } from '@tanstack/react-query'
import type { Route } from 'next'
import Link from 'next/link'
import type { ReactNode } from 'react'

import { ConsoleCard, ConsoleLoading, ConsoleOffline, ConsolePage } from '@/widgets/console-page'
import { useManageInquiries } from '@/entities/inquiry'
import {
  myRacesQueryOptions,
  SELLER_SALE_STATUS_TEXT,
  toSellerRaceRow,
  type SellerRaceRow,
} from '@/entities/race'
import { shippableCount, useSellerKitSummary } from '@/entities/seller-kit'
import { ApiError, payoutApi, unwrapData } from '@/shared/api'
import { cn, formatNumber, useNow } from '@/shared/lib'
import {
  Alert,
  buttonClassName,
  EmptyState,
  FOCUS_RING,
  Icon,
  ProgressBar,
  Skeleton,
} from '@/shared/ui'

import { formatBaseDate, formatKoreanMonthDay, soldRate } from '../lib/dashboard'

function errorText(error: unknown) {
  return (error instanceof ApiError && error.serverMessage) || '잠시 뒤 다시 시도해 주세요.'
}

// 구역: 흰 바탕 + 1px 선 상자(둥글기 12, 안쪽 24)
const CARD = 'rounded-md border border-border-default shadow-none'

// 피그마 Final v3 · 판매자 · 대시보드: 처리할 일 → 다음 정산 → 대회별 판매
export function SellerDashboardPage({ state }: { state?: 'loading' | 'offline' }) {
  const now = useNow()
  return (
    <ConsolePage
      title="대시보드"
      description={now ? formatBaseDate(now) : undefined}
      actions={
        <Link href="/seller/races/new?from=home" className={buttonClassName()}>
          대회 등록하기
        </Link>
      }
    >
      {state === 'loading' && <ConsoleLoading />}
      {state === 'offline' && <ConsoleOffline />}
      {!state && <DashboardBody />}
    </ConsolePage>
  )
}

// 아직 데이터가 없고, 에러가 났거나 재시도 중이면 실패로 본다
function isFailing(query: { data: unknown; isError: boolean; failureCount: number }) {
  return query.data === undefined && (query.isError || query.failureCount > 0)
}

function DashboardBody() {
  const races = useQuery({
    ...myRacesQueryOptions(),
    select: (list) => list.map(toSellerRaceRow),
  })

  return (
    <>
      <TodoCard />
      <PayoutCard />
      <RaceSalesCard rows={races.data} error={races.isError ? races.error : null} />
    </>
  )
}

// 처리할 일: 송장 입력 대기 · 답변 대기 문의 · 지급보류. 0건인 줄은 숨긴다
function TodoCard() {
  const kits = useSellerKitSummary()
  const summary = payoutApi.useSummary({ query: { select: unwrapData } })
  const inquiries = useManageInquiries('seller', { status: 'WAITING' })
  const shippable = kits.data && shippableCount(kits.data)
  const waiting = inquiries.data?.waitingCount
  const held = summary.data?.heldCount
  const total =
    shippable === undefined || waiting === undefined || held === undefined
      ? undefined
      : shippable + waiting + held

  return (
    <ConsoleCard title="처리할 일" className={cn(CARD, 'gap-2')}>
      {total === 0 ? (
        <p className="py-2 text-body2-regular text-text-secondary">처리할 일이 없어요</p>
      ) : (
        <>
          {(shippable === undefined || shippable > 0) && (
            <TodoRow
              href="/seller/kits"
              label={countLabel('송장 입력 대기', shippable, isFailing(kits))}
            />
          )}
          {(waiting === undefined || waiting > 0) && (
            <TodoRow
              href="/seller/inquiries"
              label={countLabel('답변 대기 문의', waiting, inquiries.isError)}
            />
          )}
          {(held === undefined || held > 0) && (
            <TodoRow
              href="/seller/settlements"
              label={countLabel('지급보류', held, isFailing(summary))}
              danger
            />
          )}
        </>
      )}
    </ConsoleCard>
  )
}

function countLabel(name: string, count: number | undefined, failed: boolean): ReactNode {
  if (count !== undefined) return `${name} ${formatNumber(count)}건`
  if (failed) return `${name} -`
  return <Skeleton className="h-5 w-32" />
}

// 줄 전체가 그 메뉴로 가는 링크다
function TodoRow({ href, label, danger }: { href: Route; label: ReactNode; danger?: boolean }) {
  return (
    <Link
      href={href}
      className={cn(
        'flex h-12 items-center gap-3 rounded-sm text-body2-medium',
        danger && 'text-text-danger',
        FOCUS_RING,
      )}
    >
      <span className="min-w-0 flex-1">{label}</span>
      <Icon name="ChevronRight" className="size-4" />
    </Link>
  )
}

function PayoutCard() {
  const summary = payoutApi.useSummary({ query: { select: unwrapData } })

  return (
    <ConsoleCard
      title="다음 정산"
      aside={
        <Link
          href="/seller/settlements"
          className={buttonClassName({ variant: 'secondary', size: 's' })}
        >
          정산
        </Link>
      }
      className={CARD}
    >
      {summary.data ? (
        <>
          <p className="flex items-baseline gap-2">
            <span className="font-display text-number-s">
              {formatNumber(summary.data.scheduledPayoutAmount)}
            </span>
            <span className="text-body2-regular text-text-secondary">원</span>
          </p>
          <p className="flex gap-4 text-body2-regular text-text-secondary">
            {/* 지급 예정 정산이 없으면 다음 정산일이 null이다 */}
            <span>
              {summary.data.nextPayDate
                ? `${formatKoreanMonthDay(summary.data.nextPayDate)} 지급 예정`
                : '지급 예정 정산 없음'}
            </span>
            {summary.data.heldCount > 0 && (
              <span className="text-text-danger">지급보류 {summary.data.heldCount}건</span>
            )}
          </p>
        </>
      ) : summary.isError ? (
        <Alert
          tone="danger"
          title="정산 요약을 불러오지 못했어요"
          description={errorText(summary.error)}
        />
      ) : (
        <Skeleton className="h-16 w-full" />
      )}
    </ConsoleCard>
  )
}

function RaceSalesCard({ rows, error }: { rows: SellerRaceRow[] | undefined; error: unknown }) {
  return (
    <ConsoleCard
      title="대회별 판매"
      aside={
        <Link href="/seller/races" className={buttonClassName({ variant: 'secondary', size: 's' })}>
          대회 관리
        </Link>
      }
      className={CARD}
    >
      {error ? (
        <Alert tone="danger" title="대회 목록을 불러오지 못했어요" description={errorText(error)} />
      ) : !rows ? (
        <Skeleton className="h-52 w-full" />
      ) : rows.length === 0 ? (
        <EmptyState
          title="등록한 대회가 없어요"
          action={
            <Link
              href="/seller/races/new?from=home"
              className={buttonClassName({ variant: 'secondary' })}
            >
              대회 등록하기
            </Link>
          }
        />
      ) : (
        <table aria-label="대회별 판매" className="w-full table-fixed text-body2-regular">
          <thead>
            <tr className="border-b border-border-default text-body2-medium text-text-secondary">
              <th className="h-11 text-left font-medium">대회</th>
              <th className="h-11 w-28 text-right font-medium">대회일</th>
              <th className="h-11 w-36 text-right font-medium">판매 / 정원</th>
              <th className="h-11 w-48 pl-6 text-left font-medium">판매율</th>
              <th className="h-11 w-28 pl-4 text-left font-medium">상태</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id} className="h-14 border-b border-border-default last:border-b-0">
                <td className="truncate">
                  <Link
                    href={`/seller/races/${row.id}`}
                    className={cn('rounded-sm text-body2-medium', FOCUS_RING)}
                  >
                    {row.title}
                  </Link>
                </td>
                <td className="text-right tabular-nums">{row.date}</td>
                <td className="text-right tabular-nums">
                  {formatNumber(row.sold)} / {formatNumber(row.capacity)}
                </td>
                <td className="pl-6">
                  <ProgressBar
                    size="s"
                    aria-label={`${row.title} 판매율`}
                    value={soldRate(row.sold, row.capacity)}
                  />
                </td>
                <td
                  className={cn(
                    'pl-4 text-body2-medium',
                    row.saleStatus
                      ? SELLER_SALE_STATUS_TEXT[row.saleStatus]
                      : 'text-text-secondary',
                  )}
                >
                  {row.badge.label}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </ConsoleCard>
  )
}
