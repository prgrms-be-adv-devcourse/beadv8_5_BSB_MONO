import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { FOCUS_RING } from '../focus-ring'
import { Icon, type IconName } from '../icon/icons'

export type AlertTone = 'info' | 'success' | 'warning' | 'danger'

// 글자색은 하나로 두고 톤은 앞쪽 아이콘(모양·색)으로만 나눈다(10/10 색 규칙)
const ICON: Record<AlertTone, { name: IconName; color: string }> = {
  info: { name: 'Info', color: 'text-text-primary' },
  success: { name: 'CircleCheck', color: 'text-text-success' },
  warning: { name: 'TriangleAlert', color: 'text-text-warning' },
  danger: { name: 'CircleAlert', color: 'text-text-danger' },
}

export type AlertProps = {
  tone?: AlertTone
  /** 한 줄 */
  title: ReactNode
  /** 두 줄 이내 */
  description?: ReactNode
  /** 닫아도 되는 안내에만 넘긴다. 넘기면 닫기 버튼이 보인다. */
  onClose?: () => void
  className?: string
}

// 화면 안에 머무는 공지. 회색 바탕 + 앞쪽 아이콘 + 굵은 제목 + 설명.
export function Alert({ tone = 'info', title, description, onClose, className }: AlertProps) {
  return (
    <div
      role={tone === 'danger' ? 'alert' : 'status'}
      className={cn('flex w-full items-start gap-3 rounded-md bg-bg-subtle p-4', className)}
    >
      <Icon name={ICON[tone].name} className={cn('size-5', ICON[tone].color)} />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <p className="text-label-strong text-text-primary">{title}</p>
        {description && (
          <p className="line-clamp-2 text-body-s text-text-secondary">{description}</p>
        )}
      </div>
      {onClose && (
        <button
          type="button"
          aria-label="닫기"
          onClick={onClose}
          className={cn('shrink-0 rounded-sm text-text-primary', FOCUS_RING)}
        >
          <Icon name="X" className="size-5" />
        </button>
      )}
    </div>
  )
}
