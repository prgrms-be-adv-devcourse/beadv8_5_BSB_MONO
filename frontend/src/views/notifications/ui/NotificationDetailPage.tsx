'use client'

import Link from 'next/link'
import { Fragment } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { LoadErrorBody } from '@/widgets/status-screens'
import { buttonClassName, Icon, Skeleton } from '@/shared/ui'

import { useNotifications, type NoticeCategory } from '../api/use-notifications'
import {
  byOldest,
  formatDayChip,
  formatTime,
  NOTICE_CATEGORIES,
  safeInternalHref,
} from '../lib/notice'
import { NoNotice } from './NoNotice'

// 피그마 시나리오 37 · 03 알림 상세: 한 종류의 알림을 날짜 칩 아래 대화처럼 오래된 것부터 보여 준다.
// 없는 종류는 라우트(page.tsx)에서 404로 보낸다.
export function NotificationDetailPage({ category }: { category: NoticeCategory }) {
  const meta = NOTICE_CATEGORIES.find((c) => c.value === category) ?? NOTICE_CATEGORIES[0]
  const notifications = useNotifications()

  const items = (notifications.data ?? []).filter((n) => n.category === meta.value).sort(byOldest)

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
            const href = safeInternalHref(notice.link?.href)
            return (
              <Fragment key={notice.id}>
                {(i === 0 || formatDayChip(items[i - 1].sentAt) !== day) && (
                  <p className="self-center rounded-full bg-bg-subtle px-2.5 py-1 text-caption text-text-secondary">
                    {day}
                  </p>
                )}
                <div className="flex items-start gap-2">
                  <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-bg-brand-subtle text-text-brand">
                    <Icon name={meta.icon} className="size-4.5" />
                  </span>
                  <div className="flex min-w-0 flex-col items-start gap-1">
                    <div className="flex max-w-80 flex-col gap-2 rounded-md rounded-tl-[2px] bg-bg-surface p-3 inset-ring inset-ring-border-default">
                      <p className="text-body-m whitespace-pre-line">{notice.body}</p>
                      {href && notice.link && (
                        <div className="flex justify-center rounded-sm bg-bg-brand-subtle py-2">
                          <Link
                            href={href}
                            className={buttonClassName({ variant: 'ghost', size: 's' })}
                          >
                            {notice.link.label}
                          </Link>
                        </div>
                      )}
                    </div>
                    <span className="text-caption text-text-tertiary">
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
