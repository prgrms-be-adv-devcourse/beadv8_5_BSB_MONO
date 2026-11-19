import type { ReactNode } from 'react'

// 참가권 화면들이 함께 쓰는 구역 제목 · 테두리 상자 · 이름-값 줄 (피그마 v4)
export function TicketSection({
  title,
  mask,
  aside,
  children,
}: {
  title: string
  mask?: boolean
  /** 제목 오른쪽 버튼 */
  aside?: ReactNode
  children: ReactNode
}) {
  return (
    <section
      className="flex flex-col gap-4 px-4 pt-8"
      data-clarity-mask={mask ? 'true' : undefined}
    >
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-title3">{title}</h2>
        {aside}
      </div>
      {children}
    </section>
  )
}

export function TicketBox({ children, footer }: { children: ReactNode; footer?: ReactNode }) {
  return (
    <div className="flex flex-col overflow-hidden rounded-md border border-border-default">
      <div className="flex flex-col gap-3 p-4">
        {children}
        {footer}
      </div>
    </div>
  )
}

export function TicketInfoRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex gap-3 text-body1-regular">
      <span className="w-22 shrink-0 text-text-secondary">{label}</span>
      <div className="min-w-0 flex-1 text-text-primary">{children}</div>
    </div>
  )
}
