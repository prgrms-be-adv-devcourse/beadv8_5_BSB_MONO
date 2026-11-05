import type { Route } from 'next'
import Link from 'next/link'
import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'
import { FOCUS_RING, Icon, type IconName } from '@/shared/ui'

/** 아직 불러오지 못한 값 자리 */
export const EMPTY = '-'

export function MenuCard({ children }: { children: ReactNode }) {
  return (
    <section className="flex flex-col rounded-lg bg-bg-surface px-4 py-2 shadow-sm inset-ring inset-ring-border-default">
      {children}
    </section>
  )
}

type MenuRowProps = {
  icon: IconName
  label: string
  value?: string
  description?: string
} & ({ href: Route } | { onClick: () => void; disabled?: boolean })

// 다른 화면으로 가는 줄은 링크, 그 자리에서 실행하는 줄(로그아웃)은 같은 모양의 버튼
export function MenuRow({ icon, label, value, description, ...action }: MenuRowProps) {
  const content = (
    <>
      <Icon name={icon} className="size-5" />
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="text-label-m">{label}</span>
        {description && <span className="text-caption text-text-secondary">{description}</span>}
      </span>
      {value && <span className="text-label-m">{value}</span>}
      <Icon name="ChevronRight" className="size-5" />
    </>
  )
  const className = cn('flex items-center gap-3 rounded-md py-3', FOCUS_RING)
  if ('href' in action) {
    return (
      <Link href={action.href} className={className}>
        {content}
      </Link>
    )
  }
  return (
    <button
      type="button"
      onClick={action.onClick}
      disabled={action.disabled}
      className={cn(className, 'w-full text-left')}
    >
      {content}
    </button>
  )
}
