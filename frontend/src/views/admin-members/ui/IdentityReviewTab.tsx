'use client'

import { keepPreviousData } from '@tanstack/react-query'

import { ConsoleCard } from '@/widgets/console-page'
import { useAuthStatus } from '@/features/auth'
import { memberApi, unwrapData } from '@/shared/api'
import { cn, useSearchParamsState } from '@/shared/lib'
import { Alert, DataTable, Pagination } from '@/shared/ui'

import { ADMIN_MEMBER_PAGE_SIZE, loadErrorMessage, toDateTime } from '../model/admin-members-api'

// 상태는 칩 대신 뜻 색 글자로 보여 준다 (완료 초록, 실패 빨강, 나머지 보조 글자)
const STATUS_TEXT: Record<string, string> = {
  success: 'text-text-success',
  danger: 'text-text-danger',
  warning: 'text-text-warning',
}
const statusText = (tone: string) =>
  cn('text-body2-medium', STATUS_TEXT[tone] ?? 'text-text-secondary')

// 확인이 필요한 계정: 같은 사람으로 보이는 계정 묶음. 묶음마다 카드 하나, 표 하나.
export function IdentityReviewTab() {
  const authenticated = useAuthStatus() === 'authenticated'
  const [url, setUrl] = useSearchParamsState({ identityPage: '0' })
  const page = Math.max(0, Math.trunc(Number(url.identityPage)) || 0)
  const list = memberApi.useFindIdentityReview(
    { page, size: ADMIN_MEMBER_PAGE_SIZE },
    { query: { select: unwrapData, enabled: authenticated, placeholderData: keepPreviousData } },
  )
  const groups = list.data?.groups ?? []
  const totalPages = list.data?.totalPages ?? 0

  return (
    <>
      {list.isError && <Alert tone="danger" title={loadErrorMessage(list.error)} />}
      {list.isSuccess && groups.length === 0 && (
        <ConsoleCard>
          <p className="text-body2-regular text-text-tertiary">확인이 필요한 계정이 없어요</p>
        </ConsoleCard>
      )}
      {groups.map((group, i) => (
        <ConsoleCard key={group.members.map((m) => m.id).join('-') || i}>
          <DataTable
            label="확인이 필요한 계정"
            rows={group.members}
            rowKey={(m) => String(m.id)}
            columns={[
              { key: 'nickname', header: '닉네임', render: (m) => m.nickname },
              { key: 'name', header: '이름', render: (m) => m.maskedName },
              { key: 'phone', header: '휴대폰', render: (m) => m.maskedPhone },
              { key: 'email', header: '이메일', render: (m) => m.maskedEmail },
              {
                key: 'ci',
                header: '인증',
                render: (m) =>
                  m.ciVerified ? (
                    <span className={statusText('success')}>CI 있음</span>
                  ) : (
                    <span className={statusText('warning')}>CI 없음</span>
                  ),
              },
              {
                key: 'joinedAt',
                header: '가입 일시',
                align: 'right',
                render: (m) => toDateTime(m.joinedAt),
              },
            ]}
          />
        </ConsoleCard>
      ))}
      {totalPages > 1 && (
        <Pagination
          page={page + 1}
          totalPages={totalPages}
          onPageChange={(p) => setUrl({ identityPage: String(p - 1) })}
        />
      )}
    </>
  )
}
