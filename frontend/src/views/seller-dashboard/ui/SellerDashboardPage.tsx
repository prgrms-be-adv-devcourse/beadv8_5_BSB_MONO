'use client'

import { useQuery } from '@tanstack/react-query'
import type { Route } from 'next'
import Link from 'next/link'
import type { ReactNode } from 'react'

import { ConsoleCard, ConsoleLoading, ConsoleOffline, ConsolePage } from '@/widgets/console-page'
import { detailPeriods, formatMonthDay, formatWeekday, HOLD_REASON } from '@/entities/payout'
import {
  daysUntil,
  formatDDay,
  formatRaceDateTime,
  myRacesQueryOptions,
  parseKstDateTime,
  toSellerRaceRow,
} from '@/entities/race'
import { shippableCount, useSellerKitSummary } from '@/entities/seller-kit'
import { ApiError, payoutApi, unwrapData, type productModel } from '@/shared/api'
import { cn, formatNumber } from '@/shared/lib'
import {
  Alert,
  Badge,
  buttonClassName,
  EmptyState,
  FOCUS_RING,
  ProgressBar,
  Skeleton,
  StatTile,
} from '@/shared/ui'

function errorText(error: unknown) {
  return (error instanceof ApiError && error.serverMessage) || '잠시 뒤 다시 시도해 주세요.'
}

// 접수 마감이 이 날 수 안에 오면 처리할 일에 띄운다 (정책 2장 등록 정보-6: 조기 마감될 수 있어 미리 알린다)
const CLOSING_SOON_DAYS = 7

const LINK = cn('rounded-sm text-label-m text-text-brand', FOCUS_RING)

// 피그마 V2 · S05 판매자 홈 (+ 판매자 · 관리자 공통 로딩 · 연결 끊김)
export function SellerDashboardPage({ state }: { state?: 'loading' | 'offline' }) {
  return (
    <ConsolePage
      title="판매자 홈"
      description="오늘 처리할 일과 다음 정산을 한눈에 봐요."
      actions={
        <Link href="/seller/races/new" className={buttonClassName()}>
          대회 등록
        </Link>
      }
    >
      {state === 'loading' && <ConsoleLoading />}
      {state === 'offline' && <ConsoleOffline />}
      {!state && <DashboardBody />}
    </ConsolePage>
  )
}

const TODO_ROW = 'flex items-center gap-3 border-b border-border-default py-3 last:border-b-0'

// 처리할 일 한 줄. href가 있으면 그 화면으로 가는 링크
function TodoRow({
  href,
  title,
  description,
  count,
}: {
  href?: Route
  title: string
  description: string
  count: ReactNode
}) {
  const body = (
    <>
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <p className="text-label-l">{title}</p>
        <p className="text-body-s text-text-secondary">{description}</p>
      </div>
      {count}
    </>
  )
  return href ? (
    <Link href={href} className={cn(TODO_ROW, 'rounded-sm', FOCUS_RING)}>
      {body}
    </Link>
  ) : (
    <div className={TODO_ROW}>{body}</div>
  )
}

// 아직 데이터가 없고, 에러가 났거나 재시도 중이면 실패로 본다
function isFailing(query: { data: unknown; isError: boolean; failureCount: number }) {
  return query.data === undefined && (query.isError || query.failureCount > 0)
}

function DashboardBody() {
  const summary = payoutApi.useSummary({ query: { select: unwrapData } })
  const races = useQuery(myRacesQueryOptions())
  const rows = races.data?.map(toSellerRaceRow)
  const onSale = rows?.filter((r) => r.saleStatus === 'ON_SALE')
  const kits = useSellerKitSummary()
  const closing = races.data && closingSoon(races.data)
  // 처리할 일 숫자 = 배송 대기 키트 + 접수 마감 임박 대회 + 지급보류. 하나라도 못 받으면 셀 수 없다.
  const todoCount =
    kits.data && closing && summary.data
      ? shippableCount(kits.data) + closing.length + summary.data.heldCount
      : undefined
  // 재시도 중(failureCount > 0)이어도 첫 실패부터 「-」를 보여 준다. 재시도가 성공하면 숫자로 바뀐다
  const todoFailed = isFailing(kits) || isFailing(races) || isFailing(summary)

  return (
    <>
      <div className="flex gap-4">
        {summary.data ? (
          <>
            <StatTile
              label="다음 정산일"
              value={formatMonthDay(summary.data.nextPayDate)}
              unit={`(${formatWeekday(summary.data.nextPayDate)})`}
            />
            <StatTile
              tone="accent"
              className="flex-2"
              label="정산 예정액"
              value={`₩${formatNumber(summary.data.scheduledPayoutAmount)}`}
            />
          </>
        ) : summary.isError ? (
          <Alert
            tone="danger"
            className="flex-3"
            title="정산 요약을 불러오지 못했어요"
            description={errorText(summary.error)}
          />
        ) : (
          <Skeleton className="h-24 flex-3" />
        )}
        {onSale ? (
          <StatTile label="판매 중 대회" value={String(onSale.length)} unit="개" />
        ) : (
          <Skeleton className="h-24 flex-1" />
        )}
        {todoCount !== undefined ? (
          <StatTile label="처리할 일" value={String(todoCount)} unit="건" />
        ) : todoFailed ? (
          <StatTile label="처리할 일" value="-" />
        ) : (
          <Skeleton className="h-24 flex-1" />
        )}
      </div>

      <div className="flex flex-wrap items-start gap-6">
        <ConsoleCard
          title="진행 중 대회"
          aside={
            <Link href="/seller/races" className={LINK}>
              대회 관리
            </Link>
          }
          className="min-w-130 flex-1"
        >
          {races.isError ? (
            <Alert
              tone="danger"
              title="대회 목록을 불러오지 못했어요"
              description={errorText(races.error)}
            />
          ) : !onSale ? (
            <Skeleton className="h-40 w-full" />
          ) : onSale.length === 0 ? (
            <EmptyState
              title="판매 중인 대회가 없어요"
              description="대회를 등록하면 여기에서 접수 현황을 볼 수 있어요"
            />
          ) : (
            onSale.map((race) => (
              <div key={race.id} className="flex flex-col gap-3 rounded-md bg-bg-subtle p-5">
                <div className="flex items-center gap-2">
                  <p className="text-label-l">{race.title}</p>
                  <Badge tone={race.badge.tone}>{race.badge.label}</Badge>
                </div>
                <p className="text-body-s text-text-secondary">{race.period}</p>
                <ProgressBar
                  label="접수율"
                  value={race.capacity > 0 ? Math.round((race.sold / race.capacity) * 100) : 0}
                  valueText={`${formatNumber(race.sold)}/${formatNumber(race.capacity)}명`}
                />
              </div>
            ))
          )}
        </ConsoleCard>

        <ConsoleCard title="처리할 일" className="w-100 shrink-0 gap-0">
          <TodoRow
            href="/seller/kits"
            title="배송 대기 키트"
            description="환불 기한이 지난 주문 · 송장 번호를 넣으면 배송 중으로 바뀌어요"
            count={
              kits.data ? (
                <CountBadge count={shippableCount(kits.data)} />
              ) : isFailing(kits) ? (
                <Badge tone="neutral">-</Badge>
              ) : (
                <Skeleton className="h-6 w-10" />
              )
            }
          />
          <ClosingSoonRow races={closing} failed={races.isError} />
          <HoldRow heldCount={summary.data?.heldCount} failed={summary.isError} />
        </ConsoleCard>
      </div>

      {/* TODO(api): 판매자 주문 목록 API가 없다. 생기면 최근 주문 표(시안 S05)로 바꾼다 */}
      <ConsoleCard title="최근 주문">
        <EmptyState
          title="최근 주문 목록은 준비 중이에요"
          description="대회별 접수 인원은 위 진행 중 대회에서 볼 수 있어요."
        />
      </ConsoleCard>
    </>
  )
}

/** 판매 중이고 접수 마감이 아직 안 지났으며 CLOSING_SOON_DAYS일 안에 오는 대회, 마감이 가까운 순 */
function closingSoon(
  races: productModel.SellerRaceDto[],
  now: Date = new Date(),
): productModel.SellerRaceDto[] {
  return races
    .filter((race) => {
      if (race.saleStatus !== 'ON_SALE' || !race.applicationCloseAt) return false
      // daysUntil은 날짜만 세므로, 오늘 이미 마감 시각이 지난 대회(판매 상태는 대회 다음 날에야 바뀐다)를 시각으로 뺀다
      if (parseKstDateTime(race.applicationCloseAt) <= now) return false
      return daysUntil(race.applicationCloseAt, now) <= CLOSING_SOON_DAYS
    })
    .sort((a, b) => (a.applicationCloseAt ?? '').localeCompare(b.applicationCloseAt ?? ''))
}

function CountBadge({ count }: { count: number }) {
  return <Badge tone={count > 0 ? 'warning' : 'neutral'}>{count}건</Badge>
}

function ClosingSoonRow({
  races,
  failed,
}: {
  races: productModel.SellerRaceDto[] | undefined
  failed: boolean
}) {
  const first = races?.[0]
  const others = (races?.length ?? 0) - 1
  const description = first?.applicationCloseAt
    ? `${first.title ?? ''}${others > 0 ? ` 외 ${others}개` : ''} · ${formatRaceDateTime(first.applicationCloseAt)} 접수 마감`
    : `${CLOSING_SOON_DAYS}일 안에 접수가 끝나는 대회가 없어요`
  return (
    <TodoRow
      href="/seller/races"
      title="접수 마감 임박"
      description={failed ? '접수 마감 일정을 불러오지 못했어요' : description}
      count={
        first?.applicationCloseAt ? (
          <Badge tone="warning">{formatDDay(first.applicationCloseAt)}</Badge>
        ) : races ? (
          <CountBadge count={0} />
        ) : failed ? (
          <Badge tone="neutral">-</Badge>
        ) : (
          <Skeleton className="h-6 w-10" />
        )
      }
    />
  )
}

// 건수는 정산 요약의 heldCount, 설명은 최근 1개월 지급보류 중 아직 보류 중인 가장 최근 것
function HoldRow({ heldCount, failed }: { heldCount: number | undefined; failed: boolean }) {
  const [period] = detailPeriods(new Date(), 1)
  const holds = payoutApi.useHolds(
    { from: period.from, to: period.to },
    { query: { select: unwrapData, enabled: (heldCount ?? 0) > 0 } },
  )
  const latest = holds.data?.find((hold) => hold.status === 'HELD')
  const description =
    heldCount === undefined
      ? failed
        ? '지급보류 건수를 불러오지 못했어요'
        : ''
      : heldCount === 0
        ? '보류된 정산이 없어요'
        : latest
          ? `${latest.holdCode} · ${HOLD_REASON[latest.reasonType]}`
          : '보류 사유는 정산 내역에서 확인해요'
  return (
    <TodoRow
      href="/seller/settlements"
      title="지급보류 알림"
      description={description}
      count={
        heldCount !== undefined ? (
          <CountBadge count={heldCount} />
        ) : failed ? (
          <Badge tone="neutral">-</Badge>
        ) : (
          <Skeleton className="h-6 w-10" />
        )
      }
    />
  )
}
