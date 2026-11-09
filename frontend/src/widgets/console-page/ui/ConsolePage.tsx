import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

export type ConsolePageProps = {
  title: string
  description?: string
  /** 제목 오른쪽 버튼 자리 */
  actions?: ReactNode
  className?: string
  children: ReactNode
}

// 판매자·관리자 화면의 본문 틀(피그마 main): 위 40 · 옆 40 · 아래 64 여백, 최대 1200, 덩어리 사이 24
export function ConsolePage({
  title,
  description,
  actions,
  className,
  children,
}: ConsolePageProps) {
  return (
    <main className="flex min-w-0 flex-1 flex-col items-center px-10 pt-10 pb-16">
      <div
        className={cn('flex w-full max-w-(--layout-content-max-width) flex-col gap-6', className)}
      >
        <div className="flex items-center justify-between gap-4">
          <div className="flex min-w-0 flex-1 flex-col gap-1">
            <h1 className="text-title1">{title}</h1>
            {description && <p className="text-body2-regular text-text-secondary">{description}</p>}
          </div>
          {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
        </div>
        {children}
      </div>
    </main>
  )
}
