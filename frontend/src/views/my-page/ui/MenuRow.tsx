import type { Route } from 'next'
import Link from 'next/link'
import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'
import { FOCUS_RING, Icon, type IconName } from '@/shared/ui'

/** 아직 불러오지 못한 값 자리 */
export const EMPTY = '-'

// 피그마 마이 · 계정 설정의 메뉴 묶음: 작은 제목 아래 줄을 늘어놓는다
export function MenuGroup({
  title,
  brand,
  children,
}: {
  title: string
  /** 계정 설정은 제목을 파란색으로 */
  brand?: boolean
  children: ReactNode
}) {
  return (
    <section className="flex flex-col px-5 pt-8">
      <h2 className={cn('text-body2-medium', brand ? 'text-text-brand' : 'text-text-secondary')}>
        {title}
      </h2>
      {children}
    </section>
  )
}

type MenuRowProps = {
  icon?: IconName
  label: string
  value?: string
} & ({ href: Route } | { onClick: () => void; disabled?: boolean })

// 다른 화면으로 가는 줄은 링크, 그 자리에서 실행하는 줄(로그아웃)은 같은 모양의 버튼
export function MenuRow({ icon, label, value, ...action }: MenuRowProps) {
  const content = (
    <>
      {icon && <Icon name={icon} className="size-6 shrink-0" />}
      <span className="min-w-0 flex-1 text-body1-medium">{label}</span>
      {value && <span className="text-body2-medium text-text-secondary">{value}</span>}
      <Icon name="ChevronRight" className="size-5 shrink-0 text-text-tertiary" />
    </>
  )
  const className = cn('flex items-center gap-3 rounded-md py-3.5', FOCUS_RING)
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
