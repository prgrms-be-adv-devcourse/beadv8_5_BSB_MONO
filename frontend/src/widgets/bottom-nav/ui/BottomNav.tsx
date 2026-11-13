'use client'

import type { Route } from 'next'
import Link from 'next/link'
import { usePathname } from 'next/navigation'

import { cn } from '@/shared/lib'
import { FOCUS_RING, Icon, Logo, type IconName } from '@/shared/ui'

type Item = { href: Route; label: string; icon: IconName }

// 피그마 v2/BottomNav · 세미: 대회 · 내 참가권 | 로고(홈) | 크루 · 마이.
// 알림은 헤더 오른쪽 위 아이콘으로 들어간다(시나리오 37).
const LEFT: Item[] = [
  { href: '/races', label: '대회', icon: 'Calendar' },
  { href: '/tickets', label: '내 참가권', icon: 'Orders' },
]
const RIGHT: Item[] = [
  { href: '/crews', label: '크루', icon: 'Users' },
  { href: '/me', label: '마이', icon: 'User' },
]

export function BottomNav() {
  const pathname = usePathname()
  const isActive = (href: string) => pathname === href || pathname.startsWith(`${href}/`)

  return (
    <nav
      aria-label="주요 메뉴"
      className="sticky bottom-0 z-10 flex shrink-0 border-t border-border-default bg-bg-surface py-1"
    >
      {LEFT.map((item) => (
        <NavItem key={item.href} item={item} selected={isActive(item.href)} />
      ))}
      <Link
        href="/"
        aria-label="홈"
        aria-current={pathname === '/' ? 'page' : undefined}
        className={cn('flex flex-1 items-center justify-center rounded-md', FOCUS_RING)}
      >
        <Logo variant="navHome" />
      </Link>
      {RIGHT.map((item) => (
        <NavItem key={item.href} item={item} selected={isActive(item.href)} />
      ))}
    </nav>
  )
}

function NavItem({ item, selected }: { item: Item; selected: boolean }) {
  return (
    <Link
      href={item.href}
      aria-current={selected ? 'page' : undefined}
      className={cn(
        'flex h-14 flex-1 flex-col items-center justify-center gap-0.5 rounded-md text-caption-medium',
        selected ? 'font-bold text-text-primary' : 'text-text-tertiary',
        FOCUS_RING,
      )}
    >
      {/* 선택된 탭은 아이콘만 브랜드 색, 글자는 굵은 검정 (피그마 v2/BottomNav) */}
      <Icon name={item.icon} className={selected ? 'text-text-brand' : undefined} />
      {item.label}
    </Link>
  )
}
