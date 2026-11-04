'use client'

import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'
import { useEffect, useRef, useState } from 'react'

import { ConsoleCard, ConsoleLoading, ConsolePage } from '@/widgets/console-page'
import { myRacesQueryOptions, toSellerRaceRow } from '@/entities/race'
import { ApiError } from '@/shared/api'
import { cn, formatNumber } from '@/shared/lib'
import {
  Alert,
  Badge,
  buttonClassName,
  DataTable,
  EmptyState,
  FOCUS_RING,
  useToast,
} from '@/shared/ui'

import { SaleChangeCard } from './SaleChangeCard'

const n = formatNumber

// 피그마 V2 · S06 대회 관리 (+ S02-4 등록 완료 토스트)
export function SellerRacesPage({ registeredRaceId }: { registeredRaceId?: string }) {
  const races = useQuery({
    ...myRacesQueryOptions(),
    select: (list) => list.map(toSellerRaceRow),
  })
  const [selectedId, setSelectedId] = useState<number>()
  const toast = useToast()
  const shown = useRef(false)

  useEffect(() => {
    if (!registeredRaceId || shown.current) {
      return
    }
    shown.current = true
    toast({
      message: '대회를 등록했어요',
      tone: 'success',
      action: {
        label: '링크 복사',
        onClick: () =>
          void navigator.clipboard?.writeText(
            `${window.location.origin}/races/${registeredRaceId}`,
          ),
      },
    })
  }, [registeredRaceId, toast])

  const rows = races.data ?? []
  const race = rows.find((r) => r.id === selectedId) ?? rows[0]

  return (
    <ConsolePage
      title="대회 관리"
      description="판매가 시작된 대회는 구매자에게 유리한 변경만 할 수 있어요."
      actions={
        <Link href="/seller/races/new?from=races" className={buttonClassName()}>
          대회 등록
        </Link>
      }
    >
      {races.isPending ? (
        <ConsoleLoading />
      ) : races.isError ? (
        <Alert
          tone="danger"
          title="대회 목록을 불러오지 못했어요"
          description={
            (races.error instanceof ApiError && races.error.serverMessage) ||
            '잠시 후 다시 시도해 주세요.'
          }
        />
      ) : !race ? (
        <EmptyState
          title="아직 등록한 대회가 없어요"
          description="대회를 등록하면 여기에서 판매 현황을 보고 바꿀 수 있어요"
          action={
            <Link
              href="/seller/races/new?from=races"
              className={buttonClassName({ variant: 'secondary' })}
            >
              대회 등록
            </Link>
          }
        />
      ) : (
        <div className="flex flex-wrap items-start gap-6">
          <ConsoleCard
            title={`대회 ${rows.length}개`}
            aside={
              <button
                type="button"
                className={cn('rounded-sm text-label-m text-text-brand', FOCUS_RING)}
              >
                대회명으로 찾기
              </button>
            }
            className="min-w-130 flex-1"
          >
            <DataTable
              label="내 대회"
              rows={rows}
              rowKey={(r) => String(r.id)}
              selected={String(race.id)}
              onRowClick={(r) => setSelectedId(r.id)}
              columns={[
                { key: 'title', header: '대회', render: (r) => r.title },
                {
                  key: 'date',
                  header: '대회일',
                  align: 'right',
                  width: 'w-24',
                  render: (r) => r.date,
                },
                {
                  key: 'sold',
                  header: '판매 / 정원',
                  align: 'right',
                  width: 'w-37.5',
                  render: (r) => `${n(r.sold)} / ${n(r.capacity)}`,
                },
                {
                  key: 'status',
                  header: '상태',
                  width: 'w-28',
                  render: (r) => <Badge tone={r.badge.tone}>{r.badge.label}</Badge>,
                },
              ]}
            />
            <p className="text-body-s text-text-tertiary">
              줄을 고르면 오른쪽에서 판매 중 변경을 할 수 있어요.
            </p>
          </ConsoleCard>

          <SaleChangeCard key={race.id} row={race} />
        </div>
      )}
    </ConsolePage>
  )
}
