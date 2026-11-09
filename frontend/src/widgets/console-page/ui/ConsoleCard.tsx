import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

export type ConsoleCardProps = {
  title?: ReactNode
  /** 제목 오른쪽 (보기 링크, 배지 등) */
  aside?: ReactNode
  className?: string
  children: ReactNode
}

// 판매자·관리자 화면의 흰 카드: 안쪽 24, 둥글기 16, Shadow/SM, 제목 Heading/S
export function ConsoleCard({ title, aside, className, children }: ConsoleCardProps) {
  return (
    <section
      className={cn('flex flex-col gap-4 rounded-lg bg-bg-surface p-6 shadow-sm', className)}
    >
      {(title || aside) && (
        <div className="flex items-center justify-between gap-4">
          {title && <h2 className="text-body1-bold">{title}</h2>}
          {aside}
        </div>
      )}
      {children}
    </section>
  )
}
