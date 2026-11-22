'use client'

import { useQuery } from '@tanstack/react-query'
import type { Route } from 'next'
import Link from 'next/link'
import type { ReactNode } from 'react'

import { ConsoleCard, ConsoleLoading, ConsoleOffline, ConsolePage } from '@/widgets/console-page'
import { recordCertReviewCountQueryOptions } from '@/features/seller-record-review'
import { useManageInquiries } from '@/entities/inquiry'
import {
  myRacesQueryOptions,
  SaleRateBar,
  SELLER_SALE_STATUS_TEXT,
  toSellerRaceRow,
  type SellerRaceRow,
} from '@/entities/race'
import { shippableCount, useSellerKitSummary } from '@/entities/seller-kit'
import { ApiError, payoutApi, unwrapData } from '@/shared/api'
import { cn, formatNumber } from '@/shared/lib'
import {
  Alert,
  Button,
  buttonClassName,
  EmptyState,
  FOCUS_RING,
  Icon,
  Skeleton,
  type IconName,
} from '@/shared/ui'

import { formatKoreanMonthDayWeekday, soldRate } from '../lib/dashboard'

function errorText(error: unknown) {
  return (error instanceof ApiError && error.serverMessage) || '잠시 뒤 다시 시도해 주세요.'
}

// 구역: 흰 바탕 + 1px 선 상자(둥글기 12, 안쪽 24)
const CARD = 'rounded-md border border-border-default shadow-sm'

// 피그마 Final v3 · 판매자 · 대시보드: 처리할 일 → 다음 정산 → 대회별 판매
export function SellerDashboardPage({ state }: { state?: 'loading' | 'offline' }) {
  return (
    <ConsolePage
      title="대시보드"
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

  // 첫 대회를 등록하기 전에는 처리할 일 · 정산 · 판매 표 없이 등록 안내만 보여 준다
  if (races.data?.length === 0) {
    return (
      <ConsoleCard>
        <EmptyState
          title="등록한 대회가 없어요"
          action={
            <Link href="/seller/races/new?from=home" className={buttonClassName()}>
              대회 등록하기
            </Link>
          }
        />
      </ConsoleCard>
    )
  }

  return (
    <>
      <div className="grid grid-cols-[1fr_400px] items-start gap-6">
        <TodoCard />
        <PayoutCard />
      </div>
      <RaceSalesCard rows={races.data} error={races.isError ? races.error : null} />
    </>
  )
}

// 처리할 일에 대회마다 따로 보여 줄 기록증 심사 줄 수. 넘는 대회는 한 줄로 묶어 대회 관리로 보낸다
const CERT_RACE_ROWS = 3

// 처리할 일: 송장 입력 대기 · 답변 대기 문의 · 기록증 심사 · 지급보류. 0건인 줄은 숨긴다
function TodoCard() {
  const kits = useSellerKitSummary()
  const summary = payoutApi.useSummary({ query: { select: unwrapData } })
  const inquiries = useManageInquiries('seller', { status: 'WAITING' })
  const shippable = kits.data && shippableCount(kits.data)
  const waiting = inquiries.data?.waitingCount
  const certs = useQuery(recordCertReviewCountQueryOptions())
  const reviewing = certs.data?.waitingCount
  // 심사 화면이 대회마다 있어 대회별로 한 줄씩 그 화면으로 보낸다. 대회 이름은 내 대회 목록(대시보드가 이미 받는다)에서 붙인다
  const certRaces = certs.data?.races ?? []
  const restRaces = certRaces.slice(CERT_RACE_ROWS)
  const myRaces = useQuery(myRacesQueryOptions())
  const titleOf = (raceId: number) => myRaces.data?.find((race) => race.raceId === raceId)?.title
  const held = summary.data?.heldCount
  const total =
    shippable === undefined ||
    waiting === undefined ||
    reviewing === undefined ||
    held === undefined
      ? undefined
      : shippable + waiting + reviewing + held

  return (
    <ConsoleCard title="처리할 일" className={CARD}>
      {total === 0 ? (
        <p className="py-2 text-body2-regular text-text-secondary">처리할 일이 없어요</p>
      ) : (
        <div className="flex flex-col gap-2">
          {(shippable === undefined || shippable > 0) && (
            <TodoRow
              href="/seller/kits"
              icon="Truck"
              name="송장 입력 대기"
              count={countLabel(shippable, isFailing(kits))}
              menu="키트 배송"
            />
          )}
          {(waiting === undefined || waiting > 0) && (
            <TodoRow
              href="/seller/inquiries"
              icon="Help"
              name="답변 대기 문의"
              count={countLabel(waiting, inquiries.isError)}
              menu="문의"
            />
          )}
          {certRaces.slice(0, CERT_RACE_ROWS).map((race) => {
            const title = titleOf(race.raceId)
            return (
              <TodoRow
                key={race.raceId}
                href={`/seller/races/${race.raceId}/record-certs`}
                icon="FileText"
                name={title ? `기록증 심사 · ${title}` : '기록증 심사'}
                count={countLabel(race.waitingCount, false)}
                menu="기록증 심사"
              />
            )
          })}
          {restRaces.length > 0 && (
            <TodoRow
              href="/seller/races"
              icon="FileText"
              name={`기록증 심사 · 외 ${formatNumber(restRaces.length)}개 대회`}
              count={countLabel(
                restRaces.reduce((sum, race) => sum + race.waitingCount, 0),
                false,
              )}
              menu="대회 관리"
            />
          )}
          {/* 대회별 건수가 없으면(불러오는 중 · 실패 · 대회별 건수를 안 주는 서버) 합계 한 줄로 대회 관리로 보낸다 */}
          {certRaces.length === 0 && (reviewing === undefined || reviewing > 0) && (
            <TodoRow
              href="/seller/races"
              icon="FileText"
              name="기록증 심사"
              count={countLabel(reviewing, isFailing(certs))}
              menu="대회 관리"
            />
          )}
          {(held === undefined || held > 0) && (
            <TodoRow
              href="/seller/settlements"
              icon="CircleAlert"
              name="지급보류"
              count={countLabel(held, isFailing(summary))}
              menu="정산"
              danger
            />
          )}
        </div>
      )}
    </ConsoleCard>
  )
}

function countLabel(count: number | undefined, failed: boolean): ReactNode {
  if (count !== undefined) return `${formatNumber(count)}건`
  if (failed) return '-'
  return <Skeleton className="h-5 w-12" />
}

// 줄 전체가 그 메뉴로 가는 링크다: 아이콘 + 이름 + 굵은 건수, 오른쪽에 이동할 메뉴
function TodoRow({
  href,
  icon,
  name,
  count,
  menu,
  danger,
}: {
  href: Route
  icon: IconName
  name: string
  count: ReactNode
  menu: string
  danger?: boolean
}) {
  return (
    <Link
      href={href}
      className={cn(
        'flex items-center gap-3 rounded-sm bg-bg-surface py-3 pr-3 pl-4 text-body1-medium shadow-sm',
        danger && 'text-text-danger',
        FOCUS_RING,
      )}
    >
      <Icon name={icon} className="size-5" />
      <span className="flex min-w-0 flex-1 items-center gap-3">
        <span className="truncate">{name}</span>{' '}
        <span className="shrink-0 text-body1-bold">{count}</span>
      </span>
      <span className="text-caption-regular text-text-secondary">{menu}</span>
      <Icon name="ChevronRight" className="size-4 text-text-secondary" />
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
          className={buttonClassName({ variant: 'ghost', size: 's' })}
        >
          정산 보기
        </Link>
      }
      className={CARD}
    >
      {summary.data ? (
        <>
          <p className="flex items-baseline gap-1">
            <span className="font-display text-number-s">
              {formatNumber(summary.data.scheduledPayoutAmount)}
            </span>
            <span className="text-body1-regular text-text-secondary">원</span>
          </p>
          {/* 지급 예정 정산이 없으면 다음 정산일이 null이다 */}
          <p className="text-caption-regular text-text-secondary">
            {summary.data.nextPayDate
              ? `${formatKoreanMonthDayWeekday(summary.data.nextPayDate)} 지급 예정`
              : '지급 예정 정산 없음'}
          </p>
        </>
      ) : summary.isError ? (
        <>
          <p className="text-caption-regular text-text-secondary">다음 정산을 불러오지 못했어요</p>
          <Button
            variant="secondary"
            size="s"
            className="self-start"
            onClick={() => void summary.refetch()}
          >
            다시 시도하기
          </Button>
        </>
      ) : (
        <Skeleton className="h-16 w-full" />
      )}
    </ConsoleCard>
  )
}

function RaceSalesCard({ rows, error }: { rows: SellerRaceRow[] | undefined; error: unknown }) {
  return (
    <ConsoleCard title="대회별 판매" className={CARD}>
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
        <table aria-label="대회별 판매" className="w-full table-fixed text-body1-regular">
          <thead>
            <tr className="border-b border-border-default text-caption-medium text-text-secondary">
              <th className="h-11 px-3 text-left font-medium">대회</th>
              <th className="h-11 w-35 px-3 text-left font-medium">대회일</th>
              <th className="h-11 w-40 px-3 text-right font-medium">판매 / 정원</th>
              <th className="h-11 w-50 px-3 text-left font-medium">판매율</th>
              <th className="h-11 w-30 px-3 text-left font-medium">상태</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id} className="h-14 border-b border-border-default">
                <td className="truncate px-3">
                  <Link
                    href={`/seller/races/${row.id}`}
                    className={cn('rounded-sm text-body1-medium', FOCUS_RING)}
                  >
                    {row.title}
                  </Link>
                </td>
                <td className="px-3 tabular-nums">{row.date}</td>
                <td className="px-3 text-right tabular-nums">
                  {formatNumber(row.sold)} / {formatNumber(row.capacity)}
                </td>
                <td className="px-3">
                  <SaleRateBar
                    aria-label={`${row.title} 판매율`}
                    percent={soldRate(row.sold, row.capacity)}
                    label={`${soldRate(row.sold, row.capacity)}%`}
                  />
                </td>
                <td
                  className={cn(
                    'px-3 text-body1-medium',
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
