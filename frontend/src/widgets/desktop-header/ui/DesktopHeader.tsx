import Link from 'next/link'

import { cn } from '@/shared/lib'
import { FOCUS_RING, Icon, Logo } from '@/shared/ui'

const ICON_LINK = cn(
  'inline-flex size-11 items-center justify-center rounded-md text-text-primary hover:bg-bg-subtle [&_svg]:size-5',
  FOCUS_RING,
)

// 피그마 Header Device=Desktop: 높이 72, 가운데 1200 칸. 판매자 등록처럼 사이드바가 없는 데스크톱 화면에 쓴다.
export function DesktopHeader() {
  return (
    <header className="flex h-18 shrink-0 justify-center border-b border-border-default bg-bg-surface">
      <div className="flex h-10 w-full max-w-(--layout-content-max-width) items-center gap-6 self-center">
        <Link href="/" aria-label="홈" className={cn('rounded-sm', FOCUS_RING)}>
          <Logo />
        </Link>
        <nav>
          <Link
            href="/seller-apply"
            className={cn(
              'flex h-10 items-center rounded-sm px-2 text-body1-medium text-text-brand',
              FOCUS_RING,
            )}
          >
            판매자
          </Link>
        </nav>
        <div className="flex-1" />
        <Link
          href="/races"
          className={cn(
            'flex h-10 w-70 items-center gap-2 rounded-full bg-bg-subtle px-4 text-body2-regular text-text-tertiary',
            FOCUS_RING,
          )}
        >
          <Icon name="Search" className="size-4.5" />
          대회 검색
        </Link>
        <div className="flex">
          <Link href="/cart" aria-label="장바구니" className={ICON_LINK}>
            <Icon name="Cart" />
          </Link>
          <Link href="/notifications" aria-label="알림" className={ICON_LINK}>
            <Icon name="Bell" />
          </Link>
          <Link href="/me" aria-label="마이" className={ICON_LINK}>
            <Icon name="User" />
          </Link>
        </div>
      </div>
    </header>
  )
}
