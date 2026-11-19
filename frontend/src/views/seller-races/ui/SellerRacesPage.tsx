'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'
import { useEffect, useRef } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'

import { ConsoleCard, ConsoleLoading, ConsolePage } from '@/widgets/console-page'
import {
  myRacesQueryOptions,
  SaleRateBar,
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
  Icon,
  TextField,
  useToast,
} from '@/shared/ui'

const n = formatNumber

// 상태 칩 순서. 「판매 전」은 쓰지 않고 코드의 다섯 가지 상태를 그대로 쓴다
const STATUS_ORDER: SellerSaleStatus[] = ['ON_SALE', 'SCHEDULED', 'CLOSED', 'STOPPED', 'CANCELED']
const searchSchema = z.object({ q: z.string().trim().max(50) })
const FILTERS = ['ALL', ...STATUS_ORDER]

// 피그마 Final v3 · 판매자 · 대회 관리: 내 대회 표를 본문 폭 전체로, 줄을 누르면 대회 상세
export function SellerRacesPage({ registeredRaceId }: { registeredRaceId?: string }) {
  const races = useQuery({
    ...myRacesQueryOptions(),
    select: (list) => list.map(toSellerRaceRow),
  })
  const [url, setUrl] = useSearchParamsState({ status: 'ALL' }, { status: FILTERS })
  // 대회 이름 찾기: 칸 하나라 zod로 검사만 하고 화면 안에서 거른다
  const { register, control } = useForm<{ q: string }>({
    resolver: zodResolver(searchSchema),
    defaultValues: { q: '' },
  })
  const query = (useWatch({ control, name: 'q' }) ?? '').trim().toLowerCase()
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
  const byStatus = url.status === 'ALL' ? all : all.filter((r) => r.saleStatus === url.status)
  const rows = query ? byStatus.filter((r) => r.title.toLowerCase().includes(query)) : byStatus
  const countOf = (status: string) =>
    status === 'ALL' ? all.length : all.filter((r) => r.saleStatus === status).length

  return (
    <ConsolePage
      title="대회 관리"
      actions={
        <Link href="/seller/races/new?from=races" className={buttonClassName()}>
          대회 등록하기
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
        <ConsoleCard>
          <EmptyState
            title="등록한 대회가 없어요"
            action={
              <Link href="/seller/races/new?from=races" className={buttonClassName()}>
                대회 등록하기
              </Link>
            }
          />
        </ConsoleCard>
      ) : (
        <>
          <div className="flex items-center justify-between gap-4">
            <div className="flex flex-wrap gap-2" role="group" aria-label="판매 상태">
              {FILTERS.map((status) => (
                <Chip
                  key={status}
                  selected={url.status === status}
                  onClick={() => setUrl({ status })}
                >
                  {status === 'ALL' ? '전체' : SELLER_SALE_STATUS[status as SellerSaleStatus].label}{' '}
                  <span
                    className={cn(
                      url.status !== status && 'text-caption-regular text-text-secondary',
                    )}
                  >
                    {countOf(status)}
                  </span>
                </Chip>
              ))}
            </div>
            <TextField
              compact
              leading={<Icon name="Search" />}
              aria-label="대회 이름으로 찾기"
              placeholder="대회 이름으로 찾기"
              className="w-80"
              {...register('q')}
            />
          </div>
          <div className="rounded-md border border-border-default px-6 py-2">
            {rows.length === 0 ? (
              <EmptyState title="해당하는 대회가 없어요" />
            ) : (
              <table aria-label="내 대회" className="w-full table-fixed text-body1-regular">
                <thead>
                  <tr className="border-b border-border-default text-caption-medium text-text-secondary">
                    <th className="h-11 px-3 text-left font-medium">대회</th>
                    <th className="h-11 w-30 px-3 text-left font-medium">대회일</th>
                    <th className="h-11 w-48 px-3 text-left font-medium">종목</th>
                    <th className="h-11 w-72 px-3 text-left font-medium">판매 / 정원</th>
                    <th className="h-11 w-32 px-3 text-left font-medium">상태</th>
                    <th className="h-11 w-12 px-3">
                      <span className="sr-only">상세</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.id} className="h-16 border-b border-border-default">
                      <td className="truncate px-3">
                        <Link
                          href={`/seller/races/${row.id}`}
                          className={cn('rounded-sm text-body1-medium', FOCUS_RING)}
                        >
                          {row.title}
                        </Link>
                      </td>
                      <td className="px-3 tabular-nums">{row.date}</td>
                      <td className="truncate px-3">{row.courseNames}</td>
                      <td className="px-3">
                        <SaleRateBar
                          aria-label={`${row.title} 판매율`}
                          percent={
                            row.capacity > 0 ? Math.round((row.sold / row.capacity) * 100) : 0
                          }
                          label={`${n(row.sold)} / ${n(row.capacity)}`}
                        />
                      </td>
                      <td
                        className={cn(
                          'px-3 text-body1-medium',
                          row.saleStatus ? SELLER_SALE_STATUS_TEXT[row.saleStatus] : '',
                        )}
                      >
                        {row.badge.label}
                        {row.saleStatus === 'SCHEDULED' && (
                          <span className="block text-caption-regular text-text-secondary">
                            {row.saleOpenText}
                          </span>
                        )}
                      </td>
                      <td className="px-3">
                        <Link
                          href={`/seller/races/${row.id}`}
                          aria-label={`${row.title} 상세`}
                          className={cn('flex justify-end rounded-sm', FOCUS_RING)}
                        >
                          <Icon name="ChevronRight" className="size-4" />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </>
      )}
    </ConsolePage>
  )
}
