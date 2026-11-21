'use client'

import { keepPreviousData } from '@tanstack/react-query'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState } from 'react'

import { ConsoleLoading, ConsolePage } from '@/widgets/console-page'
import {
  detailPeriods,
  formatDayWithWeekday,
  formatMonthDay,
  formatWeekday,
  HOLD_REASON,
  HOLD_STATUS,
  PAYOUT_STAGE,
  payoutStatusLabel,
  recentPayoutMonths,
  RECORD_TYPE,
  type DetailPeriod,
} from '@/entities/payout'
import { payoutApi, payoutModel, unwrapData } from '@/shared/api'
import { cn, formatNumber, useNow, useSearchParamsState } from '@/shared/lib'
import {
  Alert,
  Button,
  DataTable,
  Pagination,
  Select,
  Skeleton,
  StatStrip,
  StatTile,
  Tab,
  TabBar,
  Tabs,
} from '@/shared/ui'

import { ClaimList } from './ClaimList'
import { errorText, QueryRows, StatusText } from './parts'
import { TaxInvoiceList } from './TaxInvoiceList'

export type SettlementTab = 'monthly' | 'orders' | 'holds' | 'claims' | 'taxInvoices'

/** 건별 정산 한 페이지 크기 (백엔드 한도 1~100) */
const ITEM_PAGE_SIZE = 20

// 구역: 흰 바탕 + 1px 선 상자
const CARD = 'rounded-md border border-border-default shadow-sm'

/** 해당 없는 금액 칸 */
const DASH = '–'

// 피그마 V2 · S04 정산(월별 정산) · S10 정산 내역 · 지급보류
export function SellerSettlementsPage({ initialTab = 'monthly' }: { initialTab?: SettlementTab }) {
  // 화면을 연 날 기준. 서버와 브라우저의 날짜가 어긋나지 않게 마운트 뒤에 정하고, 그 전에는 뼈대만 그린다
  const today = useNow()
  if (!today) {
    return (
      <ConsolePage title="정산">
        <ConsoleLoading />
      </ConsolePage>
    )
  }
  return <SettlementsBody initialTab={initialTab} today={today} />
}

function SettlementsBody({ initialTab, today }: { initialTab: SettlementTab; today: Date }) {
  // 기간 목록이 렌더마다 바뀌지 않게 한 번만 잡는다
  const [periods] = useState(() => detailPeriods(today))
  // 탭 · 기간은 URL(?tab=&period=)에 담는다. 건별 정산의 쪽(page)은 PayoutItemList가 같은 방식으로 쓴다
  const [url, setUrl] = useSearchParamsState(
    { tab: initialTab as string, period: periods[0].value, page: '0' },
    {
      tab: ['monthly', 'orders', 'holds', 'claims', 'taxInvoices'],
      period: periods.map((p) => p.value),
    },
  )
  const tab = url.tab as SettlementTab
  const isHolds = tab === 'holds'
  // 건별 정산 · 지급보류 · 회수는 1개월 기간을 골라 본다(백엔드 한도 1개월)
  const hasPeriod = tab === 'orders' || isHolds || tab === 'claims'
  const period = periods.find((p) => p.value === url.period) ?? periods[0]

  const summary = payoutApi.useSummary({ query: { select: unwrapData } })

  return (
    <ConsolePage
      title="정산"
      // TODO(api): 정산 내역 엑셀 내려받기 API가 없어 막아 둔다
      actions={
        <Button variant="secondary" disabled title="엑셀 내려받기는 준비 중이에요">
          엑셀로 내려받기
        </Button>
      }
    >
      {summary.data ? (
        <div role="group" aria-label="정산 요약">
          <StatStrip>
            {/* 지급 예정 정산이 없으면 다음 지급일이 null이라 「-」로 보여 준다 */}
            <StatTile
              layout="cell"
              label="다음 지급일"
              value={
                summary.data.nextPayDate
                  ? `${formatMonthDay(summary.data.nextPayDate)} (${formatWeekday(summary.data.nextPayDate)})`
                  : '-'
              }
            />
            <StatTile
              layout="cell"
              label="지급 예정액"
              value={formatNumber(summary.data.scheduledPayoutAmount)}
              unit="원"
            />
            {summary.data.heldCount > 0 && (
              <StatTile
                layout="cell"
                label="지급보류"
                value={formatNumber(summary.data.heldCount)}
                unit="건"
                danger
              />
            )}
          </StatStrip>
        </div>
      ) : summary.isError ? (
        <Alert
          tone="danger"
          title="정산 요약을 불러오지 못했어요"
          description={errorText(summary.error)}
        />
      ) : (
        <Skeleton className="h-24 w-full" />
      )}

      <section aria-label="정산 내역" className={cn('flex flex-col gap-6 p-6', CARD)}>
        <Tabs value={tab} onValueChange={(next) => setUrl({ tab: String(next) })}>
          <TabBar layout="hug" className="px-0">
            <Tab value="monthly">대회별 정산</Tab>
            <Tab value="orders">건별 정산</Tab>
            <Tab value="holds" count={summary.data?.heldCount}>
              지급보류
            </Tab>
            <Tab value="claims">회수</Tab>
            <Tab value="taxInvoices">세금계산서</Tab>
          </TabBar>
        </Tabs>

        {tab === 'monthly' && <MonthlyPayouts today={today} />}

        {hasPeriod && (
          <>
            {/* TODO(api): 정산 ID로 찾기 API가 아직 없다 */}
            <Select
              aria-label="조회 기간"
              items={periods}
              value={period.value}
              onValueChange={(next) => next && setUrl({ period: next, page: '0' })}
              className="w-80"
            />
            {isHolds && <HoldList period={period} />}
            {tab === 'orders' && <PayoutItemList period={period} />}
            {tab === 'claims' && <ClaimList period={period} />}
          </>
        )}

        {tab === 'taxInvoices' && <TaxInvoiceList />}
      </section>
    </ConsolePage>
  )
}

function MonthlyPayouts({ today }: { today: Date }) {
  const [params] = useState(() => recentPayoutMonths(today))
  const payouts = payoutApi.usePayouts(params, { query: { select: unwrapData } })
  const router = useRouter()
  const detailHref = (row: payoutModel.PayoutDto) => `/seller/settlements/${row.payoutCode}`

  return (
    <QueryRows query={payouts} label="대회별 월 정산" emptyTitle="정산 내역이 없어요">
      {(rows) => (
        <DataTable
          label="대회별 월 정산"
          rows={rows}
          rowKey={(r) => String(r.id)}
          // 줄 어디를 눌러도 정산 상세로 간다. 키보드는 대회 이름 링크로 간다
          onRowClick={(r) => router.push(detailHref(r) as never)}
          columns={[
            {
              key: 'race',
              header: '대회',
              width: 'w-50',
              render: (r) => (
                <span className="flex flex-col">
                  <Link
                    href={detailHref(r) as never}
                    className="truncate text-body1-medium"
                    onClick={(event) => event.stopPropagation()}
                  >
                    {r.raceName}
                  </Link>
                  <span className="truncate text-caption-regular text-text-tertiary">
                    {r.payoutCode}
                  </span>
                </span>
              ),
            },
            {
              key: 'stage',
              header: '구분',
              width: 'w-24',
              // 1차와 최종은 서로 다른 달에 속할 수 있어, 정산 월 열 대신 구분 칸에 월을 함께 쓴다
              render: (r) =>
                `${PAYOUT_STAGE[r.payoutStage]} · ${Number(r.payoutMonth.slice(5, 7))}월`,
            },
            {
              key: 'sales',
              header: '판매액',
              align: 'right',
              render: (r) => formatNumber(r.salesAmount),
            },
            {
              key: 'refund',
              header: '환불액',
              align: 'right',
              render: (r) => formatNumber(r.refundAmount),
            },
            // 수수료와 1차 정산금 차감은 최종 정산에서만 뺀다. 1차 줄은 "–"
            {
              key: 'fee',
              header: '수수료',
              align: 'right',
              render: (r) => (r.payoutStage === '최종_정산' ? formatNumber(r.feeAmount) : DASH),
            },
            {
              key: 'advanceDeduction',
              header: '1차 정산금 차감',
              align: 'right',
              width: 'w-36',
              render: (r) =>
                r.payoutStage === '최종_정산' ? formatNumber(r.advanceDeductionAmount) : DASH,
            },
            {
              key: 'payout',
              header: '지급액',
              align: 'right',
              render: (r) => (
                <span className="text-body1-bold">{formatNumber(r.payoutAmount)}</span>
              ),
            },
            {
              key: 'paidAt',
              header: '지급일',
              width: 'w-30',
              render: (r) => formatDayWithWeekday(r.paidAt ?? r.scheduledPayDate),
            },
            {
              key: 'status',
              header: '상태',
              width: 'w-28',
              render: (r) => <PayoutStatusBadge status={r.status} />,
            },
          ]}
        />
      )}
    </QueryRows>
  )
}

function PayoutItemList({ period }: { period: DetailPeriod }) {
  // 쪽은 URL(?page=)에 담는다. 기간을 바꿀 때 위에서 첫 쪽으로 돌린다
  const [url, setUrl] = useSearchParamsState({ page: '0' })
  const page = Math.max(0, Math.trunc(Number(url.page)) || 0)
  const items = payoutApi.useItems(
    { from: period.from, to: period.to, page, size: ITEM_PAGE_SIZE },
    { query: { select: unwrapData, placeholderData: keepPreviousData } },
  )

  return (
    <QueryRows
      query={{ ...items, data: items.data?.content }}
      label="건별 정산"
      emptyTitle="이 기간에 건별 정산이 없어요"
    >
      {(rows) => (
        <>
          <DataTable
            label="건별 정산"
            rows={rows}
            rowKey={(r) => String(r.id)}
            columns={[
              {
                key: 'targetDate',
                header: '정산 대상일',
                width: 'w-27.5',
                render: (r) => formatDayWithWeekday(r.targetDate),
              },
              {
                key: 'payoutCode',
                header: '정산 ID',
                width: 'w-42.5',
                render: (r) => r.payoutCode,
              },
              { key: 'race', header: '대회', render: (r) => r.raceName },
              {
                key: 'type',
                header: '구분',
                width: 'w-20',
                render: (r) => RECORD_TYPE[r.recordType],
              },
              {
                key: 'amount',
                header: '금액(원)',
                align: 'right',
                render: (r) => formatNumber(r.amount),
              },
              {
                key: 'fee',
                header: '수수료',
                align: 'right',
                render: (r) => formatNumber(r.feeAmount),
              },
              {
                key: 'status',
                header: '상태',
                width: 'w-30',
                render: (r) => <PayoutStatusBadge status={r.payoutStatus} />,
              },
            ]}
          />
          {(items.data?.totalPages ?? 0) > 1 && (
            <Pagination
              className="self-center"
              page={page + 1}
              totalPages={items.data?.totalPages ?? 1}
              onPageChange={(next) => setUrl({ page: String(next - 1) })}
            />
          )}
        </>
      )}
    </QueryRows>
  )
}

function HoldList({ period }: { period: DetailPeriod }) {
  const holds = payoutApi.useHolds(
    { from: period.from, to: period.to },
    { query: { select: unwrapData, placeholderData: keepPreviousData } },
  )

  return (
    <>
      <QueryRows query={holds} label="지급보류 내역" emptyTitle="이 기간에 지급보류가 없어요">
        {(rows) => (
          <DataTable
            label="지급보류 내역"
            rows={rows}
            rowKey={(r) => String(r.id)}
            columns={[
              { key: 'id', header: '지급보류 ID', width: 'w-47.5', render: (r) => r.holdCode },
              {
                key: 'settlementId',
                header: '정산 ID',
                width: 'w-47.5',
                render: (r) => r.payoutCode,
              },
              { key: 'race', header: '대회', render: (r) => r.raceName },
              {
                key: 'amount',
                header: '금액(원)',
                align: 'right',
                width: 'w-35',
                render: (r) => formatNumber(r.payoutAmount),
              },
              { key: 'reason', header: '사유', render: (r) => HOLD_REASON[r.reasonType] },
              {
                key: 'heldAt',
                header: '보류일',
                width: 'w-33',
                render: (r) => formatDayWithWeekday(r.heldAt),
              },
              {
                key: 'status',
                header: '상태',
                width: 'w-30',
                render: (r) => (
                  <StatusText label={holdStatusText(r)} tone={HOLD_STATUS[r.status].tone} />
                ),
              },
            ]}
          />
        )}
      </QueryRows>
    </>
  )
}

/** '지급보류' 또는 '해제 · 10/2' */
function holdStatusText(hold: payoutModel.SellerPayoutHoldDto) {
  const { label } = HOLD_STATUS[hold.status]
  return hold.status === '해제' && hold.releasedAt
    ? `${label} · ${formatMonthDay(hold.releasedAt)}`
    : label
}

function PayoutStatusBadge({ status }: { status: string }) {
  const { label, tone } = payoutStatusLabel(status)
  return <StatusText label={label} tone={tone} />
}
