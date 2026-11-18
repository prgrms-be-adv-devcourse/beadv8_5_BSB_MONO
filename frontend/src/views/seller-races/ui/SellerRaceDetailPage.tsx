'use client'

import { useQuery } from '@tanstack/react-query'
import type { Route } from 'next'
import Link from 'next/link'
import { useState } from 'react'

import { ConsoleLoading, ConsolePage } from '@/widgets/console-page'
import { useToggleSale } from '@/features/seller-race'
import {
  isRaceEditable,
  myRacesQueryOptions,
  SELLER_SALE_STATUS_TEXT,
  toSellerRaceRow,
} from '@/entities/race'
import { ApiError } from '@/shared/api'
import { cn, formatNumber } from '@/shared/lib'
import { Alert, buttonClassName, Dialog, EmptyState, FOCUS_RING, useToast } from '@/shared/ui'

import { SaleChangeCard } from './SaleChangeCard'

const n = formatNumber

// 피그마 Final v3 · 판매자 · 대회 상세: 상태 글자 → 대회명 → 대회일, 종목 표, 판매 상태 버튼, 변경
export function SellerRaceDetailPage({ raceId }: { raceId: number }) {
  const races = useQuery(myRacesQueryOptions())
  const race = races.data?.find((r) => r.raceId === raceId)
  const row = race && toSellerRaceRow(race)
  const [stopOpen, setStopOpen] = useState(false)
  const toast = useToast()
  const stop = useToggleSale(raceId, 'stop')
  const resume = useToggleSale(raceId, 'resume')

  const saleAction = (mutation: typeof stop, done: string) =>
    mutation.mutate(undefined, {
      onSuccess: () => {
        setStopOpen(false)
        toast({ message: done, tone: 'success' })
      },
      onError: (error) =>
        toast({
          message:
            (error instanceof ApiError && error.serverMessage) || '잠시 뒤 다시 시도해 주세요.',
          tone: 'error',
        }),
    })

  return (
    <ConsolePage
      title={row?.title ?? '대회 상세'}
      description={row ? `대회일 ${row.date}` : undefined}
      actions={
        row && (
          <>
            {row.saleStatus === 'ON_SALE' && (
              <button
                type="button"
                className={buttonClassName({ variant: 'secondary' })}
                onClick={() => setStopOpen(true)}
              >
                판매 멈추기
              </button>
            )}
            {row.saleStatus === 'STOPPED' && (
              <button
                type="button"
                className={buttonClassName({ variant: 'secondary' })}
                disabled={resume.isPending}
                onClick={() => saleAction(resume, '판매를 다시 열었어요')}
              >
                다시 열기
              </button>
            )}
          </>
        )
      }
    >
      <Link
        href="/seller/races"
        className={cn('self-start rounded-sm text-body2-medium', FOCUS_RING)}
      >
        ← 대회 관리
      </Link>
      {races.isPending ? (
        <ConsoleLoading />
      ) : races.isError ? (
        <Alert
          tone="danger"
          title="대회 정보를 불러오지 못했어요"
          description={
            (races.error instanceof ApiError && races.error.serverMessage) ||
            '잠시 후 다시 시도해 주세요.'
          }
        />
      ) : !race || !row ? (
        <EmptyState title="찾을 수 없는 대회예요" />
      ) : (
        <>
          <p
            className={cn(
              'text-body2-medium',
              row.saleStatus ? SELLER_SALE_STATUS_TEXT[row.saleStatus] : '',
            )}
          >
            {row.badge.label}
          </p>
          <section
            aria-label="종목별 판매"
            className="flex flex-col gap-2 rounded-md border border-border-default p-6"
          >
            <h2 className="text-body1-bold">종목</h2>
            <table aria-label="종목" className="w-full table-fixed text-body2-regular">
              <thead>
                <tr className="border-b border-border-default text-body2-medium text-text-secondary">
                  <th className="h-11 text-left font-medium">종목</th>
                  <th className="h-11 w-32 text-right font-medium">참가비</th>
                  <th className="h-11 w-32 text-right font-medium">정원</th>
                  <th className="h-11 w-32 text-right font-medium">판매</th>
                  <th className="h-11 w-32 text-right font-medium">결제 대기</th>
                </tr>
              </thead>
              <tbody>
                {(race.courses ?? []).map((course) => (
                  <tr
                    key={course.raceCourseId}
                    className="h-14 border-b border-border-default last:border-b-0"
                  >
                    <td className="truncate">{course.name}</td>
                    <td className="text-right tabular-nums">{n(course.price ?? 0)}원</td>
                    <td className="text-right tabular-nums">{n(course.quota ?? 0)}</td>
                    <td className="text-right tabular-nums">{n(course.sold ?? 0)}</td>
                    <td className="text-right tabular-nums">{n(course.reserved ?? 0)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
          <section
            aria-label="바로 가기"
            className="flex flex-col rounded-md border border-border-default px-6"
          >
            <ShortcutRow href={`/seller/sales?race=${raceId}` as Route} label="판매 현황" />
            <ShortcutRow href={`/seller/kits?race=${raceId}` as Route} label="키트 배송" />
            <ShortcutRow href="/seller/settlements" label="정산" />
            {isRaceEditable(row.saleStatus) && (
              <ShortcutRow
                href={`/seller/races/${raceId}/change` as Route}
                label="일정 변경 · 대회 취소"
                danger
              />
            )}
          </section>
          <SaleChangeCard row={row} />
          <Dialog
            open={stopOpen}
            onOpenChange={setStopOpen}
            title="판매를 멈출까요?"
            body="새로 참가할 수 없어요. 이미 잡은 결제 대기는 기한 안에 결제할 수 있어요."
            confirmLabel="판매 멈추기"
            confirming={stop.isPending}
            onConfirm={() => saleAction(stop, '판매를 멈췄어요')}
          />
        </>
      )}
    </ConsolePage>
  )
}

function ShortcutRow({ href, label, danger }: { href: Route; label: string; danger?: boolean }) {
  return (
    <Link
      href={href}
      className={cn(
        'flex h-14 items-center border-b border-border-default text-body2-medium last:border-b-0',
        danger && 'text-text-danger',
        FOCUS_RING,
      )}
    >
      {label}
    </Link>
  )
}
