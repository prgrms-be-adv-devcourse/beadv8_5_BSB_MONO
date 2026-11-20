import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { Icon, type IconName } from '../icon/icons'

export type EmptyStateProps = {
  /** 이유를 나타내는 아이콘. 기본은 Inbox. null을 넘기면 아이콘 없이 글만 그린다 */
  icon?: IconName | null
  /** 아이콘 대신 보여 줄 그림(예: 마스코트). 있으면 icon은 무시한다 */
  illustration?: ReactNode
  title: ReactNode
  description?: ReactNode
  /** 다음 행동 버튼(Secondary M) */
  action?: ReactNode
  /** 불러오기 실패처럼 바로 알려야 하면 'alert'. 제목과 설명을 그 역할로 읽는다 */
  role?: 'alert' | 'status'
  className?: string
}

// v4 시안(2070:40061): 제목 20 굵게 · 설명 12 · (아이콘 48) · 다음 행동 버튼
export function EmptyState({
  icon = 'Inbox',
  illustration,
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
      {illustration ??
        // 시안 선 굵기 3.43(48 기준) = 24 기준 1.71
        (icon && <Icon name={icon} className="size-12 text-text-disabled" strokeWidth={1.71} />)}
      <div role={role} className="flex flex-col gap-1">
        <p className="text-title3 text-text-primary">{title}</p>
        {description && <p className="text-caption-regular text-text-secondary">{description}</p>}
      </div>
      {action}
    </div>
  )
}
