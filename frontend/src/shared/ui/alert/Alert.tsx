import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { FOCUS_RING } from '../focus-ring'
import { Icon, type IconName } from '../icon/icons'

export type AlertTone = 'info' | 'success' | 'warning' | 'danger'

// v4 시안(2070:38728·37360·38427): 모든 톤이 흰 바탕 + 1px 테두리. 톤은 앞쪽 아이콘(모양·색)으로만 구분한다.
const TONE: Record<AlertTone, { icon: IconName; iconColor: string }> = {
  info: { icon: 'Info', iconColor: 'text-text-tertiary' },
  success: { icon: 'CircleCheck', iconColor: 'text-text-success' },
  warning: { icon: 'TriangleAlert', iconColor: 'text-text-warning' },
  danger: { icon: 'CircleAlert', iconColor: 'text-text-danger' },
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

// 화면 안에 머무는 공지. 흰 바탕 + 테두리 + 앞쪽 아이콘 + 굵은 제목 + 설명.
export function Alert({ tone = 'info', title, description, onClose, className }: AlertProps) {
  return (
    <div
      role={tone === 'danger' ? 'alert' : 'status'}
      className={cn(
        'flex w-full items-center gap-3 rounded-md border border-border-default bg-bg-surface p-4',
        className,
      )}
    >
      <Icon name={TONE[tone].icon} className={cn('size-5', TONE[tone].iconColor)} />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <p
          className={cn(
            'text-body1-bold',
            tone === 'danger' ? 'text-text-danger' : 'text-text-primary',
          )}
        >
          {title}
        </p>
        {description && (
          <p
            className={cn(
              'line-clamp-2 text-body1-regular',
              tone === 'danger' ? 'text-text-primary' : 'text-text-secondary',
            )}
          >
            {description}
          </p>
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
