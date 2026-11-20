'use client'

import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'

import { ConsoleLoading, ConsolePage } from '@/widgets/console-page'
import { myRacesQueryOptions, toSellerRaceRow } from '@/entities/race'
import { cn, formatNumber } from '@/shared/lib'
import { Alert, EmptyState, FOCUS_RING } from '@/shared/ui'

import { SaleChangeCard } from './SaleChangeCard'

// 피그마 V4 · 판매자 · 판매 중 변경 / 대회 수정
export function SellerRaceEditPage({ raceId }: { raceId: number }) {
  const races = useQuery(myRacesQueryOptions())
  const race = races.data?.find((r) => r.raceId === raceId)
  const row = race && toSellerRaceRow(race)
  const started = row?.saleStatus !== undefined && row.saleStatus !== 'SCHEDULED'
  return (
    <ConsolePage
      title={started ? '판매 중 변경' : '대회 수정'}
      description={
        row
          ? `${row.title} · ${row.badge.label}${started ? ` · ${formatNumber(row.sold)}명 구매` : ''}`
          : undefined
      }
    >
      <Link
        href={`/seller/races/${raceId}`}
        className={cn('self-start rounded-sm text-body2-medium', FOCUS_RING)}
      >
        ← 대회 상세
      </Link>
      {races.isPending ? (
        <ConsoleLoading />
      ) : races.isError ? (
        <Alert
          tone="danger"
          title="대회 정보를 불러오지 못했어요"
          description="잠시 후 다시 시도해 주세요."
        />
      ) : !row ? (
        <EmptyState title="찾을 수 없는 대회예요" />
      ) : (
        <SaleChangeCard row={row} />
      )}
    </ConsolePage>
  )
}
