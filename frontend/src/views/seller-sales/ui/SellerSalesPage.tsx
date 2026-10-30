'use client'

import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'
import { useState } from 'react'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import {
  formatDDay,
  formatRaceDateTime,
  myRacesQueryOptions,
  raceQueryOptions,
  toSellerRaceRow,
} from '@/entities/race'
import { ApiError, type productModel } from '@/shared/api'
import { formatNumber } from '@/shared/lib'
import {
  Alert,
  Button,
  buttonClassName,
  EmptyState,
  Select,
  Skeleton,
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
  return { sold, quota, remaining: Math.max(quota - sold - reserved, 0) }
}

// 피그마 V2 · S03 판매 현황 (주문 · 참가자)
// 대회 정보와 자리 수는 내 대회 목록(GET /api/v1/product/races/mine), 환불 기한은 대회 상세로 채운다.
export function SellerSalesPage() {
  const races = useQuery(myRacesQueryOptions())
  const [picked, setPicked] = useState<string | null>(null)
  const list = races.data ?? []
  // 고르기 전에는 판매 중인 대회를 먼저, 없으면 가장 최근 대회
  const race =
    list.find((r) => String(r.raceId) === picked) ??
    list.find((r) => r.saleStatus === 'ON_SALE') ??
    list[0]

  if (races.isError) {
    return (
      <ConsolePage title="주문 · 참가자">
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
      <ConsolePage title="주문 · 참가자">
        <Skeleton className="h-96 w-full" />
      </ConsolePage>
    )
  }
  if (!race || race.raceId === undefined) {
    return (
      <ConsolePage title="주문 · 참가자">
        <EmptyState
          title="등록한 대회가 없어요"
          description="대회를 등록하면 여기에서 판매 현황을 볼 수 있어요"
          action={
            <Link
              href="/seller/races/new?from=sales"
              className={buttonClassName({ variant: 'secondary' })}
            >
              대회 등록
            </Link>
          }
        />
      </ConsolePage>
    )
  }

  const row = toSellerRaceRow(race)
  return (
    <ConsolePage
      title={row.title}
      description={`${row.badge.label} · ${row.period}`}
      actions={
        <>
          <a href="#download" className={buttonClassName({ variant: 'secondary' })}>
            명단 내려받기
          </a>
          <Link href="/seller/races" className={buttonClassName({ variant: 'secondary' })}>
            대회 수정
          </Link>
        </>
      }
    >
      <Select
        label="대회"
        className="w-100"
        items={list.map((r) => ({ value: String(r.raceId), label: r.title ?? '' }))}
        value={String(race.raceId)}
        onValueChange={(value) => setPicked(value)}
      />
      <SalesBody race={race} raceId={race.raceId} />
    </ConsolePage>
  )
}

function SalesBody({ race, raceId }: { race: productModel.SellerRaceDto; raceId: number }) {
  const detail = useQuery(raceQueryOptions(raceId))
  const seats = seatsOf(race)
  const refundDeadline = detail.data?.refundDeadline

  return (
    <>
      <div className="flex gap-6">
        <StatTile
          label="전체 판매"
          value={formatNumber(seats.sold)}
          unit={`/ ${formatNumber(seats.quota)}명`}
        />
        <StatTile
          tone="accent"
          label="남은 자리"
          value={formatNumber(seats.remaining)}
          unit={`/ ${formatNumber(seats.quota)}명`}
        />
        {race.applicationCloseAt ? (
          <StatTile
            label="접수 마감"
            value={formatDDay(race.applicationCloseAt)}
            unit={formatRaceDateTime(race.applicationCloseAt)}
          />
        ) : (
          <StatTile label="접수 마감" value="-" />
        )}
        {refundDeadline ? (
          <StatTile
            label="환불 기한"
            value={formatDDay(refundDeadline)}
            unit={formatRaceDateTime(refundDeadline)}
          />
        ) : detail.isPending ? (
          <Skeleton className="h-24 flex-1" />
        ) : (
          <StatTile label="환불 기한" value="-" />
        )}
      </div>

      {refundDeadline && (
        <Alert
          title={`환불 기한(${formatRaceDateTime(refundDeadline)})까지는 취소가 생길 수 있어요`}
          description="취소로 풀린 자리는 남은 자리로 자동으로 돌아가 다시 판매돼요."
        />
      )}

      {/* TODO(api): 판매자용 대회별 참가자(주문) 목록 API가 없다. 생기면 시안 S03의 표로 바꾼다 */}
      <ConsoleCard title={`참가자 ${formatNumber(seats.sold)}명`}>
        <EmptyState
          title="참가자 목록은 준비 중이에요"
          description="판매 인원은 위 전체 판매에서 볼 수 있어요."
        />
        <p className="text-caption text-text-tertiary">
          참가권 결제 한 건이 주문 1건이에요. 구매자는 닉네임으로 보여 줘요. 취소한 주문의 자리는
          남은 자리로 자동 복구돼요.
        </p>
      </ConsoleCard>

      <div id="download">
        {/* TODO(api): 명단 파일 API가 생기면 사유를 남기고 엑셀 파일을 받는다 */}
        <ConsoleCard title="명단 내려받기">
          <Alert
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
          <p className="text-caption text-text-tertiary">
            내려받기 기록은 1년 보관돼요. 참가자 정보는 대회가 끝나고 1년 뒤 파기해야 해요.
          </p>
        </ConsoleCard>
      </div>
    </>
  )
}
