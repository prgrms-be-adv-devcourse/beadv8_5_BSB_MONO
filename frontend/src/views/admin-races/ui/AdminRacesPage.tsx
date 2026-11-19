'use client'

import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'

import {
  CONSOLE_BOX,
  ConsoleCard,
  ConsoleLoading,
  ConsoleMessage,
  ConsolePage,
} from '@/widgets/console-page'
import { RaceSaleActionButton } from '@/features/race-sale-status'
import { adminRacesQueryOptions, toAdminRaceRow } from '@/entities/race'
import { cn, formatNumber } from '@/shared/lib'
import { Chip, DataTable, EmptyState, KeyValue } from '@/shared/ui'

const n = formatNumber

// 상태는 칩 대신 뜻 색 글자로 보여 준다 (완료 초록, 실패 빨강, 나머지 보조 글자)
const STATUS_TEXT: Record<string, string> = {
  success: 'text-text-success',
  danger: 'text-text-danger',
  warning: 'text-text-warning',
}
const statusText = (tone: string) =>
  cn('text-body2-medium', STATUS_TEXT[tone] ?? 'text-text-secondary')

// 피그마 V2 · A05 대회 관리 (대회 정보는 판매자가 고치고, 관리자는 판매 상태만 바꾼다)
export function AdminRacesPage() {
  const races = useQuery({
    ...adminRacesQueryOptions(),
    select: (list) => list.map(toAdminRaceRow),
  })
  const [selectedId, setSelectedId] = useState<number>()
  const [filter, setFilter] = useState<'all' | 'ON_SALE' | 'CLOSED'>('all')
  const allRows = races.data ?? []
  const rows = filter === 'all' ? allRows : allRows.filter((r) => r.saleStatus === filter)
  const race = rows.find((r) => r.id === selectedId) ?? rows[0]

  return (
    <ConsolePage title="대회 관리">
      <div role="tablist" aria-label="대회 보기" className="flex items-center gap-2">
        {(
          [
            ['all', '전체', allRows.length],
            ['ON_SALE', '판매 중', allRows.filter((r) => r.saleStatus === 'ON_SALE').length],
            ['CLOSED', '종료', allRows.filter((r) => r.saleStatus === 'CLOSED').length],
          ] as const
        ).map(([value, label, count]) => (
          <Chip
            key={value}
            role="tab"
            aria-pressed={undefined}
            aria-selected={filter === value}
            selected={filter === value}
            onClick={() => setFilter(value)}
          >
            {label}
            <span className="tabular-nums">{count}</span>
          </Chip>
        ))}
      </div>
      {races.isPending ? (
        <ConsoleLoading />
      ) : races.isError && !races.data ? (
        <ConsoleMessage
          kind="error"
          onRetry={() => void races.refetch()}
          retrying={races.isFetching}
        >
          대회 목록을 불러오지 못했어요
        </ConsoleMessage>
      ) : !race ? (
        <EmptyState title="등록된 대회가 없어요" />
      ) : (
        <div className="flex flex-wrap items-start gap-6">
          <div className="min-w-130 flex-1">
            <DataTable
              label="등록된 대회"
              rows={rows}
              rowKey={(r) => String(r.id)}
              selected={String(race.id)}
              onRowClick={(r) => setSelectedId(r.id)}
              columns={[
                { key: 'title', header: '대회', render: (r) => r.title },
                { key: 'seller', header: '판매자', width: 'w-35', render: (r) => r.seller },
                {
                  key: 'status',
                  header: '상태',
                  width: 'w-24',
                  render: (r) => <span className={statusText(r.badge.tone)}>{r.badge.label}</span>,
                },
                {
                  key: 'sold',
                  header: '판매 / 정원',
                  align: 'right',
                  width: 'w-30',
                  render: (r) => `${n(r.sold)} / ${n(r.capacity)}`,
                },
                {
                  key: 'date',
                  header: '대회일',
                  align: 'right',
                  width: 'w-24',
                  render: (r) => r.date,
                },
              ]}
            />
          </div>

          <ConsoleCard title="대회 상세" className="w-95 shrink-0">
            <p className="text-body2-regular text-text-secondary">{race.title}</p>
            <div className={cn('flex flex-col gap-3 rounded-md p-4', CONSOLE_BOX)}>
              <KeyValue label="상태" value={race.badge.label} />
              <KeyValue label="판매자" value={race.seller} />
              <KeyValue label="판매 / 정원" value={`${n(race.sold)} / ${n(race.capacity)}명`} />
              <KeyValue label="대회일" value={race.raceDateTime} />
              <KeyValue label="접수 기간" value={race.applicationPeriod} />
              <KeyValue label="환불 기준일" value={race.refundDeadline} />
            </div>
            <RaceSaleActionButton
              key={race.id}
              raceId={race.id}
              saleStatus={race.saleStatus}
              by="admin"
              className="w-full"
            />
          </ConsoleCard>
        </div>
      )}
    </ConsolePage>
  )
}
