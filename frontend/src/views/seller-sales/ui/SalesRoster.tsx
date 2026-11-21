'use client'

import { keepPreviousData } from '@tanstack/react-query'
import { useState, type FormEvent, type ReactNode } from 'react'
import { z } from 'zod'

import { formatRaceDate } from '@/entities/race'
import { ApiError, orderApi, unwrapData } from '@/shared/api'
import { cn, formatNumber, useSearchParamsState } from '@/shared/lib'
import {
  Alert,
  Button,
  FOCUS_RING,
  Icon,
  Skeleton,
  Tab,
  TabBar,
  Tabs,
  TextField,
} from '@/shared/ui'

import {
  applyMethod,
  kitOptionText,
  ORDER_STATUS,
  parsePartyNo,
  PARTY_STATUS,
  partyNo,
  type StatusTone,
} from '../lib/roster'

/** 한 쪽에 받는 줄 수 (서버는 100까지 받는다) */
export const ROSTER_PAGE_SIZE = 50

const TABS = ['participants', 'parties'] as const
// 주소의 쪽 번호. 숫자가 아니거나 너무 크면 첫 쪽 (서버는 쪽 번호를 int로 받는다)
const pageSchema = z.coerce.number().int().min(1).max(1_000_000).catch(1)
const TH = 'h-11 px-3 text-left text-caption-medium font-medium text-text-secondary'
const TD = 'truncate px-3'
const TONE: Record<StatusTone, string> = {
  default: 'text-body1-medium text-text-primary',
  muted: 'text-caption-medium text-text-secondary',
  success: 'text-body1-medium text-text-success',
  danger: 'text-body1-medium text-text-danger',
}

function errorText(error: unknown) {
  return (error instanceof ApiError && error.serverMessage) || '잠시 뒤 다시 시도해 주세요.'
}

// 시안 2070:66595 · 2070:66774 · 2072:83121: 참가자 · 파티별 탭과 찾기, 표, 쪽 넘기기.
// 탭 · 쪽은 주소에 담는다. 찾는 말은 참가자 이름이라 주소(방문 기록 · 접근 로그 · 화면 녹화)에 남기지 않고 화면 상태로만 둔다.
// 대회를 바꾸면 위에서 key로 다시 그려 찾는 말도 비운다.
export function SalesRoster({ raceId }: { raceId: number }) {
  const [url, setUrl] = useSearchParamsState({ tab: 'participants', page: '1' }, { tab: [...TABS] })
  const [draft, setDraft] = useState('')
  const [q, setQ] = useState('')
  const page = pageSchema.parse(url.page)
  const parties = url.tab === 'parties'

  function search(event: FormEvent) {
    event.preventDefault()
    setQ(draft.trim())
    setUrl({ page: '1' })
  }
  function clearSearch() {
    setDraft('')
    setQ('')
    setUrl({ page: '1' })
  }

  const placeholder = parties ? '파티 번호로 찾기' : '이름 · 주문번호로 찾기'
  const list = {
    page,
    onPage: (next: number) => setUrl({ page: String(next) }),
    q,
    onClear: clearSearch,
  }

  return (
    <section
      aria-label="명단"
      className="flex flex-col gap-4 rounded-md border border-border-default p-6 shadow-sm"
    >
      <div className="flex items-start gap-4 border-b border-border-default">
        <Tabs
          value={url.tab}
          onValueChange={(tab) => {
            setDraft('')
            setQ('')
            setUrl({ tab: String(tab), page: '1' })
          }}
        >
          <TabBar layout="hug" className="border-b-0 px-0">
            <Tab value="participants">참가자</Tab>
            <Tab value="parties">파티별</Tab>
          </TabBar>
        </Tabs>
        <form role="search" onSubmit={search} className="ml-auto w-80 pt-1">
          <TextField
            compact
            leading={<Icon name="Search" />}
            aria-label={placeholder}
            placeholder={placeholder}
            value={draft}
            maxLength={50}
            onChange={(event) => setDraft(event.target.value)}
          />
        </form>
      </div>
      {parties ? (
        <PartyList raceId={raceId} {...list} />
      ) : (
        <ParticipantList raceId={raceId} {...list} />
      )}
    </section>
  )
}

type ListProps = {
  raceId: number
  page: number
  onPage: (page: number) => void
  q: string
  onClear: () => void
}

function ParticipantList({ raceId, page, onPage, q, onClear }: ListProps) {
  const base = { raceId, size: ROSTER_PAGE_SIZE }
  const orders = orderApi.useFindSellerOrders(
    { ...base, page: page - 1, keyword: q || undefined },
    { query: { select: unwrapData, placeholderData: keepPreviousData } },
  )
  // 전체 수는 찾는 말과 상관없이 대회의 전체 참가자 수 (찾지 않을 때의 첫 쪽과 같은 요청이라 캐시를 같이 쓴다)
  const all = orderApi.useFindSellerOrders(
    { ...base, page: 0 },
    { query: { select: unwrapData, placeholderData: keepPreviousData } },
  )
  const total = all.data?.totalElements

  return (
    <RosterBody
      label="참가자"
      totalText={total === undefined ? undefined : `전체 ${formatNumber(total)}명`}
      error={orders.error}
      totalPages={orders.data?.totalPages ?? 0}
      emptyText={q ? `「${q}」와 맞는 참가자가 없어요` : '아직 참가자가 없어요'}
      searching={Boolean(q)}
      onClear={onClear}
      page={page}
      onPage={onPage}
      head={
        <>
          <th className={cn(TH, 'w-50')}>주문번호</th>
          <th className={TH}>참가자</th>
          <th className={cn(TH, 'w-25')}>종목</th>
          <th className={cn(TH, 'w-50')}>키트 옵션</th>
          <th className={cn(TH, 'w-35')}>신청 방식</th>
          <th className={cn(TH, 'w-30')}>결제일</th>
          <th className={cn(TH, 'w-25')}>상태</th>
        </>
      }
      rows={orders.data?.orders?.map((order) => {
        const status = order.status ? ORDER_STATUS[order.status] : undefined
        return (
          <tr key={order.orderItemId} className="h-14 border-b border-border-default">
            <td className={TD}>{order.orderNo}</td>
            {/* 참가자 이름은 Clarity 화면 녹화에서 가린다 */}
            <td className={TD} data-clarity-mask="true">
              {order.participantName}
            </td>
            <td className={TD}>{order.courseName}</td>
            <td className={TD}>{kitOptionText(order)}</td>
            <td className={TD}>{applyMethod(order.partyId)}</td>
            <td className={TD}>{order.paidAt ? formatRaceDate(order.paidAt) : '-'}</td>
            <td className={cn(TD, status && TONE[status.tone])}>{status?.label}</td>
          </tr>
        )
      })}
    />
  )
}

function PartyList({ raceId, page, onPage, q, onClear }: ListProps) {
  const partyId = q ? parsePartyNo(q) : undefined
  // 파티 번호로 읽히지 않는 말은 서버에 묻지 않고 바로 없다고 보여 준다
  const unreadable = Boolean(q) && partyId === undefined
  const parties = orderApi.useFindSellerParties(
    { raceId, size: ROSTER_PAGE_SIZE, page: page - 1, partyId },
    { query: { select: unwrapData, placeholderData: keepPreviousData, enabled: !unreadable } },
  )
  const all = orderApi.useFindSellerParties(
    { raceId, size: ROSTER_PAGE_SIZE, page: 0 },
    { query: { select: unwrapData, placeholderData: keepPreviousData } },
  )
  const total = all.data?.totalElements

  return (
    <RosterBody
      label="파티별"
      totalText={total === undefined ? undefined : `전체 ${formatNumber(total)}개 파티`}
      error={unreadable ? null : parties.error}
      totalPages={unreadable ? 0 : (parties.data?.totalPages ?? 0)}
      emptyText={q ? `「${q}」와 맞는 파티가 없어요` : '아직 신청한 파티가 없어요'}
      searching={Boolean(q)}
      onClear={onClear}
      page={page}
      onPage={onPage}
      head={
        <>
          <th className={cn(TH, 'w-35')}>파티 번호</th>
          <th className={TH}>파티장</th>
          <th className={TH}>크루</th>
          <th className={cn(TH, 'w-25 text-right')}>인원</th>
          <th className={cn(TH, 'w-40 text-right')}>결제</th>
          <th className={cn(TH, 'w-30')}>상태</th>
        </>
      }
      rows={
        unreadable
          ? []
          : parties.data?.parties?.map((party) => {
              const status = party.status ? PARTY_STATUS[party.status] : undefined
              return (
                <tr key={party.partyId} className="h-14 border-b border-border-default">
                  <td className={TD}>{partyNo(party.partyId ?? 0)}</td>
                  {/* TODO(api): 파티 목록 응답에 파티장 이름과 크루 이름이 없다(크루 ID만 온다) */}
                  <td className={TD}>-</td>
                  <td className={TD}>-</td>
                  <td className={cn(TD, 'text-right')}>
                    {formatNumber(party.appliedCount ?? 0)}명
                  </td>
                  <td className={cn(TD, 'text-right')}>
                    {formatNumber(party.paidCount ?? 0)} / {formatNumber(party.appliedCount ?? 0)}명
                    완료
                  </td>
                  <td className={cn(TD, status && TONE[status.tone])}>{status?.label}</td>
                </tr>
              )
            })
      }
    />
  )
}

type BodyProps = {
  label: string
  totalText?: string
  error: unknown
  totalPages: number
  emptyText: string
  searching: boolean
  onClear: () => void
  page: number
  onPage: (page: number) => void
  head: ReactNode
  rows?: ReactNode[]
}

function RosterBody({
  label,
  totalText,
  error,
  totalPages,
  emptyText,
  searching,
  onClear,
  page,
  onPage,
  head,
  rows,
}: BodyProps) {
  if (error) {
    return (
      <Alert
        tone="danger"
        title={`${label === '참가자' ? '참가자를' : '파티를'} 불러오지 못했어요`}
        description={errorText(error)}
      />
    )
  }
  return (
    <>
      {totalText ? (
        <p className="text-caption-medium text-text-secondary">{totalText}</p>
      ) : (
        <Skeleton className="h-4 w-20" />
      )}
      {!rows ? (
        <Skeleton className="h-56 w-full" />
      ) : (
        <table
          aria-label={label}
          className="w-full table-fixed text-body1-regular text-text-primary"
        >
          <thead>
            <tr className="border-b border-border-default">{head}</tr>
          </thead>
          <tbody>{rows}</tbody>
        </table>
      )}
      {!rows ? null : rows.length === 0 && page > 1 && totalPages > 0 ? (
        // 주소의 쪽이 전체 쪽 수를 넘었다(공유한 주소 등). 빈 문구 대신 앞쪽으로 갈 수 있게 쪽 넘기기만 보인다
        <PageNav page={page} totalPages={totalPages} onPage={onPage} />
      ) : rows.length === 0 ? (
        // 시안 2072:83121: 표 머리 아래 가운데에 문구와 찾기 지우기
        <div className="flex flex-col items-center gap-4 py-24">
          {/* 찾는 말이 참가자 이름일 수 있어 화면 녹화에서 가린다 */}
          <p className="text-title3 text-text-primary" data-clarity-mask="true">
            {emptyText}
          </p>
          {searching && (
            <Button variant="secondary" onClick={onClear}>
              찾기 지우기
            </Button>
          )}
        </div>
      ) : (
        totalPages > 1 && <PageNav page={page} totalPages={totalPages} onPage={onPage} />
      )}
    </>
  )
}

function PageNav({
  page,
  totalPages,
  onPage,
}: {
  page: number
  totalPages: number
  onPage: (page: number) => void
}) {
  const arrow = cn('rounded-sm text-text-primary disabled:text-text-disabled', FOCUS_RING)
  return (
    <nav aria-label="쪽" className="flex items-center justify-center gap-4">
      <button
        type="button"
        aria-label="이전 쪽"
        disabled={page <= 1}
        onClick={() => onPage(page - 1)}
        className={arrow}
      >
        <Icon name="ChevronLeft" className="size-5" />
      </button>
      <span className="text-body1-medium">
        {page} / {totalPages}
      </span>
      <button
        type="button"
        aria-label="다음 쪽"
        disabled={page >= totalPages}
        onClick={() => onPage(page + 1)}
        className={arrow}
      >
        <Icon name="ChevronRight" className="size-5" />
      </button>
    </nav>
  )
}
