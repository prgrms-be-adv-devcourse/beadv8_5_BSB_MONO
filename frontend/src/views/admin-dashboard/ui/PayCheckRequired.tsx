'use client'

import { ConsoleCard } from '@/widgets/console-page'
import { formatMonthDay, PAYOUT_STAGE } from '@/entities/payout'
import { ApiError } from '@/shared/api'
import { formatNumber } from '@/shared/lib'
import { Alert, DataTable, EmptyState, Skeleton, StatTile } from '@/shared/ui'

import { PAY_CHECK_PAGE_SIZE, usePayCheckRequired } from '../model/use-pay-check-required'

export function PayCheckRequiredTile() {
  const failures = usePayCheckRequired()
  if (!failures.data) {
    return <Skeleton className="h-24 flex-1" />
  }
  return (
    <StatTile
      label="지급 확인 필요 (최근 1개월)"
      value={formatNumber(failures.data.records.totalElements)}
      unit="건"
    />
  )
}

export function PayCheckRequiredCard() {
  const failures = usePayCheckRequired()
  const records = failures.data?.records
  return (
    <ConsoleCard title="지급 확인 필요 · 최근 1개월" className="min-w-130 flex-1">
      {failures.isError ? (
        <Alert
          tone="danger"
          title="지급 확인 필요 기록을 불러오지 못했어요"
          description={
            (failures.error instanceof ApiError && failures.error.serverMessage) ||
            '잠시 뒤 다시 시도해 주세요.'
          }
        />
      ) : !records ? (
        <Skeleton className="h-40 w-full" />
      ) : records.content.length === 0 ? (
        <EmptyState title="지급 결과를 확인해야 하는 정산이 없어요" />
      ) : (
        <>
          <DataTable
            label="지급 확인 필요"
            rows={records.content}
            rowKey={(r) => String(r.id)}
            columns={[
              {
                key: 'recordedAt',
                header: '기록일',
                align: 'right',
                width: 'w-22.5',
                render: (r) => formatMonthDay(r.recordedAt),
              },
              {
                key: 'seller',
                header: '판매자 ID',
                width: 'w-22.5',
                render: (r) => r.sellerId ?? '-',
              },
              {
                key: 'stage',
                header: '단계',
                width: 'w-16',
                render: (r) => PAYOUT_STAGE[r.payoutStage],
              },
              { key: 'note', header: '비고', render: (r) => r.note ?? '' },
            ]}
          />
          <p className="text-body2-regular text-text-tertiary">
            지급 결과를 3번 몰라 지급을 멈춘 정산이에요. 캐시에서 실제로 지급됐는지 확인해 주세요.
            {records.totalElements > PAY_CHECK_PAGE_SIZE &&
              ` 최근 ${PAY_CHECK_PAGE_SIZE}건만 보여요.`}
          </p>
        </>
      )}
    </ConsoleCard>
  )
}
