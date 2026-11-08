'use client'

import type { Route } from 'next'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'
import { FOCUS_RING, Icon, IconButton, Logo } from '@/shared/ui'

// IconButton(M 44, ghost)과 같은 모양의 링크
const ICON_LINK = cn(
  'inline-flex size-11 shrink-0 items-center justify-center rounded-full text-text-primary hover:bg-bg-subtle [&_svg]:size-5',
  FOCUS_RING,
)

type MainProps = { type?: 'main' }
type SubProps = {
  type: 'sub'
  title: string
  /** 뒤로 가기 대상. 없으면 브라우저 뒤로 가기 */
  backHref?: Route
  /** 흐름을 끝내는 화면(결제 결과 등)은 뒤로 가기 대신 닫기(X)를 쓴다 */
  close?: boolean
  /** 오른쪽 아이콘 버튼 자리 (공유 등) */
  action?: ReactNode
}

export type AppHeaderProps = (MainProps | SubProps) & { className?: string }

// 피그마 Header Device=Mobile. 회원 화면은 PC에서도 600 기둥 안에서 이 모양을 쓴다.
export function AppHeader(props: AppHeaderProps) {
  const base = cn(
    'sticky top-0 z-10 flex h-(--layout-header-height) shrink-0 items-center gap-1 border-b border-border-default bg-bg-surface',
    props.className,
  )

  if (props.type === 'sub') {
    return (
      <header className={cn(base, 'px-1')}>
        <BackButton href={props.backHref} close={props.close} />
        <h1 className="min-w-0 flex-1 truncate text-heading-s">{props.title}</h1>
        {props.action}
      </header>
    )
  }

  return (
    <header className={cn(base, 'pr-1 pl-(--layout-page-margin)')}>
      <Link href="/" aria-label="홈" className={cn('rounded-sm', FOCUS_RING)}>
        <Logo />
      </Link>
      <div className="flex-1" />
      <div className="flex">
        <Link href="/races" aria-label="대회 찾기" className={ICON_LINK}>
          <Icon name="Search" />
        </Link>
        <Link href="/notifications" aria-label="알림" className={ICON_LINK}>
          <Icon name="Bell" />
        </Link>
      </div>
    </header>
  )
}

function BackButton({ href, close }: { href?: Route; close?: boolean }) {
  const router = useRouter()
  const label = close ? '닫기' : '뒤로 가기'
  const icon = <Icon name={close ? 'X' : 'ChevronLeft'} />
  if (href) {
    return (
      <Link href={href} aria-label={label} className={ICON_LINK}>
        {icon}
      </Link>
    )
  }
  return (
    <IconButton aria-label={label} onClick={() => router.back()}>
      {icon}
    </IconButton>
  )
}
