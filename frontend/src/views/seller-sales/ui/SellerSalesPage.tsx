'use client'

import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import { RosterExportButton } from '@/features/seller-roster-export'
import { formatRaceDate, myRacesQueryOptions, raceQueryOptions, SaleRateBar } from '@/entities/race'
import { ApiError, type productModel } from '@/shared/api'
import { formatNumber, useSearchParamsState } from '@/shared/lib'
import {
  Alert,
  buttonClassName,
  EmptyState,
  Select,
  Skeleton,
  StatStrip,
  StatTile,
} from '@/shared/ui'

import { SalesRoster } from './SalesRoster'

function errorText(error: unknown) {
  return (error instanceof ApiError && error.serverMessage) || '잠시 뒤 다시 시도해 주세요.'
}

/** 대회 전체의 판매 · 정원 · 결제 대기 자리. 남은 자리 = 정원 - 판매 - 결제 대기 */
function seatsOf(race: productModel.SellerRaceDto) {
  const courses = race.courses ?? []
  const sold = courses.reduce((sum, c) => sum + (c.sold ?? 0), 0)
  const quota = courses.reduce((sum, c) => sum + (c.quota ?? 0), 0)
  const reserved = courses.reduce((sum, c) => sum + (c.reserved ?? 0), 0)
  return { sold, quota, reserved, remaining: Math.max(quota - sold - reserved, 0) }
}

// 피그마 V4 · 판매자 · 판매 현황 (2070:66595)
// 대회 정보와 자리 수는 내 대회 목록(GET /api/v1/product/races/mine), 환불 기한은 대회 상세로 채운다.
// 참가자 · 파티별 명단은 판매자 주문 API, 명단 파일은 명단 내려받기 API로 받는다.
export function SellerSalesPage() {
  const races = useQuery(myRacesQueryOptions())
  // 고른 대회는 주소(?race=)에 담는다. 대회 상세의 바로 가기가 번호를 넘긴다.
  // 명단의 탭 · 쪽도 주소에 있어서, 대회를 바꾸면 첫 쪽으로 돌린다
  const [url, setUrl] = useSearchParamsState({ race: '', tab: 'participants', page: '1' })
  const picked = url.race
  const list = races.data ?? []
  // 고르기 전에는 판매 중인 대회를 먼저, 없으면 가장 최근 대회
  const race =
    list.find((r) => String(r.raceId) === picked) ??
    list.find((r) => r.saleStatus === 'ON_SALE') ??
    list[0]

  if (races.isError) {
    return (
      <ConsolePage title="판매 현황">
        <Alert
          tone="danger"
          title="대회 목록을 불러오지 못했어요"
          description={errorText(races.error)}
        />
      </ConsolePage>
    )
  }
  if (!races.data) {
    return (
      <ConsolePage title="판매 현황">
        <Skeleton className="h-96 w-full" />
      </ConsolePage>
    )
  }
  if (!race || race.raceId === undefined) {
    return (
      <ConsolePage title="판매 현황">
        <ConsoleCard>
          <EmptyState
            icon={null}
            title="등록한 대회가 없어요"
            action={
              <Link href="/seller/races/new?from=sales" className={buttonClassName()}>
                대회 등록하기
              </Link>
            }
          />
        </ConsoleCard>
      </ConsolePage>
    )
  }

  return (
    <ConsolePage title="판매 현황" actions={<RosterExportButton key={race.raceId} race={race} />}>
      <Select
        aria-label="대회"
        className="w-100"
        items={list.map((r) => ({ value: String(r.raceId), label: r.title ?? '' }))}
        value={String(race.raceId)}
        onValueChange={(value) => setUrl({ race: value ?? '', page: '1' })}
      />
      <SalesBody race={race} raceId={race.raceId} />
    </ConsolePage>
  )
}

function soldRate(sold: number, quota: number) {
  return quota > 0 ? Math.round((sold / quota) * 100) : 0
}

const CARD = 'rounded-md border border-border-default shadow-sm'

function SalesBody({ race, raceId }: { race: productModel.SellerRaceDto; raceId: number }) {
  const detail = useQuery(raceQueryOptions(raceId))
  const seats = seatsOf(race)
  const refundDeadline = detail.data?.refundDeadline

  return (
    <>
      <div role="group" aria-label="판매 숫자">
        <StatStrip>
          <StatTile
            layout="cell"
            label="판매"
            value={formatNumber(seats.sold)}
            unit={`/ ${formatNumber(seats.quota)}명`}
          />
          <StatTile
            layout="cell"
            size="s"
            label="결제 대기"
            value={formatNumber(seats.reserved)}
            unit="명"
          />
          <StatTile
            layout="cell"
            size="s"
            label="남은 자리"
            value={formatNumber(seats.remaining)}
            unit="자리"
          />
          <StatTile
            layout="cell"
            size="s"
            label="환불 기한"
            value={
              detail.isPending ? (
                <Skeleton className="h-8 w-24" />
              ) : refundDeadline ? (
                formatRaceDate(refundDeadline)
              ) : (
                '-'
              )
            }
          />
        </StatStrip>
      </div>

      <ConsoleCard title="종목별 판매" className={CARD}>
        <table aria-label="종목별 판매" className="w-full table-fixed text-body1-regular">
          <thead>
            <tr className="border-b border-border-default text-caption-medium text-text-secondary">
              <th className="h-11 w-40 px-3 text-left font-medium">종목</th>
              <th className="h-11 px-3 text-left font-medium">판매 / 정원</th>
              <th className="h-11 w-35 px-3 text-right font-medium">결제 대기</th>
              <th className="h-11 w-35 px-3 text-right font-medium">남은 자리</th>
            </tr>
          </thead>
          <tbody>
            {(race.courses ?? []).map((c) => {
              const sold = c.sold ?? 0
              const quota = c.quota ?? 0
              const reserved = c.reserved ?? 0
              return (
                <tr key={c.raceCourseId} className="h-14 border-b border-border-default">
                  <td className="truncate px-3 text-body1-medium">{c.name}</td>
                  <td className="px-3">
                    <SaleRateBar
                      aria-label={`${c.name} 판매율`}
                      barClassName="w-60"
                      percent={soldRate(sold, quota)}
                      label={`${formatNumber(sold)} / ${formatNumber(quota)}명`}
                    />
                  </td>
                  <td className="px-3 text-right tabular-nums">{formatNumber(reserved)}명</td>
                  <td className="px-3 text-right tabular-nums">
                    {formatNumber(Math.max(quota - sold - reserved, 0))}자리
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </ConsoleCard>

      <SalesRoster key={raceId} raceId={raceId} />
    </>
  )
}
