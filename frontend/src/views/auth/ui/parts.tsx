import Link from 'next/link'
import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'
import { FOCUS_RING, Logo } from '@/shared/ui'

// 로그인·찾기 화면 가운데의 큰 로고(150×44)와 제목
export function AuthIntro({ title, size = 'm' }: { title: string; size?: 's' | 'm' }) {
  return (
    <div className="flex flex-col items-center gap-1 pb-3">
      <Logo className="h-11 w-[150px]" />
      <div className="h-2" />
      <p className={size === 's' ? 'text-body1-bold' : 'text-title3'}>{title}</p>
    </div>
  )
}

// 가입 단계 제목 (왼쪽 정렬)
export function StepIntro({ title, description }: { title: string; description?: string }) {
  return (
    <div className="flex flex-col gap-1">
      <h2 className="text-title2 whitespace-pre-line">{title}</h2>
      {description && <p className="text-body2-regular text-text-tertiary">{description}</p>}
    </div>
  )
}

// 회원 화면 본문이 짧을 때 가운데로 모으는 판 (bg-default, 옆 16 · 위아래 24, 칸 사이 20)
export function CenterBody({ children, center = true }: { children: ReactNode; center?: boolean }) {
  return (
    <main className={center ? 'flex flex-1 flex-col justify-center' : 'flex flex-1 flex-col'}>
      <div className="flex flex-col gap-5 px-4 py-6">{children}</div>
    </main>
  )
}

// 피그마 계정 찾기 탭 두 개. 화면이 둘로 나뉘어 있어 TabBar 모양의 링크로 만든다.
export function AccountTabs({ current }: { current: 'email' | 'password' }) {
  const tabs = [
    { key: 'email', href: '/find-email', label: '이메일 찾기' },
    { key: 'password', href: '/password-reset', label: '비밀번호 찾기' },
  ] as const
  return (
    <nav aria-label="계정 찾기" className="flex border-b border-border-default bg-bg-surface">
      {tabs.map((tab) => (
        <Link
          key={tab.key}
          href={tab.href}
          aria-current={tab.key === current ? 'page' : undefined}
          className={cn(
            'relative flex-1 py-3 text-center text-body1-medium text-text-tertiary',
            'aria-[current=page]:font-bold aria-[current=page]:text-text-primary',
            'after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 aria-[current=page]:after:bg-text-primary',
            FOCUS_RING,
          )}
        >
          {tab.label}
        </Link>
      ))}
    </nav>
  )
}
