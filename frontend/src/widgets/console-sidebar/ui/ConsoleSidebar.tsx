'use client'

import type { Route } from 'next'
import Link from 'next/link'
import { usePathname } from 'next/navigation'

import { useLogout } from '@/features/auth'
import { cn } from '@/shared/lib'
import { FOCUS_RING, Icon, Logo, type IconName } from '@/shared/ui'

type Item = { href: Route; label: string; icon: IconName }

const MENU: Record<ConsoleRole, { label: string; home: Route; settings: Route; items: Item[] }> = {
  seller: {
    label: '판매자',
    home: '/seller',
    settings: '/seller/settings',
    items: [
      { href: '/seller', label: '대시보드', icon: 'Dashboard' },
      { href: '/seller/races', label: '대회 관리', icon: 'Calendar' },
      { href: '/seller/sales', label: '주문 · 참가자', icon: 'Orders' },
      { href: '/seller/settlements', label: '정산', icon: 'Inbox' },
    ],
  },
  admin: {
    label: '관리자',
    home: '/admin',
    settings: '/admin/settings',
    items: [
      { href: '/admin', label: '대시보드', icon: 'Dashboard' },
      { href: '/admin/sellers', label: '판매자 승인', icon: 'Users' },
      { href: '/admin/members', label: '회원 · 주문', icon: 'Orders' },
      { href: '/admin/settlements', label: '정산 · 환급 처리', icon: 'Inbox' },
      { href: '/admin/races', label: '대회 관리', icon: 'Calendar' },
      { href: '/admin/stats', label: '이용 통계', icon: 'Chart' },
    ],
  },
}

export type ConsoleRole = 'seller' | 'admin'

// 피그마 Sidebar. 데스크톱은 Expanded 240, 태블릿은 Collapsed 72(아이콘만). 모바일은 지원하지 않는다.
export function ConsoleSidebar({ role }: { role: ConsoleRole }) {
  const pathname = usePathname()
  const menu = MENU[role]
  // 대시보드(홈)는 정확히 같을 때만, 나머지는 하위 경로까지 선택으로 본다
  const isActive = (href: Route) =>
    href === menu.home ? pathname === href : pathname === href || pathname.startsWith(`${href}/`)

  return (
    <aside className="sticky top-0 flex h-dvh w-18 shrink-0 flex-col items-center gap-2 bg-bg-hero px-3.5 py-5 desktop:w-60 desktop:items-start desktop:px-4">
      <Link
        href={menu.home}
        className={cn('flex items-center gap-2 rounded-sm pb-4', FOCUS_RING)}
        aria-label={`크루런 ${menu.label} 홈`}
      >
        <Logo variant="symbol" className="desktop:hidden" />
        <Logo variant="wordmarkOnHero" className="hidden desktop:block" />
        <span className="hidden text-caption-medium text-text-on-hero-muted desktop:inline">
          {menu.label}
        </span>
      </Link>
      <nav aria-label={`${menu.label} 메뉴`} className="flex w-full flex-col gap-1">
        {menu.items.map((item) => (
          <SidebarItem key={item.href} {...item} selected={isActive(item.href)} />
        ))}
      </nav>
      <div className="flex-1" />
      <div className="flex w-full flex-col gap-1">
        <SidebarItem href={menu.settings} label="설정" icon="Settings" selected={false} />
        {/* 콘솔 밖 크루런 홈으로 나가는 링크라 선택 표시를 하지 않는다. 로그인 상태는 그대로다 */}
        <SidebarItem href="/" label="크루런 홈으로" icon="Home" selected={false} />
        <LogoutItem />
      </div>
    </aside>
  )
}

function SidebarItem({ href, label, icon, selected }: Item & { selected: boolean }) {
  return (
    <Link
      href={href}
      aria-current={selected ? 'page' : undefined}
      title={label}
      className={itemClassName(selected)}
    >
      <ItemContent label={label} icon={icon} />
    </Link>
  )
}

// 마이페이지 로그아웃과 같은 useLogout을 쓴다. 다른 화면으로 가는 링크가 아니라 같은 모양의 버튼
function LogoutItem() {
  const logout = useLogout()
  return (
    <button
      type="button"
      onClick={() => logout.mutate()}
      disabled={logout.isPending}
      title="로그아웃"
      className={itemClassName(false)}
    >
      <ItemContent label="로그아웃" icon="LogOut" />
    </button>
  )
}

function ItemContent({ label, icon }: { label: string; icon: IconName }) {
  return (
    <>
      <Icon name={icon} className="size-5" />
      <span className="sr-only desktop:not-sr-only">{label}</span>
    </>
  )
}

function itemClassName(selected: boolean) {
  return cn(
    'flex size-11 items-center justify-center gap-3 rounded-md text-body2-medium desktop:h-11 desktop:w-full desktop:justify-start desktop:px-3',
    selected
      ? 'bg-bg-hero-raised text-text-on-hero-accent'
      : 'text-text-on-hero-muted hover:bg-bg-hero-raised',
    FOCUS_RING,
  )
}
