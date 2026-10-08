import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { Icon, type IconName } from '../icon/icons'

export type EmptyStateProps = {
  icon?: IconName
  title: ReactNode
  description?: ReactNode
  /** 다음 행동 버튼(Secondary M) */
  action?: ReactNode
  className?: string
}

// 보여 줄 내용이 없을 때 이유와 다음 행동을 알려 준다(빈 장바구니, 검색 결과 없음, 신청 내역 없음).
export function EmptyState({
  icon = 'Inbox',
  title,
  description,
  action,
  className,
}: EmptyStateProps) {
  return (
    <div
      className={cn('flex w-full flex-col items-center gap-4 px-6 py-10 text-center', className)}
    >
      <div className="flex size-16 items-center justify-center rounded-full bg-bg-subtle">
        <Icon name={icon} className="size-7 text-text-tertiary" />
      </div>
      <div className="flex flex-col gap-1.5">
        <p className="text-heading-s text-text-primary">{title}</p>
        {description && <p className="text-body-m text-text-secondary">{description}</p>}
      </div>
      {action}
    </div>
  )
}
