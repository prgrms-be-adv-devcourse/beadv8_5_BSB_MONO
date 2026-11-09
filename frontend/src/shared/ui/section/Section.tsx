import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

export type SectionProps = {
  title?: ReactNode
  /** 제목 오른쪽 (배지, 바꾸기 링크 등) */
  aside?: ReactNode
  className?: string
  children: ReactNode
}

// 회원 화면의 내용 묶음: bg-surface 판, 옆 16 · 위아래 24 여백, 제목 Heading/M.
// 기둥도 흰색이라 묶음은 여백과 제목으로 나뉜다.
export function Section({ title, aside, className, children }: SectionProps) {
  return (
    <section className={cn('flex flex-col gap-4 bg-bg-surface px-4 py-6', className)}>
      {(title || aside) && (
        <div className="flex items-center justify-between gap-2">
          {title && <h2 className="text-title3">{title}</h2>}
          {aside}
        </div>
      )}
      {children}
    </section>
  )
}
