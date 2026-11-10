'use client'

import { useMutation, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { LoadErrorBody } from '@/widgets/status-screens'
import {
  NOTIFICATIONS_KEY,
  readAllNotices,
  useNotifications,
  type Notice,
} from '@/entities/notification'
import { cn } from '@/shared/lib'
import { Button, FOCUS_RING, Icon, Skeleton, Tab, TabBar, Tabs, useToast } from '@/shared/ui'

import {
  byOldest,
  categoryOf,
  formatShortDate,
  NOTICE_CATEGORIES,
  type NoticeCategory,
} from '../lib/notice'
import { useIsSeller } from '../model/use-is-seller'
import { NoNotice } from './NoNotice'
import { UnreadCount } from './UnreadCount'

type Filter = 'all' | NoticeCategory

// 피그마 시나리오 39 · 02 알림 목록 · 02-S 판매자 (+ 01-A 알림 없음)
// empty는 시안 미리보기(?preview=empty)용. 없으면 알림 API로 불러온다.
export function NotificationsPage({ empty = false }: { empty?: boolean }) {
  const [filter, setFilter] = useState<Filter>('all')
  const notifications = useNotifications()
  const all = empty ? [] : (notifications.data ?? [])
  const loading = !empty && notifications.isPending
  const failed = !empty && notifications.isError
  const { isSeller } = useIsSeller()
  const categories = NOTICE_CATEGORIES.filter((c) => c.value !== 'seller' || isSeller)

  // 판매 소식은 판매자에게 가장 먼저 볼 일이라 묶음 줄 맨 위에 둔다(탭은 맨 뒤)
  const groups = [...categories]
    .sort((a, b) => Number(b.value === 'seller') - Number(a.value === 'seller'))
    .map((category) => {
      const items = all.filter((n) => categoryOf(n) === category.value).sort(byOldest)
      return { ...category, latest: items.at(-1), unread: items.filter((n) => n.unread).length }
    })
    .filter((group): group is typeof group & { latest: Notice } => group.latest !== undefined)
  const totalUnread = groups.reduce((sum, group) => sum + group.unread, 0)
  const visible = groups.filter((group) => filter === 'all' || group.value === filter)

  return (
    <>
      <AppHeader
        type="sub"
        title="알림"
        action={<ReadAllButton disabled={!all.some((n) => n.unread)} />}
      />
      {loading ? (
        <main role="status" aria-label="알림 불러오는 중" className="flex flex-col">
          {categories.map((category) => (
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
        <main className="flex flex-1 flex-col">
          <NoNotice />
        </main>
      ) : (
        <main className="flex flex-1 flex-col">
          <Tabs value={filter} onValueChange={(value) => setFilter(value as Filter)}>
            <TabBar layout="hug">
              <Tab
                value="all"
                count={totalUnread > 0 ? <UnreadCount count={totalUnread} /> : undefined}
              >
                전체
              </Tab>
              {categories.map((category) => (
                <Tab key={category.value} value={category.value}>
                  {category.tab}
                </Tab>
              ))}
            </TabBar>
          </Tabs>
          {visible.length === 0 ? (
            <NoNotice />
          ) : (
            <ul>
              {visible.map((group) => (
                <li key={group.value}>
                  <Link
                    href={`/notifications/${group.value}`}
                    className={cn('flex items-center gap-3 px-4 py-3.5', FOCUS_RING)}
                  >
                    <span className="flex size-12 shrink-0 items-center justify-center rounded-full bg-bg-brand-subtle text-text-brand">
                      <Icon name={group.icon} />
                    </span>
                    <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                      <span className="text-body1-bold">{group.title}</span>
                      <span className="truncate text-body2-regular text-text-secondary">
                        {group.latest.body}
                      </span>
                    </span>
                    <span className="flex shrink-0 flex-col items-end gap-1.5">
                      <span className="text-caption-regular text-text-tertiary">
                        {formatShortDate(group.latest.sentAt)}
                      </span>
                      {group.unread > 0 && <UnreadCount count={group.unread} />}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </main>
      )}
    </>
  )
}

// 시나리오 39 · 머리말 [모두 읽음]. 목록에 안 보이는 알림(모르는 종류)도 같이 읽음으로 바뀐다
function ReadAllButton({ disabled }: { disabled: boolean }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const readAll = useMutation({
    mutationFn: readAllNotices,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_KEY }),
    onError: () => toast({ message: '모두 읽음으로 바꾸지 못했어요', tone: 'error' }),
  })
  return (
    <Button
      variant="text"
      size="s"
      className="mr-2"
      disabled={disabled}
      loading={readAll.isPending}
      onClick={() => readAll.mutate()}
    >
      모두 읽음
    </Button>
  )
}
