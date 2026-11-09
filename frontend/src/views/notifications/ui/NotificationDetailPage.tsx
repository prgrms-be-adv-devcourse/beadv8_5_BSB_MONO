'use client'

import { useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { Fragment, useEffect, useRef } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { LoadErrorBody } from '@/widgets/status-screens'
import { NOTIFICATIONS_KEY, readNotice, useNotifications } from '@/entities/notification'
import { buttonClassName, Icon, Skeleton } from '@/shared/ui'

import {
  byOldest,
  categoryOf,
  formatDayChip,
  formatTime,
  NOTICE_CATEGORIES,
  safeInternalHref,
  type NoticeCategory,
} from '../lib/notice'
import { NoNotice } from './NoNotice'

// 피그마 시나리오 37 · 03 알림 상세: 한 종류의 알림을 날짜 칩 아래 대화처럼 오래된 것부터 보여 준다.
// 없는 종류는 라우트(page.tsx)에서 404로 보낸다.
export function NotificationDetailPage({ category }: { category: NoticeCategory }) {
  const meta = NOTICE_CATEGORIES.find((c) => c.value === category) ?? NOTICE_CATEGORIES[0]
  const notifications = useNotifications()

  const items = (notifications.data ?? [])
    .filter((n) => categoryOf(n) === meta.value)
    .sort(byOldest)
  useReadOnOpen(items.filter((n) => n.unread).map((n) => n.id))

  return (
    <>
      <AppHeader type="sub" title={meta.title} backHref="/notifications" />
      {notifications.isPending ? (
        <main
          role="status"
          aria-label="알림 불러오는 중"
          className="flex flex-col gap-3.5 px-4 pt-4"
        >
          <Skeleton className="mx-auto h-6 w-24 rounded-full" />
          <div className="flex gap-2">
            <Skeleton shape="circle" className="size-9" />
            <Skeleton shape="rect" className="h-16 w-48" />
          </div>
        </main>
      ) : notifications.isError ? (
        <LoadErrorBody
          onRetry={() => void notifications.refetch()}
          retrying={notifications.isFetching}
        />
      ) : items.length === 0 ? (
        <main className="flex flex-1 flex-col">
          <NoNotice />
        </main>
      ) : (
        <main className="flex flex-1 flex-col gap-3.5 px-4 pt-4 pb-8">
          {items.map((notice, i) => {
            const day = formatDayChip(notice.sentAt)
            const href = safeInternalHref(notice.href)
            return (
              <Fragment key={notice.id}>
                {(i === 0 || formatDayChip(items[i - 1].sentAt) !== day) && (
                  <p className="self-center rounded-full bg-bg-subtle px-2.5 py-1 text-caption-regular text-text-secondary">
                    {day}
                  </p>
                )}
                <div className="flex items-start gap-2">
                  <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-bg-brand-subtle text-text-brand">
                    <Icon name={meta.icon} className="size-4.5" />
                  </span>
                  <div className="flex min-w-0 flex-col items-start gap-1">
                    <div className="flex max-w-80 flex-col gap-2 rounded-md rounded-tl-[2px] bg-bg-surface p-3 inset-ring inset-ring-border-default">
                      <p className="text-body2-regular whitespace-pre-line">{notice.body}</p>
                      {href && (
                        <div className="flex justify-center rounded-sm bg-bg-brand-subtle py-2">
                          <Link
                            href={href}
                            className={buttonClassName({ variant: 'text', size: 's' })}
                          >
                            {meta.linkLabel}
                          </Link>
                        </div>
                      )}
                    </div>
                    <span className="text-caption-regular text-text-tertiary">
                      {formatTime(notice.sentAt)}
                    </span>
                  </div>
                </div>
              </Fragment>
            )
          })}
        </main>
      )}
    </>
  )
}

/**
 * 상세 화면에 보인 안 읽은 알림을 하나씩 읽음으로 바꾸고, 끝나면 알림을 다시 불러온다(머리말 점도 같이 바뀐다).
 * 같은 알림은 한 번만 부른다. 다시 불러온 응답이 아직 안 읽음이어도(폴링과 겹침 · 실패) 또 부르지 않는다.
 * 실패는 화면에 알리지 않는다. 다음에 상세 화면을 열 때 다시 시도한다.
 */
function useReadOnOpen(unreadIds: string[]) {
  const queryClient = useQueryClient()
  const requested = useRef(new Set<string>())
  const key = unreadIds.join(',')
  useEffect(() => {
    const ids = key.split(',').filter((id) => id !== '' && !requested.current.has(id))
    if (ids.length === 0) return
    ids.forEach((id) => requested.current.add(id))
    void Promise.allSettled(ids.map(readNotice)).then(() =>
      queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_KEY }),
    )
  }, [key, queryClient])
}
