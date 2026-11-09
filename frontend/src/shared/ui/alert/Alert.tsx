import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { FOCUS_RING } from '../focus-ring'
import { Icon, type IconName } from '../icon/icons'

export type AlertTone = 'info' | 'success' | 'warning' | 'danger'

// 피그마 v2/Alert. 톤마다 바탕색과 앞쪽 아이콘(모양·색)이 바뀐다.
const TONE: Record<AlertTone, { icon: IconName; iconColor: string; bg: string }> = {
  info: { icon: 'Info', iconColor: 'text-text-tertiary', bg: 'bg-bg-muted' },
  success: { icon: 'CircleCheck', iconColor: 'text-text-success', bg: 'bg-bg-success-subtle' },
  warning: { icon: 'TriangleAlert', iconColor: 'text-text-warning', bg: 'bg-bg-warning-subtle' },
  danger: { icon: 'CircleAlert', iconColor: 'text-text-danger', bg: 'bg-bg-danger-subtle' },
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

// 화면 안에 머무는 공지. 톤 바탕 + 앞쪽 아이콘 + 굵은 제목 + 설명.
export function Alert({ tone = 'info', title, description, onClose, className }: AlertProps) {
  return (
    <div
      role={tone === 'danger' ? 'alert' : 'status'}
      className={cn('flex w-full items-start gap-3 rounded-md p-4', TONE[tone].bg, className)}
    >
      <Icon name={TONE[tone].icon} className={cn('size-5', TONE[tone].iconColor)} />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <p className="text-body2-bold text-text-primary">{title}</p>
        {description && (
          <p className="line-clamp-2 text-body2-regular text-text-secondary">{description}</p>
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
