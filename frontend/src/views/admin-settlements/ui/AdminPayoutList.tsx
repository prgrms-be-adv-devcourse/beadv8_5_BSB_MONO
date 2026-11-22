'use client'

import { keepPreviousData } from '@tanstack/react-query'
import { format } from 'date-fns'
import { useState } from 'react'

import { ConsoleMessage } from '@/widgets/console-page'
import { useReleaseHold } from '@/features/payout-hold'
import { formatMonthDay, HOLD_REASON, PAYOUT_STAGE, payoutStatusLabel } from '@/entities/payout'
import { payoutApi, unwrapData, type payoutModel } from '@/shared/api'
import { DateTimeField } from '@/shared/date-picker'
import { formatWon } from '@/shared/lib'
import { Button, DataTable, Pagination, Skeleton } from '@/shared/ui'

const PAGE_SIZE = 50

type Props = {
  status: payoutModel.PayoutStatus
  /** 처음 보여 줄 지급 예정일. 없으면 백엔드 기본값(오늘보다 뒤인 가장 가까운 20일) */
  defaultPayDate?: Date
}

/** 지급 예정일 하루의 정산을 상태별로 보여 준다. 지급보류 탭은 줄마다 보류 풀기가 붙는다. */
export function AdminPayoutList({ status, defaultPayDate }: Props) {
  const [payDate, setPayDate] = useState<string | undefined>(
    defaultPayDate && format(defaultPayDate, 'yyyy-MM-dd'),
  )
  const [page, setPage] = useState(0)
  const release = useReleaseHold()
  const list = payoutApi.usePayouts1(
    { payDate, status, page, size: PAGE_SIZE },
    { query: { select: unwrapData, placeholderData: keepPreviousData } },
  )
  const lines = list.data?.payouts.content ?? []
  const holdable = status === '지급보류'

  return (
    <div className="flex flex-col gap-4">
      <DateTimeField
        mode="single"
        withTime={false}
        label="지급 예정일"
        placeholder="가까운 20일"
        className="w-60"
        defaultValue={defaultPayDate}
        onValueChange={(value) => {
          setPayDate(value || undefined)
          setPage(0)
        }}
      />

      {list.isError ? (
        <ConsoleMessage kind="error" onRetry={() => void list.refetch()} retrying={list.isFetching}>
          정산 목록을 불러오지 못했어요
        </ConsoleMessage>
      ) : list.isPending ? (
        <Skeleton className="h-40 w-full" />
      ) : (
        <>
          <p className="text-body2-medium text-text-secondary">
            지급 예정일 {formatMonthDay(list.data.payDate)} · {list.data.payouts.totalElements}건
          </p>
          {lines.length === 0 ? (
            <ConsoleMessage kind="empty">이 지급일에 해당하는 정산이 없어요</ConsoleMessage>
          ) : (
            <DataTable
              label={`${payoutStatusLabel(status).label} 정산`}
              rows={lines}
              rowKey={(l) => String(l.payout.id)}
              columns={[
                { key: 'race', header: '대회 이름', render: (l) => l.payout.raceName },
                {
                  key: 'code',
                  header: '정산 ID',
                  width: 'w-44',
                  render: (l) => l.payout.payoutCode,
                },
                {
                  key: 'stage',
                  header: '정산 단계',
                  width: 'w-24',
                  render: (l) => PAYOUT_STAGE[l.payout.payoutStage],
                },
                {
                  key: 'amount',
                  header: '지급액',
                  align: 'right',
                  width: 'w-32',
                  render: (l) => formatWon(l.payout.payoutAmount),
                },
                ...(holdable
                  ? [
                      {
                        key: 'hold',
                        header: '보류 사유',
                        width: 'w-36',
                        render: (l: (typeof lines)[number]) => {
                          const hold = l.holds.find((h) => h.status === '지급보류')
                          return hold ? HOLD_REASON[hold.reasonType] : '–'
                        },
                      },
                      {
                        key: 'release',
                        header: '',
                        width: 'w-24',
                        render: (l: (typeof lines)[number]) => {
                          const hold = l.holds.find((h) => h.status === '지급보류')
                          return hold ? (
                            <Button
                              variant="secondary"
                              size="s"
                              loading={release.isPending && release.variables?.holdId === hold.id}
                              onClick={() => release.mutate({ holdId: hold.id })}
                            >
                              보류 풀기
                            </Button>
                          ) : null
                        },
                      },
                    ]
                  : []),
              ]}
            />
          )}
          {list.data.payouts.totalPages > 1 && (
            <Pagination
              page={page + 1}
              totalPages={list.data.payouts.totalPages}
              onPageChange={(next) => setPage(next - 1)}
            />
          )}
        </>
      )}
    </div>
  )
}
