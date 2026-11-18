'use client'

import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'
import { useEffect, useRef } from 'react'

import { ConsoleLoading, ConsolePage } from '@/widgets/console-page'
import {
  myRacesQueryOptions,
  SELLER_SALE_STATUS,
  SELLER_SALE_STATUS_TEXT,
  toSellerRaceRow,
  type SellerSaleStatus,
} from '@/entities/race'
import { ApiError } from '@/shared/api'
import { cn, copyText, formatNumber, useSearchParamsState } from '@/shared/lib'
import {
  Alert,
  buttonClassName,
  Chip,
  EmptyState,
  FOCUS_RING,
  ProgressBar,
  useToast,
} from '@/shared/ui'

const n = formatNumber

// 상태 칩 순서. 「판매 전」은 쓰지 않고 코드의 다섯 가지 상태를 그대로 쓴다
const STATUS_ORDER = Object.keys(SELLER_SALE_STATUS) as SellerSaleStatus[]
const FILTERS = ['ALL', ...STATUS_ORDER]

// 피그마 Final v3 · 판매자 · 대회 관리: 내 대회 표를 본문 폭 전체로, 줄을 누르면 대회 상세
export function SellerRacesPage({ registeredRaceId }: { registeredRaceId?: string }) {
  const races = useQuery({
    ...myRacesQueryOptions(),
    select: (list) => list.map(toSellerRaceRow),
  })
  const [url, setUrl] = useSearchParamsState({ status: 'ALL' }, { status: FILTERS })
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
          void copyText(`${window.location.origin}/races/${registeredRaceId}`).then((copied) =>
            toast(
              copied
                ? { message: '링크를 복사했어요', tone: 'success' }
                : { message: '복사하지 못했어요. 잠시 뒤 다시 시도해 주세요.', tone: 'error' },
            ),
          ),
      },
    })
  }, [registeredRaceId, toast])

  const all = races.data ?? []
  const rows = url.status === 'ALL' ? all : all.filter((r) => r.saleStatus === url.status)
  const countOf = (status: string) =>
    status === 'ALL' ? all.length : all.filter((r) => r.saleStatus === status).length

  return (
    <ConsolePage
      title="대회 관리"
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
      ) : all.length === 0 ? (
        <EmptyState
          title="아직 등록한 대회가 없어요"
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
        <div className="flex flex-col gap-4 rounded-md border border-border-default p-6">
          <div className="flex flex-wrap gap-2" role="group" aria-label="판매 상태">
            {FILTERS.map((status) => (
              <Chip
                key={status}
                selected={url.status === status}
                onClick={() => setUrl({ status })}
              >
                {status === 'ALL' ? '전체' : SELLER_SALE_STATUS[status as SellerSaleStatus].label}{' '}
                {countOf(status)}
              </Chip>
            ))}
          </div>
          <table aria-label="내 대회" className="w-full table-fixed text-body2-regular">
            <thead>
              <tr className="border-b border-border-default text-body2-medium text-text-secondary">
                <th className="h-11 text-left font-medium">대회</th>
                <th className="h-11 w-28 text-right font-medium">대회일</th>
                <th className="h-11 w-36 text-right font-medium">판매 / 정원</th>
                <th className="h-11 w-48 pl-6 text-left font-medium">판매율</th>
                <th className="h-11 w-28 pl-4 text-left font-medium">상태</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id} className="h-14 border-b border-border-default last:border-b-0">
                  <td className="truncate">
                    <Link
                      href={`/seller/races/${row.id}`}
                      className={cn('rounded-sm text-body2-medium', FOCUS_RING)}
                    >
                      {row.title}
                    </Link>
                  </td>
                  <td className="text-right tabular-nums">{row.date}</td>
                  <td className="text-right tabular-nums">{`${n(row.sold)} / ${n(row.capacity)}`}</td>
                  <td className="pl-6">
                    <ProgressBar
                      size="s"
                      aria-label={`${row.title} 판매율`}
                      value={row.capacity > 0 ? Math.round((row.sold / row.capacity) * 100) : 0}
                    />
                  </td>
                  <td
                    className={cn(
                      'pl-4 text-body2-medium',
                      row.saleStatus ? SELLER_SALE_STATUS_TEXT[row.saleStatus] : '',
                    )}
                  >
                    {row.badge.label}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </ConsolePage>
  )
}
