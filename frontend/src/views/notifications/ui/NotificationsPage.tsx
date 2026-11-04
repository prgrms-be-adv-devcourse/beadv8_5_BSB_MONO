'use client'

import Link from 'next/link'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { BottomNav } from '@/widgets/bottom-nav'
import { cn } from '@/shared/lib'
import { EmptyState, FOCUS_RING, Icon, Skeleton } from '@/shared/ui'

import { useNotifications, type Notice } from '../api/use-notifications'

// 피그마 V2 · M25 알림 (+ 없음)
// empty는 시안 미리보기(?preview=empty)용. 없으면 알림 API로 불러온다. 불러오지 못하면 알림이 없는 것으로 본다.
export function NotificationsPage({ empty = false }: { empty?: boolean }) {
  const [read, setRead] = useState(false)
  const notifications = useNotifications()
  const loading = !empty && notifications.isPending
  const all = empty ? [] : (notifications.data ?? [])
  const today = all
    .filter((n) => n.group === 'today')
    .map((n) => ({ ...n, unread: n.unread && !read }))
  const earlier = all.filter((n) => n.group === 'earlier')
  const unreadCount = today.filter((n) => n.unread).length
  const none = !loading && all.length === 0

  return (
    <>
      <AppHeader />
      <main className="flex flex-1 flex-col gap-4 px-4 pt-(--layout-content-start) pb-8">
        <h1 className="text-heading-l">알림</h1>
        {loading ? (
          <div role="status" aria-label="알림 불러오는 중" className="flex flex-col gap-3">
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-16 w-full" />
          </div>
        ) : none ? (
          <div className="flex flex-1 items-center justify-center">
            <EmptyState
              title="아직 알림이 없어요"
              description="대회 일정이 바뀌거나 환불이 끝나면 여기로 알려 드려요."
              className="max-w-80"
            />
          </div>
        ) : (
          <>
            <div className="flex items-center justify-between py-1">
              <p className="text-body-s text-text-secondary">읽지 않은 알림 {unreadCount}개</p>
              <button
                type="button"
                onClick={() => setRead(true)}
                className={cn('rounded-sm text-label-m text-text-brand', FOCUS_RING)}
              >
                모두 읽음
              </button>
            </div>
            {today.length > 0 && <NoticeGroup title="오늘" items={today} />}
            {earlier.length > 0 && <NoticeGroup title="이전" items={earlier} />}
          </>
        )}
      </main>
      <BottomNav />
    </>
  )
}

function NoticeGroup({ title, items }: { title: string; items: Notice[] }) {
  return (
    <section className="flex flex-col gap-2">
      <h2 className="pt-2 text-label-strong">{title}</h2>
      <ul className="overflow-hidden rounded-lg bg-bg-surface inset-ring inset-ring-border-default">
        {items.map((n) => (
          <li key={n.id} className="border-b border-border-default last:border-b-0">
            <Link
              href={n.href}
              className={cn('flex gap-3 px-4 py-4', n.unread && 'bg-bg-brand-subtle', FOCUS_RING)}
            >
              <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-bg-surface shadow-sm">
                <Icon name={n.icon} className="size-4" />
              </span>
              <div className="flex min-w-0 flex-1 flex-col gap-1">
                <div className="flex items-start justify-between gap-2">
                  <p className="text-label-m">{n.title}</p>
                  <span className="flex items-center gap-2 text-caption text-text-secondary">
                    {n.at}
                    {n.unread && (
                      <span aria-label="읽지 않음" className="size-1.5 rounded-full bg-bg-brand" />
                    )}
                  </span>
                </div>
                <p className="text-body-s whitespace-pre-line text-text-secondary">{n.body}</p>
              </div>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
