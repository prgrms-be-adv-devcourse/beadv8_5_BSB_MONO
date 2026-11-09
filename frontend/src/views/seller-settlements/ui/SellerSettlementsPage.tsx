'use client'

import { keepPreviousData } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import {
  detailPeriods,
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
import { ApiError, payoutApi, payoutModel, unwrapData } from '@/shared/api'
import { cn, formatNumber, formatWon } from '@/shared/lib'
import {
  Alert,
  Badge,
  Button,
  DataTable,
  EmptyState,
  FOCUS_RING,
  Pagination,
  Segmented,
  Select,
  Skeleton,
  StatTile,
  TextField,
} from '@/shared/ui'

export type SettlementTab = 'monthly' | 'orders' | 'holds'

/** 건별 정산 한 페이지 크기 (백엔드 한도 1~100) */
const ITEM_PAGE_SIZE = 20

/** 해당 없는 금액 칸 */
const DASH = '–'

function errorText(error: unknown) {
  return (error instanceof ApiError && error.serverMessage) || '잠시 뒤 다시 시도해 주세요.'
}

// 피그마 V2 · S04 정산(월별 정산) · S10 정산 내역 · 지급보류
export function SellerSettlementsPage({ initialTab = 'monthly' }: { initialTab?: SettlementTab }) {
  const [tab, setTab] = useState<SettlementTab>(initialTab)
  const isHolds = tab === 'holds'
  // 화면을 연 날 기준. 기간 목록이 렌더마다 바뀌지 않게 한 번만 잡는다
  const [today] = useState(() => new Date())
  const [periods] = useState(() => detailPeriods(today))
  const [periodValue, setPeriodValue] = useState(periods[0].value)
  const period = periods.find((p) => p.value === periodValue) ?? periods[0]

  const summary = payoutApi.useSummary({ query: { select: unwrapData } })

  return (
    <ConsolePage
      title={isHolds ? '정산 내역' : '정산'}
      description={
        isHolds
          ? '1차 정산은 환불 마감일, 최종 정산은 대회일이 속한 달의 다음 달 20일(주말이어도 그날)에 지급해요. 보류가 풀리면 해제일 다음 날 지급해요(원래 지급일보다 앞당기지 않아요).'
          : '매월 20일(주말이어도 그날)에 지급해요. 1차 정산은 환불 마감일, 최종 정산은 대회일이 속한 달의 다음 달 20일이에요.'
      }
      // TODO(api): 정산 내역 엑셀 내려받기 API가 없어 막아 둔다
      actions={
        <Button variant="secondary" disabled title="엑셀 내려받기는 준비 중이에요">
          엑셀로 내려받기
        </Button>
      }
    >
      <div className="flex gap-6">
        {summary.data ? (
          <>
            {/* 지급 예정 정산이 없으면 다음 정산일이 null이라 「-」로 보여 준다 */}
            <StatTile
              label="다음 정산일"
              value={summary.data.nextPayDate ? formatMonthDay(summary.data.nextPayDate) : '-'}
              unit={summary.data.nextPayDate && `(${formatWeekday(summary.data.nextPayDate)})`}
            />
            <StatTile
              tone="accent"
              label="정산 예정액"
              value={formatNumber(summary.data.scheduledPayoutAmount)}
              unit="원"
            />
            <StatTile label="지급보류" value={formatNumber(summary.data.heldCount)} unit="건" />
          </>
        ) : summary.isError ? (
          <Alert
            tone="danger"
            title="정산 요약을 불러오지 못했어요"
            description={errorText(summary.error)}
          />
        ) : (
          <Skeleton className="h-24 flex-1" />
        )}
      </div>

      <Segmented
        label="정산 보기"
        layout="hug"
        value={tab}
        onChange={setTab}
        items={[
          { value: 'monthly', label: '월별 정산' },
          { value: 'orders', label: '건별 정산' },
          { value: 'holds', label: '지급보류', count: summary.data?.heldCount },
        ]}
      />

      {tab === 'monthly' && <MonthlyPayouts today={today} />}

      {(tab === 'orders' || isHolds) && (
        <ConsoleCard title={isHolds ? '지급보류 내역' : '건별 정산'}>
          <div className="flex items-start gap-4">
            <Select
              label="조회 기간"
              helper="건별 정산·지급보류는 한 번에 최대 1개월까지"
              items={periods}
              value={periodValue}
              onValueChange={(next) => next && setPeriodValue(next)}
              className="flex-1"
            />
            {/* TODO(api): ID로 찾기 API가 아직 없어 막아 둔다 */}
            <TextField
              label="ID로 찾기"
              helper="ID로 찾기는 준비 중이에요"
              disabled
              className="flex-1"
            />
          </div>
          {isHolds ? <HoldList period={period} /> : <PayoutItemList period={period} />}
        </ConsoleCard>
      )}
    </ConsolePage>
  )
}

function MonthlyPayouts({ today }: { today: Date }) {
  const [params] = useState(() => recentPayoutMonths(today))
  const payouts = payoutApi.usePayouts(params, { query: { select: unwrapData } })

  return (
    <ConsoleCard
      title="대회별 월 정산"
      aside={
        // TODO(api): 정산 ID로 찾기 API가 아직 없어 막아 둔다
        <button
          type="button"
          disabled
          title="정산 ID로 찾기는 준비 중이에요"
          className={cn(
            'rounded-sm text-body2-medium text-text-brand disabled:cursor-not-allowed disabled:text-text-disabled',
            FOCUS_RING,
          )}
        >
          정산 ID로 찾기
        </button>
      }
    >
      <QueryRows query={payouts} label="대회별 월 정산" emptyTitle="최근 12개월 정산 내역이 없어요">
        {(rows) => (
          <DataTable
            label="대회별 월 정산"
            rows={rows}
            rowKey={(r) => String(r.id)}
            columns={[
              { key: 'id', header: '정산 ID', width: 'w-42.5', render: (r) => r.payoutCode },
              { key: 'race', header: '대회', render: (r) => r.raceName },
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
                render: (r) => formatWon(r.salesAmount),
              },
              {
                key: 'refund',
                header: '환불액',
                align: 'right',
                render: (r) => formatWon(r.refundAmount),
              },
              // 수수료와 1차 정산금 차감은 최종 정산에서만 뺀다. 1차 줄은 "–"
              {
                key: 'fee',
                header: '수수료',
                align: 'right',
                render: (r) => (r.payoutStage === '최종_정산' ? formatWon(r.feeAmount) : DASH),
              },
              {
                key: 'advanceDeduction',
                header: '1차 정산금 차감',
                align: 'right',
                render: (r) =>
                  r.payoutStage === '최종_정산' ? formatWon(r.advanceDeductionAmount) : DASH,
              },
              {
                key: 'payout',
                header: '지급액',
                align: 'right',
                render: (r) => formatWon(r.payoutAmount),
              },
              {
                key: 'paidAt',
                header: '지급일',
                align: 'right',
                render: (r) => formatMonthDay(r.paidAt ?? r.scheduledPayDate),
              },
              {
                key: 'status',
                header: '상태',
                render: (r) => <PayoutStatusBadge status={r.status} />,
              },
            ]}
          />
        )}
      </QueryRows>
      <p className="text-caption-regular text-text-tertiary">
        환불 마감 뒤 판매액의 40%를 1차 정산으로 먼저 지급하고, 대회가 끝나면 나머지를 최종 정산으로
        지급해요. 최종 정산에서 수수료((판매액 − 환불액)의 5.5%, 부가세 포함)와 1차 정산금을 빼요.
      </p>
    </ConsoleCard>
  )
}

function PayoutItemList({ period }: { period: DetailPeriod }) {
  // 기간이 바뀌면 첫 페이지로 돌아간다
  const [pageState, setPageState] = useState({ key: period.value, page: 0 })
  const page = pageState.key === period.value ? pageState.page : 0
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
                render: (r) => formatMonthDay(r.targetDate),
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
              { key: 'amount', header: '금액', align: 'right', render: (r) => formatWon(r.amount) },
              {
                key: 'fee',
                header: '수수료',
                align: 'right',
                render: (r) => formatWon(r.feeAmount),
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
              onPageChange={(next) => setPageState({ key: period.value, page: next - 1 })}
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
              {
                key: 'amount',
                header: '금액',
                align: 'right',
                width: 'w-35',
                render: (r) => formatWon(r.payoutAmount),
              },
              { key: 'reason', header: '사유', render: (r) => HOLD_REASON[r.reasonType] },
              {
                key: 'status',
                header: '상태',
                width: 'w-30',
                render: (r) => <Badge tone={HOLD_STATUS[r.status].tone}>{holdStatusText(r)}</Badge>,
              },
              {
                key: 'heldAt',
                header: '보류일',
                align: 'right',
                width: 'w-32.5',
                render: (r) => formatMonthDay(r.heldAt),
              },
            ]}
          />
        )}
      </QueryRows>
      <Alert
        title="보류 사유는 두 가지예요"
        description="정산 계좌 미등록 · 대회 취소 처리. 보류된 금액은 지급 완료로 바뀌지 않고, 풀리면 해제일 다음 날 지급해요(원래 지급일보다 앞당기지 않아요)."
      />
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

/** 불러오는 중 · 오류 · 빈 목록을 같은 모양으로 보여 주고, 행이 있으면 children을 그린다 */
function QueryRows<Row>({
  query,
  label,
  emptyTitle,
  children,
}: {
  query: { data?: Row[]; isPending: boolean; isError: boolean; error: unknown }
  label: string
  emptyTitle: string
  children: (rows: Row[]) => ReactNode
}) {
  if (query.isError) {
    return (
      <Alert
        tone="danger"
        title={`${label}을 불러오지 못했어요`}
        description={errorText(query.error)}
      />
    )
  }
  if (query.isPending || !query.data) {
    return <Skeleton className="h-40 w-full" />
  }
  if (query.data.length === 0) {
    return <EmptyState title={emptyTitle} description="조회 기간을 바꿔 다시 찾아보세요." />
  }
  return children(query.data)
}

function PayoutStatusBadge({ status }: { status: string }) {
  const { label, tone } = payoutStatusLabel(status)
  return <Badge tone={tone}>{label}</Badge>
}
