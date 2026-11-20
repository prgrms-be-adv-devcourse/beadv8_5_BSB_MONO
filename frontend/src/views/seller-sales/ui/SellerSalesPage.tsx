'use client'

import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'

import { CONSOLE_BOX, ConsoleCard, ConsolePage } from '@/widgets/console-page'
import { formatRaceDate, myRacesQueryOptions, raceQueryOptions, SaleRateBar } from '@/entities/race'
import { ApiError, type productModel } from '@/shared/api'
import { formatNumber, useSearchParamsState } from '@/shared/lib'
import {
  Alert,
  Button,
  buttonClassName,
  EmptyState,
  Select,
  Skeleton,
  StatStrip,
  StatTile,
  TextField,
} from '@/shared/ui'

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

// 피그마 V2 · S03 판매 현황 (주문 · 참가자)
// 대회 정보와 자리 수는 내 대회 목록(GET /api/v1/product/races/mine), 환불 기한은 대회 상세로 채운다.
export function SellerSalesPage() {
  const races = useQuery(myRacesQueryOptions())
  // 고른 대회는 주소(?race=)에 담는다. 대회 상세의 바로 가기가 번호를 넘긴다
  const [url, setUrl] = useSearchParamsState({ race: '' })
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
              <Link href="/seller/races/new?from=sales" className={buttonClassName({ size: 's' })}>
                대회 등록하기
              </Link>
            }
          />
        </ConsoleCard>
      </ConsolePage>
    )
  }

  return (
    <ConsolePage
      title="판매 현황"
      actions={
        <a href="#download" className={buttonClassName({ variant: 'secondary' })}>
          명단 내려받기
        </a>
      }
    >
      <Select
        aria-label="대회"
        className="w-100"
        items={list.map((r) => ({ value: String(r.raceId), label: r.title ?? '' }))}
        value={String(race.raceId)}
        onValueChange={(value) => setUrl({ race: value ?? '' })}
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
            label="결제 대기"
            value={formatNumber(seats.reserved)}
            unit="명"
          />
          <StatTile
            layout="cell"
            label="남은 자리"
            value={formatNumber(seats.remaining)}
            unit="자리"
          />
          <StatTile
            layout="cell"
            label="환불 기한"
            value={
              detail.isPending ? (
                <Skeleton className="h-9 w-24" />
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
              <th className="h-10 w-40 px-3 text-left font-medium">종목</th>
              <th className="h-10 px-3 text-left font-medium">판매 / 정원</th>
              <th className="h-10 w-40 px-3 text-right font-medium">결제 대기</th>
              <th className="h-10 w-40 px-3 text-right font-medium">남은 자리</th>
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

      {/* TODO(api): 판매자용 대회별 참가자(주문) 목록 API가 없다. 생기면 시안의 참가자·파티별 표로 바꾼다 */}
      <ConsoleCard className={CARD} title={`참가자 ${formatNumber(seats.sold)}명`}>
        <EmptyState title="참가자 목록은 준비 중이에요" />
      </ConsoleCard>

      <div id="download">
        {/* TODO(api): 명단 파일 API가 생기면 사유를 남기고 엑셀 파일을 받는다 */}
        <ConsoleCard className={CARD} title="명단 내려받기">
          <Alert
            className={CONSOLE_BOX}
            title="엑셀 내려받기는 준비 중이에요"
            description="명단 파일 기능이 열리면 사유를 남기고 내려받을 수 있어요."
          />
          <div className="flex items-end gap-4">
            <Select
              label="범위"
              items={[
                {
                  value: 'confirmed',
                  label: `참가 확정자 전체 (${formatNumber(seats.sold)}명)`,
                },
                ...(race.courses ?? []).map((c) => ({
                  value: String(c.raceCourseId),
                  label: `${c.name ?? ''} 참가 확정자 (${formatNumber(c.sold ?? 0)}명)`,
                })),
              ]}
              defaultValue="confirmed"
              disabled
              className="flex-1"
            />
            <TextField label="내려받는 사유" disabled className="flex-1" />
            <Button disabled>엑셀로 내려받기</Button>
          </div>
        </ConsoleCard>
      </div>
    </>
  )
}
