'use client'

import { ConsolePage } from '@/widgets/console-page'
import { useAuthStatus } from '@/features/auth'
import { cashApi, memberApi, unwrapData } from '@/shared/api'
import { cn, useSearchParamsState } from '@/shared/lib'
import { Chip, DataTable, Icon, TextField } from '@/shared/ui'

import { ADMIN_MEMBER_PAGE_SIZE } from '../model/admin-members-api'
import { ChargeReviewTab } from './ChargeReviewTab'
import { IdentityReviewTab } from './IdentityReviewTab'

// 상태는 칩 대신 뜻 색 글자로 보여 준다 (완료 초록, 실패 빨강, 나머지 보조 글자)
const STATUS_TEXT: Record<string, string> = {
  success: 'text-text-success',
  danger: 'text-text-danger',
  warning: 'text-text-warning',
}
const statusText = (tone: string) =>
  cn('text-body2-medium', STATUS_TEXT[tone] ?? 'text-text-secondary')

type Tab = 'all' | 'identity' | 'charge'

type Member = {
  id: string
  nickname: string
  joinedAt: string
  deposit: string
  tickets: string
  suspended: boolean
}

// TODO(api): 전체 회원 목록 API로 바꾼다. 시안 예시 값.
const MEMBERS: Member[] = [
  {
    id: '2041',
    nickname: '달리는곰',
    joinedAt: '2025. 3. 2',
    deposit: '₩45,000',
    tickets: '2장',
    suspended: false,
  },
  {
    id: '3318',
    nickname: '달리는곰2',
    joinedAt: '2026. 9. 14',
    deposit: '₩0',
    tickets: '0장',
    suspended: false,
  },
  {
    id: '4120',
    nickname: '달리는곰곰',
    joinedAt: '2026. 10. 30',
    deposit: '₩0',
    tickets: '0장',
    suspended: true,
  },
]

// 피그마 V2 · A04 회원 · 주문 조회
export function AdminMembersPage() {
  const authenticated = useAuthStatus() === 'authenticated'
  // 탭은 URL(?tab=)에 담는다
  const [url, setUrl] = useSearchParamsState({ tab: 'all' }, { tab: ['all', 'identity', 'charge'] })
  const tab = url.tab as Tab
  // 탭 배지. 탭을 누르기 전에도 개수가 보이도록 첫 페이지를 항상 불러온다(각 탭의 첫 페이지와 같은 요청이라 한 번만 간다).
  const identity = memberApi.useFindIdentityReview(
    { page: 0, size: ADMIN_MEMBER_PAGE_SIZE },
    { query: { select: unwrapData, enabled: authenticated } },
  )
  const charge = cashApi.useGetChargeReviews(
    { page: 0, size: ADMIN_MEMBER_PAGE_SIZE },
    { query: { select: unwrapData, enabled: authenticated } },
  )

  const tabs: { value: Tab; label: string; count?: number }[] = [
    { value: 'all', label: '전체 회원' },
    { value: 'identity', label: '확인이 필요한 계정', count: identity.data?.totalElements },
    { value: 'charge', label: '고액 충전 확인', count: charge.data?.totalElements },
  ]

  return (
    <ConsolePage title="회원 관리">
      <div className="flex items-center gap-2">
        <div role="group" aria-label="회원 보기" className="flex items-center gap-2">
          {tabs.map((t) => (
            <Chip key={t.value} selected={tab === t.value} onClick={() => setUrl({ tab: t.value })}>
              {t.label}
              {t.count !== undefined && <span className="tabular-nums">{t.count}</span>}
            </Chip>
          ))}
        </div>
        <div className="flex-1" />
        {tab === 'all' && (
          // TODO(api): 닉네임·주문번호 검색
          <TextField
            compact
            leading={<Icon name="Search" />}
            aria-label="닉네임·주문번호로 찾기"
            placeholder="닉네임·주문번호로 찾기"
            className="w-80"
          />
        )}
      </div>
      {tab === 'identity' && <IdentityReviewTab />}
      {tab === 'charge' && <ChargeReviewTab />}
      {tab === 'all' && (
        <DataTable
          label="전체 회원"
          framed
          rows={MEMBERS}
          rowKey={(m) => m.id}
          columns={[
            { key: 'nickname', header: '닉네임', render: (m) => m.nickname },
            { key: 'id', header: '회원 ID', align: 'right', width: 'w-34', render: (m) => m.id },
            {
              key: 'joinedAt',
              header: '가입일',
              align: 'right',
              width: 'w-34',
              render: (m) => m.joinedAt,
            },
            {
              key: 'deposit',
              header: '예치금',
              align: 'right',
              width: 'w-34',
              render: (m) => m.deposit,
            },
            {
              key: 'tickets',
              header: '참가권',
              align: 'right',
              width: 'w-22',
              render: (m) => m.tickets,
            },
            {
              key: 'status',
              header: '상태',
              width: 'w-30',
              render: (m) => m.suspended && <span className={statusText('danger')}>이용 정지</span>,
            },
          ]}
        />
      )}
    </ConsolePage>
  )
}
