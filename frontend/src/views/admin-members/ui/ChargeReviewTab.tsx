'use client'

import { keepPreviousData } from '@tanstack/react-query'

import { ConsoleCard } from '@/widgets/console-page'
import { useAuthStatus } from '@/features/auth'
import { cashApi, unwrapData } from '@/shared/api'
import { formatWon, useSearchParamsState } from '@/shared/lib'
import { Alert, DataTable, Pagination } from '@/shared/ui'

import { ADMIN_MEMBER_PAGE_SIZE, loadErrorMessage, toDateTime } from '../model/admin-members-api'

// 고액 충전 확인: 가입 직후 큰 금액을 충전한 회원. 한 줄이 회원 한 명이다.
export function ChargeReviewTab() {
  const authenticated = useAuthStatus() === 'authenticated'
  const [url, setUrl] = useSearchParamsState({ chargePage: '0' })
  const page = Math.max(0, Math.trunc(Number(url.chargePage)) || 0)
  const list = cashApi.useGetChargeReviews(
    { page, size: ADMIN_MEMBER_PAGE_SIZE },
    { query: { select: unwrapData, enabled: authenticated, placeholderData: keepPreviousData } },
  )
  const rows = list.data?.content ?? []
  const totalPages = list.data?.totalPages ?? 0

  return (
    <>
      {list.isError && <Alert tone="danger" title={loadErrorMessage(list.error)} />}
      <ConsoleCard>
        <DataTable
          label="고액 충전 확인"
          rows={rows}
          rowKey={(r) => `${r.memberId}-${r.chargedAt}`}
          columns={[
            { key: 'nickname', header: '닉네임', render: (r) => r.nickname },
            { key: 'memberId', header: '회원 ID', align: 'right', render: (r) => r.memberId },
            {
              key: 'joinedAt',
              header: '가입 일시',
              align: 'right',
              render: (r) => toDateTime(r.joinedAt),
            },
            {
              key: 'chargeAmount',
              header: '충전 금액',
              align: 'right',
              render: (r) => formatWon(r.chargeAmount),
            },
            {
              key: 'chargedAt',
              header: '충전 완료 시각',
              align: 'right',
              render: (r) => toDateTime(r.chargedAt),
            },
            {
              key: 'joinWindowChargeTotal',
              header: '가입 24시간 충전 합계',
              align: 'right',
              render: (r) => formatWon(r.joinWindowChargeTotal),
            },
          ]}
        />
        {list.isSuccess && rows.length === 0 && (
          <p className="text-body2-regular text-text-tertiary">확인할 충전이 없어요</p>
        )}
      </ConsoleCard>
      {totalPages > 1 && (
        <Pagination
          page={page + 1}
          totalPages={totalPages}
          onPageChange={(p) => setUrl({ chargePage: String(p - 1) })}
        />
      )}
    </>
  )
}
