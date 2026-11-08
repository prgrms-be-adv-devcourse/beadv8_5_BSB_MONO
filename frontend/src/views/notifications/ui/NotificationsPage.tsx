'use client'

import Link from 'next/link'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { LoadErrorBody } from '@/widgets/status-screens'
import { cn } from '@/shared/lib'
import { EmptyState, FOCUS_RING, Icon, Skeleton, Tab, TabBar, Tabs } from '@/shared/ui'

import { useNotifications, type Notice, type NoticeCategory } from '../api/use-notifications'
import { byOldest, formatShortDate, NOTICE_CATEGORIES } from '../lib/notice'
import { UnreadCount } from './UnreadCount'

type Filter = 'all' | NoticeCategory

// 피그마 시나리오 37 · 02 알림 목록 (+ 01-A 알림 없음)
// empty는 시안 미리보기(?preview=empty)용. 없으면 알림 API로 불러온다.
export function NotificationsPage({ empty = false }: { empty?: boolean }) {
  const [filter, setFilter] = useState<Filter>('all')
  const notifications = useNotifications()
  const all = empty ? [] : (notifications.data ?? [])
  const loading = !empty && notifications.isPending
  const failed = !empty && notifications.isError

  const groups = NOTICE_CATEGORIES.map((category) => {
    const items = all.filter((n) => n.category === category.value).sort(byOldest)
    return { ...category, latest: items.at(-1), unread: items.filter((n) => n.unread).length }
  }).filter((group): group is typeof group & { latest: Notice } => group.latest !== undefined)
  const totalUnread = groups.reduce((sum, group) => sum + group.unread, 0)

  return (
    <>
      <AppHeader type="sub" title="알림" />
      {loading ? (
        <main role="status" aria-label="알림 불러오는 중" className="flex flex-col">
          {NOTICE_CATEGORIES.map((category) => (
            <div key={category.value} className="flex items-center gap-3 px-4 py-3.5">
              <Skeleton shape="circle" className="size-12" />
              <Skeleton className="w-32" />
            </div>
          ))}
        </main>
      ) : failed ? (
        <LoadErrorBody
          onRetry={() => void notifications.refetch()}
          retrying={notifications.isFetching}
        />
      ) : groups.length === 0 ? (
        <main className="flex flex-1 items-center justify-center px-4 py-8">
          <EmptyState
            icon="Bell"
            title="아직 알림이 없어요"
            description="대회 일정이 바뀌거나 환불이 끝나면 여기로 알려 드려요."
            className="max-w-86"
          />
        </main>
      ) : (
        <main className="flex flex-1 flex-col">
          <Tabs value={filter} onValueChange={(value) => setFilter(value as Filter)}>
            <TabBar variant="underline">
              <Tab
                value="all"
                count={totalUnread > 0 ? <UnreadCount count={totalUnread} /> : undefined}
              >
                전체
              </Tab>
              {NOTICE_CATEGORIES.map((category) => (
                <Tab key={category.value} value={category.value}>
                  {category.tab}
                </Tab>
              ))}
            </TabBar>
          </Tabs>
          <ul>
            {groups
              .filter((group) => filter === 'all' || group.value === filter)
              .map((group) => (
                <li key={group.value}>
                  <Link
                    href={`/notifications/${group.value}`}
                    className={cn('flex items-center gap-3 px-4 py-3.5', FOCUS_RING)}
                  >
                    <span className="flex size-12 shrink-0 items-center justify-center rounded-full bg-bg-brand-subtle text-text-brand">
                      <Icon name={group.icon} />
                    </span>
                    <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                      <span className="text-heading-s">{group.title}</span>
                      <span className="truncate text-body-m text-text-secondary">
                        {group.latest.body}
                      </span>
                    </span>
                    <span className="flex shrink-0 flex-col items-end gap-1.5">
                      <span className="text-caption text-text-tertiary">
                        {formatShortDate(group.latest.sentAt)}
                      </span>
                      {group.unread > 0 && <UnreadCount count={group.unread} />}
                    </span>
                  </Link>
                </li>
              ))}
          </ul>
        </main>
      )}
    </>
  )
}
