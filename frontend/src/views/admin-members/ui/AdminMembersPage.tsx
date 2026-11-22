'use client'

import { useState } from 'react'

import { ConsolePage } from '@/widgets/console-page'
import { useAuthStatus } from '@/features/auth'
import { cashApi, memberApi, unwrapData } from '@/shared/api'
import { useSearchParamsState } from '@/shared/lib'
import { Chip, Icon, TextField } from '@/shared/ui'

import { ADMIN_MEMBER_PAGE_SIZE } from '../model/admin-members-api'
import { ChargeReviewTab } from './ChargeReviewTab'
import { IdentityReviewTab } from './IdentityReviewTab'
import { MemberSearchTab } from './MemberSearchTab'

type Tab = 'all' | 'identity' | 'charge'

// 피그마 v4 · 관리자 · 02 회원 관리
export function AdminMembersPage() {
  const authenticated = useAuthStatus() === 'authenticated'
  // 탭과 찾은 닉네임은 URL(?tab=, ?q=)에 담는다
  const [url, setUrl] = useSearchParamsState(
    { tab: 'all', q: '' },
    { tab: ['all', 'identity', 'charge'] },
  )
  const tab = url.tab as Tab
  const [draft, setDraft] = useState(url.q)
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
          // 막을 규칙 없이 앞뒤 공백만 빼는 검색창이라 trim만 한다
          <form
            role="search"
            onSubmit={(e) => {
              e.preventDefault()
              setUrl({ q: draft.trim() })
            }}
          >
            <TextField
              compact
              leading={<Icon name="Search" />}
              aria-label="닉네임으로 찾기"
              placeholder="닉네임으로 찾기"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              className="w-80"
            />
          </form>
        )}
      </div>
      {tab === 'identity' && <IdentityReviewTab />}
      {tab === 'charge' && <ChargeReviewTab />}
      {tab === 'all' && <MemberSearchTab nickname={url.q} />}
    </ConsolePage>
  )
}
