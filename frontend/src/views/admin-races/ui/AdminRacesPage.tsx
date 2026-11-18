'use client'

import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'

import { CONSOLE_BOX, ConsoleCard, ConsoleLoading, ConsolePage } from '@/widgets/console-page'
import { RaceSaleActionButton } from '@/features/race-sale-status'
import { adminRacesQueryOptions, toAdminRaceRow } from '@/entities/race'
import { ApiError } from '@/shared/api'
import { cn, formatNumber } from '@/shared/lib'
import { Alert, DataTable, EmptyState, KeyValue } from '@/shared/ui'

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
  const rows = races.data ?? []
  const race = rows.find((r) => r.id === selectedId) ?? rows[0]

  return (
    <ConsolePage title="대회 관리" description="등록된 대회를 조회하고 판매 상태를 바꿔요.">
      {races.isPending ? (
        <ConsoleLoading />
      ) : races.isError && !races.data ? (
        <Alert
          tone="danger"
          title="대회 목록을 불러오지 못했어요"
          description={
            (races.error instanceof ApiError && races.error.serverMessage) ||
            '잠시 후 다시 시도해 주세요.'
          }
        />
      ) : !race ? (
        <EmptyState title="등록된 대회가 없어요" />
      ) : (
        <div className="flex flex-wrap items-start gap-6">
          <ConsoleCard title={`대회 ${rows.length}개`} className="min-w-130 flex-1">
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
          </ConsoleCard>

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
