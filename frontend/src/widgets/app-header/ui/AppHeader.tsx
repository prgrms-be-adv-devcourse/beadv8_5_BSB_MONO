'use client'

import type { Route } from 'next'
import Link from 'next/link'
import type { ReactNode } from 'react'

import { NoticeBellLink } from '@/entities/notification'
import { cn, useGoBack } from '@/shared/lib'
import { FOCUS_RING, Icon, IconButton, Logo } from '@/shared/ui'

// IconButton(M 44, ghost)과 같은 모양의 링크
const ICON_LINK = cn(
  'inline-flex size-11 shrink-0 items-center justify-center rounded-full text-text-primary hover:bg-bg-subtle [&_svg]:size-5',
  FOCUS_RING,
)

type MainProps = {
  type?: 'main'
  /** false면 검색 아이콘을 빼고 알림만 둔다(홈) */
  search?: boolean
}
type SubProps = {
  type: 'sub'
  title: string
  /** 앱 안에서 온 기록이 없을 때 뒤로 가기가 갈 곳. 없으면 홈 */
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
        <h1 className="min-w-0 flex-1 truncate text-body1-bold">{props.title}</h1>
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
        {props.search !== false && (
          <Link href="/races" aria-label="대회 찾기" className={ICON_LINK}>
            <Icon name="Search" />
          </Link>
        )}
        <NoticeBellLink className={ICON_LINK} />
      </div>
    </header>
  )
}

function BackButton({ href, close }: { href?: Route; close?: boolean }) {
  const goBack = useGoBack()
  const label = close ? '닫기' : '뒤로 가기'
  const icon = <Icon name={close ? 'X' : 'ChevronLeft'} />
  // 닫기도 뒤로 간다. 끝난 화면은 폼을 replace해 들어왔으므로 출발한 화면으로 돌아가고, 같은 화면이 기록에 겹치지 않는다
  return (
    <IconButton aria-label={label} onClick={() => goBack(href ?? '/')}>
      {icon}
    </IconButton>
  )
}
