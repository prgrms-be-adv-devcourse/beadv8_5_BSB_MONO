import type { ReactNode } from 'react'

import { Logo } from '@/shared/ui'

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
export function StepIntro({ title, description }: { title: string; description: string }) {
  return (
    <div className="flex flex-col gap-1">
      <h2 className="text-title3">{title}</h2>
      <p className="text-body2-regular text-text-tertiary">{description}</p>
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
