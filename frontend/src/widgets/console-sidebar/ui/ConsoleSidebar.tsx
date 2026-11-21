'use client'

import type { Route } from 'next'
import Link from 'next/link'
import { usePathname } from 'next/navigation'

import { useLogout } from '@/features/auth'
import { cn } from '@/shared/lib'
import { FOCUS_RING, Icon, Logo, type IconName } from '@/shared/ui'

type Item = {
  href: Route
  label: string
  icon: IconName
  /** 메뉴 묶음의 첫 항목이면 위에 묶음 여백(16)을 둔다 */
  groupStart?: boolean
}

const MENU: Record<
  ConsoleRole,
  {
    label: string
    home: Route
    settings: Item
    items: Item[]
  }
> = {
  seller: {
    label: '판매자',
    home: '/seller',
    settings: { href: '/seller/settings', label: '판매자 정보', icon: 'User' },
    items: [
      { href: '/seller', label: '대시보드', icon: 'Dashboard' },
      { href: '/seller/races', label: '대회 관리', icon: 'Calendar' },
      { href: '/seller/sales', label: '판매 현황', icon: 'Users' },
      { href: '/seller/kits', label: '키트 배송', icon: 'Truck' },
      { href: '/seller/settlements', label: '정산', icon: 'Inbox' },
      { href: '/seller/inquiries', label: '문의', icon: 'Help' },
      { href: '/seller/ai', label: '크루런 AI', icon: 'Message' },
    ],
  },
  admin: {
    label: '관리자',
    home: '/admin',
    settings: { href: '/admin/settings', label: '내 계정', icon: 'User' },
    items: [
      { href: '/admin', label: '대시보드', icon: 'Dashboard' },
      { href: '/admin/members', label: '회원 관리', icon: 'User', groupStart: true },
      { href: '/admin/sellers', label: '판매자 관리', icon: 'Users' },
      { href: '/admin/races', label: '대회 관리', icon: 'Calendar', groupStart: true },
      { href: '/admin/settlements', label: '정산 관리', icon: 'Inbox' },
      { href: '/admin/claims', label: '회수 관리', icon: 'Orders' },
      { href: '/admin/inquiries', label: '문의', icon: 'Help', groupStart: true },
      { href: '/admin/community-reports', label: '커뮤니티 신고', icon: 'CircleAlert' },
      { href: '/admin/ai', label: '크루런 AI', icon: 'Message', groupStart: true },
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
    <aside className="console-sidebar sticky top-0 flex h-dvh w-18 shrink-0 flex-col items-center gap-1 bg-bg-hero px-3.5 py-6 desktop:w-60 desktop:items-start desktop:px-4">
      <Link
        href={menu.home}
        className={cn('flex items-center gap-2 rounded-sm pb-4', FOCUS_RING)}
        aria-label={`크루런 ${menu.label} 홈`}
      >
        <Logo variant="symbol" className="desktop:hidden" />
        <Logo variant="wordmarkOnHero" className="hidden h-6 w-auto desktop:block" />
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
        <SidebarItem {...menu.settings} selected={isActive(menu.settings.href)} />
        {/* 판매자 시안에만 있다(2070:66595). 관리자 시안(2070:11050)에는 없다. 선택 표시는 하지 않는다 */}
        {role === 'seller' && (
          <SidebarItem href="/" label="크루런 홈으로" icon="Home" selected={false} />
        )}
        <LogoutItem />
      </div>
    </aside>
  )
}

function SidebarItem({ href, label, icon, groupStart, selected }: Item & { selected: boolean }) {
  return (
    <Link
      href={href}
      aria-current={selected ? 'page' : undefined}
      title={label}
      className={cn(itemClassName(selected), groupStart && 'mt-3')}
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
    'flex size-11 items-center justify-center gap-3 rounded-md text-body1-medium desktop:h-11 desktop:w-full desktop:justify-start desktop:px-3',
    selected
      ? 'bg-bg-hero-raised text-text-on-hero-accent'
      : 'text-text-on-hero-muted hover:bg-bg-hero-raised',
    FOCUS_RING,
  )
}
