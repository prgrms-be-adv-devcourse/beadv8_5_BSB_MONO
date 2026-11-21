'use client'

import { keepPreviousData } from '@tanstack/react-query'
import { useState } from 'react'

import { ConsoleMessage } from '@/widgets/console-page'
import { formatMonthDay, PAYOUT_STAGE, payoutStatusLabel } from '@/entities/payout'
import { ApiError, payoutApi, unwrapData, type payoutModel } from '@/shared/api'
import { DateTimeField } from '@/shared/date-picker'
import { cn, formatWon } from '@/shared/lib'
import { DataTable, FOCUS_RING, Icon, Modal, Pagination, Skeleton, TextField } from '@/shared/ui'

import { holdBlockLabel } from '../model/holdable'

type Line = { payout: payoutModel.PayoutDto; holds: payoutModel.PayoutHoldDto[] }

const PAGE_SIZE = 50
// 정산 ID(ST-만든 날-16진수 4자리). 끝까지 쳤을 때만 서버에 묻고, 치는 중에는 받아 온 목록에서 거른다
const FULL_CODE = /^ST-\d{8}-[0-9A-F]{4}$/i
const CODE_PREFIX = /^ST-/i

type Props = {
  open: boolean
  onOpenChange: (open: boolean) => void
  onPick: (payout: payoutModel.PayoutDto) => void
}

// 보류를 걸 정산을 지급 예정일과 대회 이름으로 찾아 고르는 창
export function PayoutPickerDialog({ open, onOpenChange, onPick }: Props) {
  return (
    <Modal open={open} onOpenChange={onOpenChange} title="정산 고르기" widthClassName="w-240">
      <PickerBody onPick={onPick} />
    </Modal>
  )
}

function PickerBody({ onPick }: Pick<Props, 'onPick'>) {
  const [payDate, setPayDate] = useState<string>()
  const [page, setPage] = useState(0)
  const [text, setText] = useState('')
  const keyword = text.trim()
  const byCode = FULL_CODE.test(keyword)

  // 창을 닫으면 캐시를 바로 버린다(gcTime 0). 다시 열면 보류가 걸린 결과가 반영된 목록을 새로 받는다
  const list = payoutApi.usePayouts1(
    { payDate, page, size: PAGE_SIZE },
    {
      query: { select: unwrapData, placeholderData: keepPreviousData, gcTime: 0, enabled: !byCode },
    },
  )
  const found = payoutApi.useFindPayout(
    { code: keyword.toUpperCase() },
    { query: { select: unwrapData, gcTime: 0, retry: false, enabled: byCode } },
  )

  const source = byCode ? found : list
  const lines: Line[] = byCode
    ? found.data
      ? [{ payout: found.data, holds: [] }]
      : []
    : (list.data?.payouts.content ?? []).filter(({ payout }) =>
        CODE_PREFIX.test(keyword)
          ? payout.payoutCode.includes(keyword.toUpperCase())
          : payout.raceName.includes(keyword),
      )
  const notFound = byCode && found.error instanceof ApiError && found.error.status === 404
  const emptyText = !keyword
    ? '이 지급일에 정산이 없어요'
    : CODE_PREFIX.test(keyword)
      ? `「${keyword}」로 찾은 정산이 없어요`
      : `「${keyword}」 이름의 정산이 없어요`

  const pick = ({ payout, holds }: Line) => {
    if (!holdBlockLabel(payout, holds)) onPick(payout)
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-end gap-4">
        <DateTimeField
          mode="single"
          withTime={false}
          label="지급 예정일"
          placeholder="가까운 20일"
          className="w-60"
          onValueChange={(value) => {
            setPayDate(value || undefined)
            setPage(0)
          }}
        />
        <TextField
          label="대회 이름"
          placeholder="대회 이름 또는 ST-…"
          leading={<Icon name="Search" />}
          className="flex-1"
          value={text}
          onChange={(e) => setText(e.target.value)}
        />
      </div>

      {notFound || (!byCode && list.isError) || (byCode && found.isError && !notFound) ? (
        notFound ? (
          <ConsoleMessage kind="empty">{emptyText}</ConsoleMessage>
        ) : (
          <ConsoleMessage
            kind="error"
            onRetry={() => void source.refetch()}
            retrying={source.isFetching}
          >
            정산 목록을 불러오지 못했어요
          </ConsoleMessage>
        )
      ) : source.isPending ? (
        <Skeleton className="h-40 w-full" />
      ) : lines.length === 0 ? (
        <ConsoleMessage kind="empty">{emptyText}</ConsoleMessage>
      ) : (
        <>
          {!byCode && list.data && (
            <p className="text-body2-medium text-text-secondary">
              지급 예정일 {formatMonthDay(list.data.payDate)}
            </p>
          )}
          <DataTable
            label="정산"
            rows={lines}
            rowKey={(l) => String(l.payout.id)}
            onRowClick={pick}
            columns={[
              {
                key: 'race',
                header: '대회 이름',
                render: (l) => {
                  const blocked = holdBlockLabel(l.payout, l.holds)
                  return (
                    <button
                      type="button"
                      disabled={Boolean(blocked)}
                      className={cn(
                        'w-full truncate rounded-sm text-left text-body2-medium disabled:cursor-not-allowed disabled:opacity-50',
                        FOCUS_RING,
                      )}
                    >
                      {l.payout.raceName}
                    </button>
                  )
                },
              },
              {
                key: 'code',
                header: '정산 ID',
                width: 'w-48',
                render: (l) => <Dim line={l}>{l.payout.payoutCode}</Dim>,
              },
              {
                key: 'stage',
                header: '정산 단계',
                width: 'w-24',
                render: (l) => <Dim line={l}>{PAYOUT_STAGE[l.payout.payoutStage]}</Dim>,
              },
              {
                key: 'amount',
                header: '지급액',
                align: 'right',
                width: 'w-32',
                render: (l) => <Dim line={l}>{formatWon(l.payout.payoutAmount)}</Dim>,
              },
              {
                key: 'status',
                header: '상태',
                width: 'w-28',
                render: (l) => {
                  const blocked = holdBlockLabel(l.payout, l.holds)
                  return blocked === '보류 중' ? (
                    <span className="text-body2-medium text-text-warning">보류 중</span>
                  ) : (
                    <Dim line={l}>{payoutStatusLabel(l.payout.status).label}</Dim>
                  )
                },
              },
            ]}
          />
        </>
      )}
      {/* 이름 검색은 불러온 한 쪽 안에서만 거르므로, 검색 중에도 쪽을 넘겨 다른 쪽을 볼 수 있게 한다 */}
      {!byCode && list.data && list.data.payouts.totalPages > 1 && (
        <div className="flex flex-col items-center gap-2">
          {keyword && (
            <p className="text-body2-regular text-text-tertiary">
              지금 쪽의 {list.data.payouts.content.length}건 안에서 찾았어요
            </p>
          )}
          <Pagination
            page={page + 1}
            totalPages={list.data.payouts.totalPages}
            onPageChange={(next) => setPage(next - 1)}
          />
        </div>
      )}
    </div>
  )
}

// 보류를 걸 수 없는 줄은 글자를 흐리게 한다
function Dim({ line, children }: { line: Line; children: React.ReactNode }) {
  return (
    <span className={cn(holdBlockLabel(line.payout, line.holds) && 'opacity-50')}>{children}</span>
  )
}
