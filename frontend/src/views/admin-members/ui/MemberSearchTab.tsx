'use client'

import { ConsoleMessage } from '@/widgets/console-page'
import { useAuthStatus } from '@/features/auth'
import { memberApi, unwrapData } from '@/shared/api'
import { formatWon } from '@/shared/lib'
import { DataTable } from '@/shared/ui'

import {
  ADMIN_MEMBER_ERRORS,
  loadErrorMessage,
  roParticle,
  toShortDate,
} from '../model/admin-members-api'

const EMPTY = '–'

// 전체 회원: 닉네임 앞부분으로 찾은 회원(최대 20명). 찾기 전에는 요청하지 않는다.
// 상태 컬럼은 이용 정지 기능이 생길 때까지 비어 있다.
export function MemberSearchTab({ nickname: raw }: { nickname: string }) {
  // 주소창으로 들어온 ?q=도 검색창과 같이 앞뒤 공백을 뺀다
  const nickname = raw.trim()
  const authenticated = useAuthStatus() === 'authenticated'
  const list = memberApi.useSearchAdminMembers(
    { nickname },
    { query: { select: unwrapData, enabled: authenticated && nickname !== '' } },
  )
  const rows = list.data ?? []

  if (nickname === '') {
    return (
      <ConsoleMessage kind="empty" className="min-h-120">
        닉네임으로 찾아 주세요
      </ConsoleMessage>
    )
  }
  if (list.isError) {
    return (
      <ConsoleMessage kind="error" onRetry={() => void list.refetch()} retrying={list.isFetching}>
        {loadErrorMessage(list.error, ADMIN_MEMBER_ERRORS.memberLoadFailed)}
      </ConsoleMessage>
    )
  }
  // 불러오는 동안에는 빈 표를 그리지 않는다 (옆 탭과 같다)
  if (!list.isSuccess) {
    return null
  }
  if (rows.length === 0) {
    return (
      <ConsoleMessage kind="empty">
        「{nickname}」{roParticle(nickname)} 찾은 회원이 없어요
      </ConsoleMessage>
    )
  }
  return (
    <DataTable
      label="전체 회원"
      framed
      rows={rows}
      rowKey={(m) => String(m.id)}
      columns={[
        { key: 'nickname', header: '닉네임', render: (m) => m.nickname },
        { key: 'id', header: '회원 ID', align: 'right', width: 'w-34', render: (m) => m.id },
        {
          key: 'joinedAt',
          header: '가입일',
          align: 'right',
          width: 'w-34',
          render: (m) => toShortDate(m.joinedAt),
        },
        {
          key: 'deposit',
          header: '예치금',
          align: 'right',
          width: 'w-34',
          render: (m) => (m.deposit == null ? EMPTY : formatWon(m.deposit)),
        },
        {
          key: 'tickets',
          header: '참가권',
          align: 'right',
          width: 'w-24',
          render: (m) => (m.activeTickets == null ? EMPTY : `${m.activeTickets}장`),
        },
        { key: 'status', header: '상태', width: 'w-27', render: () => null },
      ]}
    />
  )
}
