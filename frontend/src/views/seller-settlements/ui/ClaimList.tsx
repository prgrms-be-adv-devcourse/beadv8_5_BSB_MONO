'use client'

import { keepPreviousData } from '@tanstack/react-query'

import {
  formatDayWithWeekday,
  RECOVERY_REASON,
  RECOVERY_STATUS,
  type DetailPeriod,
} from '@/entities/payout'
import { payoutApi, payoutModel, unwrapData } from '@/shared/api'
import { copyText, formatNumber } from '@/shared/lib'
import { DataTable, useToast } from '@/shared/ui'

import { QueryRows, StatusText } from './parts'

// 시안 2070:69648 회수 · 2070:70255 입금 계좌 누르면 복사
export function ClaimList({ period }: { period: DetailPeriod }) {
  const claims = payoutApi.useClaims(
    { from: period.from, to: period.to },
    { query: { select: unwrapData, placeholderData: keepPreviousData } },
  )

  return (
    <QueryRows query={claims} label="회수 내역" emptyTitle="이 기간에 회수 내역이 없어요">
      {(rows) => (
        <DataTable
          label="회수 내역"
          rows={rows}
          rowKey={(r) => String(r.id)}
          columns={[
            { key: 'claimCode', header: '청구 ID', width: 'w-50', render: (r) => r.claimCode },
            {
              key: 'race',
              header: '대회',
              width: 'w-30.75',
              render: (r) => r.raceName ?? '-',
            },
            {
              key: 'reason',
              header: '회수 사유',
              width: 'w-30.75',
              render: (r) => RECOVERY_REASON[r.reasonType],
            },
            {
              key: 'amount',
              header: '청구 금액',
              align: 'right',
              width: 'w-35',
              render: (r) => formatNumber(r.amount),
            },
            {
              key: 'dueDate',
              header: '입금 기한',
              width: 'w-32',
              render: (r) => formatDayWithWeekday(r.dueDate),
            },
            { key: 'account', header: '입금 계좌', render: (r) => <ClaimAccount claim={r} /> },
            {
              key: 'status',
              header: '상태',
              width: 'w-30',
              render: (r) => <StatusText {...RECOVERY_STATUS[r.status]} />,
            },
          ]}
        />
      )}
    </QueryRows>
  )
}

/** 청구 전용 가상계좌. 누르면 계좌번호를 복사한다. 아직 발급 전이면 「-」 */
function ClaimAccount({ claim }: { claim: payoutModel.SellerRecoveryDto }) {
  const showToast = useToast()
  const { virtualBankName: bank, virtualAccountNumber: number } = claim
  if (!bank || !number) return '-'

  async function copy() {
    const copied = await copyText(number ?? '')
    showToast(
      copied
        ? { message: '계좌번호를 복사했어요' }
        : { message: '계좌번호를 복사하지 못했어요', tone: 'error' },
    )
  }

  return (
    <button
      type="button"
      onClick={copy}
      className="flex flex-col items-start gap-1 text-left"
      aria-label={`${bank} ${number} 계좌번호 복사`}
    >
      <span className="text-body1-regular">{bank}</span>
      <span className="text-caption-regular text-text-secondary">{number}</span>
    </button>
  )
}
