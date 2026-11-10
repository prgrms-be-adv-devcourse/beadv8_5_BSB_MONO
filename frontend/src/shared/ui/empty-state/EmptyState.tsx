import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { Icon, type IconName } from '../icon/icons'

export type EmptyStateProps = {
  /** 이유를 나타내는 아이콘. 기본은 Inbox */
  icon?: IconName
  title: ReactNode
  description?: ReactNode
  /** 다음 행동 버튼(Secondary M) */
  action?: ReactNode
  /** 불러오기 실패처럼 바로 알려야 하면 'alert'. 제목과 설명을 그 역할로 읽는다 */
  role?: 'alert' | 'status'
  className?: string
}

// 피그마 v2/EmptyState: 회색 아이콘 48 · 제목 · 설명 · 다음 행동 버튼
export function EmptyState({
  icon = 'Inbox',
  title,
  description,
  action,
  role,
  className,
}: EmptyStateProps) {
  return (
    <div
      className={cn('flex w-full flex-col items-center gap-4 px-6 py-10 text-center', className)}
    >
      {/* 시안 선 굵기 3.43(48 기준) = 24 기준 1.71 */}
      <Icon name={icon} className="size-12 text-text-tertiary" strokeWidth={1.71} />
      <div role={role} className="flex flex-col">
        <p className="text-body1-medium text-text-primary">{title}</p>
        {description && <p className="text-body2-regular text-text-secondary">{description}</p>}
      </div>
      {action}
    </div>
  )
}
