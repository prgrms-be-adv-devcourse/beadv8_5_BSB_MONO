'use client'

import { keepPreviousData, useQueries } from '@tanstack/react-query'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState } from 'react'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import {
  CREATE_FAILURE_ACTION,
  CREATE_FAILURE_TYPE_ITEMS,
  detailPeriods,
  formatMonthDay,
  PAYOUT_STAGE,
} from '@/entities/payout'
import {
  ApiError,
  memberApi,
  payoutApi,
  productApi,
  unwrapData,
  type payoutModel,
} from '@/shared/api'
import { formatNumber } from '@/shared/lib'
import {
  Alert,
  Badge,
  Button,
  buttonClassName,
  DataTable,
  EmptyState,
  KeyValue,
  Pagination,
  Segmented,
  Select,
  Skeleton,
  StatTile,
} from '@/shared/ui'

import { captionText, courseText, diffText, noteText, summaryText } from '../lib/anomaly-text'

/** 한 페이지 크기 (백엔드 한도 1~100) */
const PAGE_SIZE = 20

type TypeFilter = payoutModel.PayoutCreateFailureType | 'all'
type FailureRecord = payoutModel.PayoutCreateFailureDto

// 관리자 「정산 · 환급 처리」 탭 막대 (시안 C8). 회수 화면은 아직 없어 눌러도 옮기지 않는다
type AdminPayoutTab = 'settlements' | 'holds' | 'claims' | 'anomalies'
const TAB_ITEMS: { value: AdminPayoutTab; label: string }[] = [
  { value: 'settlements', label: '정산 · 환급 내역' },
  { value: 'holds', label: '지급보류' },
  { value: 'claims', label: '회수' },
  { value: 'anomalies', label: '이상 감지' },
]
const TAB_HREF = {
  settlements: '/admin/settlements',
  holds: '/admin/holds',
} as const

function errorText(error: unknown) {
  return (error instanceof ApiError && error.serverMessage) || '잠시 뒤 다시 시도해 주세요.'
}

/** '2026-11-06T03:00:12' → '11/6 03:00' */
function formatRecordedAt(dateTime: string) {
  return `${formatMonthDay(dateTime)} ${dateTime.slice(11, 16)}`
}

function ActionBadge({ action }: { action: FailureRecord['actionTaken'] }) {
  // 칸을 더하기 전에 쌓인 대사 불일치 기록은 조치를 모른다
  if (!action) {
    return <Badge tone="neutral">알 수 없음</Badge>
  }
  const { label, tone } = CREATE_FAILURE_ACTION[action]
  return <Badge tone={tone}>{label}</Badge>
}

// 피그마 시안 N4 이상 감지 · N8 이상 감지 없음 (BUK-108)
// 판매자 · 대회 · 코스 이름은 정산 API에 없어(C6 결정) 회원 판매자 목록과 대회 상세를 따로 받아 합친다
export function AdminAnomaliesPage() {
  const router = useRouter()
  // 화면을 연 날 기준. 기간 목록이 렌더마다 바뀌지 않게 한 번만 잡는다
  const [periods] = useState(() => detailPeriods(new Date()))
  const [periodValue, setPeriodValue] = useState(periods[0].value)
  const period = periods.find((p) => p.value === periodValue) ?? periods[0]
  const [type, setType] = useState<TypeFilter>('all')
  // 기간이나 종류가 바뀌면 첫 페이지로 돌아간다
  const filterKey = `${period.value}|${type}`
  const [pageState, setPageState] = useState({ key: filterKey, page: 0 })
  const page = pageState.key === filterKey ? pageState.page : 0
  const [selectedId, setSelectedId] = useState<number>()

  const failures = payoutApi.useCreateFailures(
    {
      from: period.from,
      to: period.to,
      ...(type === 'all' ? {} : { type }),
      page,
      size: PAGE_SIZE,
    },
    { query: { select: unwrapData, placeholderData: keepPreviousData } },
  )
  const data = failures.data
  const rows = data?.records.content ?? []
  const selected = rows.find((r) => r.id === selectedId) ?? rows[0]

  // 판매자 ID는 판매자 신청서 ID다. 승인된 판매자만 정산을 받는다
  const sellers = memberApi.useFindAll(
    { status: 'APPROVED' },
    { query: { select: unwrapData, enabled: rows.length > 0 } },
  )
  const sellerName = (r: FailureRecord) => {
    if (r.sellerId == null) {
      return '—'
    }
    const seller = sellers.data?.find((s) => s.id === r.sellerId)
    return seller ? seller.businessName || seller.representativeName : `판매자 #${r.sellerId}`
  }

  const raceIds = [...new Set(rows.flatMap((r) => (r.raceId == null ? [] : [r.raceId])))]
  const races = useQueries({
    queries: raceIds.map((raceId) =>
      productApi.getFindRaceQueryOptions(raceId, {
        query: { select: unwrapData, staleTime: 5 * 60_000 },
      }),
    ),
  })
  const raceOf = (raceId: number | null) =>
    raceId == null ? undefined : races[raceIds.indexOf(raceId)]?.data
  const raceText = (r: FailureRecord) => {
    const race = r.raceId == null ? '대회 모름' : (raceOf(r.raceId)?.title ?? `대회 #${r.raceId}`)
    return `${race} · ${PAYOUT_STAGE[r.payoutStage]}`
  }

  const selectedCourses = selected
    ? courseText(selected, raceOf(selected.raceId)?.courses ?? [])
    : null

  return (
    <ConsolePage
      title="이상 감지"
      description="매일 정산 생성 배치 뒤에 주문 판매와 정산 내역을 비교한 결과예요."
      // TODO(api): 이상 감지 기록 엑셀 내려받기 API가 없어 막아 둔다
      actions={
        <Button variant="secondary" disabled title="엑셀 내려받기는 준비 중이에요">
          엑셀로 내려받기
        </Button>
      }
    >
      <Segmented
        label="정산 · 환급 처리"
        layout="hug"
        value="anomalies"
        onChange={(next) => {
          if (next === 'settlements' || next === 'holds') {
            router.push(TAB_HREF[next])
          }
        }}
        items={TAB_ITEMS}
      />

      <div className="flex gap-4">
        {data ? (
          <>
            <StatTile label="대사 불일치" value={formatNumber(data.mismatchCount)} unit="건" />
            <StatTile
              label="자동으로 채운 참가권"
              value={formatNumber(data.autoFilledCount)}
              unit="건"
            />
            <StatTile
              label="주문 조회 실패"
              value={formatNumber(data.orderLookupFailureCount)}
              unit="건"
            />
          </>
        ) : (
          !failures.isError && <Skeleton className="h-24 flex-1" />
        )}
      </div>

      <ConsoleCard
        title={`${period.label} 이상 감지 ${formatNumber(data?.records.totalElements ?? 0)}건`}
      >
        <div className="flex items-start gap-4">
          <Select
            label="조회 기간"
            helper="배치 실행일 기준, 한 번에 최대 1개월까지"
            items={periods}
            value={periodValue}
            onValueChange={(next) => next && setPeriodValue(next)}
            className="flex-1"
          />
          <Select
            label="종류"
            items={CREATE_FAILURE_TYPE_ITEMS}
            value={type}
            onValueChange={(next) => next && setType(next)}
            className="flex-1"
          />
        </div>

        {failures.isError ? (
          <Alert
            tone="danger"
            title="이상 감지 기록을 불러오지 못했어요"
            description={errorText(failures.error)}
          />
        ) : !data ? (
          <Skeleton className="h-40 w-full" />
        ) : rows.length === 0 ? (
          <EmptyState
            title={`${period.label} 점검에서 어긋난 곳이 없어요.`}
            description="조회 기간이나 종류를 바꿔 다시 찾아보세요."
          />
        ) : (
          <>
            <DataTable
              label="이상 감지"
              rows={rows}
              rowKey={(r) => String(r.id)}
              selected={selected && String(selected.id)}
              onRowClick={(r) => setSelectedId(r.id)}
              columns={[
                {
                  key: 'recordedAt',
                  header: '기록 시각',
                  width: 'w-30',
                  render: (r) => formatRecordedAt(r.recordedAt),
                },
                { key: 'seller', header: '판매자', width: 'w-35', render: sellerName },
                { key: 'race', header: '대회 · 단계', width: 'w-50', render: raceText },
                { key: 'diff', header: '차이', align: 'right', width: 'w-20', render: diffText },
                { key: 'summary', header: '내용', render: summaryText },
                {
                  key: 'action',
                  header: '처리',
                  width: 'w-24',
                  render: (r) => <ActionBadge action={r.actionTaken} />,
                },
              ]}
            />
            {data.records.totalPages > 1 && (
              <Pagination
                className="self-center"
                page={page + 1}
                totalPages={data.records.totalPages}
                onPageChange={(next) => setPageState({ key: filterKey, page: next - 1 })}
              />
            )}
          </>
        )}
      </ConsoleCard>

      {selected && (
        <ConsoleCard title="기록 상세">
          <div className="flex flex-col gap-3 rounded-md border border-border-default p-4">
            <p className="text-label-m">
              {formatRecordedAt(selected.recordedAt)} · {sellerName(selected)} ·{' '}
              {raceText(selected)}
            </p>
            <KeyValue label="비고" value={noteText(selected)} />
            {selectedCourses && <KeyValue label="코스별" value={selectedCourses} />}
            {selected.actionTaken === '지급보류' && (
              <Link
                href="/admin/holds"
                className={buttonClassName({ variant: 'secondary', className: 'w-full' })}
              >
                보류 보기
              </Link>
            )}
            <p className="text-caption text-text-tertiary">{captionText(selected)}</p>
          </div>
        </ConsoleCard>
      )}
    </ConsolePage>
  )
}
