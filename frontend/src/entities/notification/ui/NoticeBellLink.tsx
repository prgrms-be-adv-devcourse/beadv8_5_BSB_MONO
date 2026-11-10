'use client'

import Link from 'next/link'

import { cn } from '@/shared/lib'
import { Icon } from '@/shared/ui'

import { useHasUnreadNotice } from '../api/use-notifications'

// 머리말 종 아이콘. 안 읽은 알림이 있으면 아이콘 오른쪽 위에 빨간 점(8)을 달고 이름에도 알린다
export function NoticeBellLink({ className }: { className?: string }) {
  const unread = useHasUnreadNotice()
  return (
    <Link
      href="/notifications"
      aria-label={unread ? '알림, 읽지 않은 알림 있음' : '알림'}
      className={cn('relative', className)}
    >
      <Icon name="Bell" />
      {unread && (
        <span
          data-unread-dot
          className="absolute top-3 right-3 size-2 rounded-full bg-text-danger"
        />
      )}
    </Link>
  )
}
