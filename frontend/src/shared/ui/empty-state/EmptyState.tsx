import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { Logo } from '../logo/Logo'

export type EmptyStateProps = {
  title: ReactNode
  description?: ReactNode
  /** 다음 행동 버튼(Secondary M) */
  action?: ReactNode
  /** 불러오기 실패처럼 바로 알려야 하면 'alert'. 제목과 설명을 그 역할로 읽는다 */
  role?: 'alert' | 'status'
  className?: string
}

// 보여 줄 내용이 없거나 불러오지 못했을 때 회색 로고와 회색 글자로 조용히 알리고 다음 행동을 둔다.
export function EmptyState({ title, description, action, role, className }: EmptyStateProps) {
  return (
    <div
      className={cn('flex w-full flex-col items-center gap-5 px-6 py-16 text-center', className)}
    >
      <Logo variant="symbolMuted" decorative className="size-26" />
      <div role={role} className="flex flex-col gap-1.5">
        <p className="text-label-l text-text-tertiary">{title}</p>
        {description && <p className="text-body-s text-text-tertiary">{description}</p>}
      </div>
      {action}
    </div>
  )
}
