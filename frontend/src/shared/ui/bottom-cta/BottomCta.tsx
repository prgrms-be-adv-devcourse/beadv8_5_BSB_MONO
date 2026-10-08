import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

export type BottomCtaProps = {
  /** Price 형태의 금액 줄. 없으면 Single(버튼 하나) 또는 Double(보조+주요) 형태가 된다. */
  price?: { label: ReactNode; amount: ReactNode }
  /** 보조 버튼(Secondary L). 주요 버튼보다 좁다. */
  secondary?: ReactNode
  /** 주요 버튼(Primary L). 남은 폭을 채운다. */
  primary: ReactNode
  className?: string
}

// 모바일·태블릿에서 화면 아래에 고정하는 주요 버튼 영역. 데스크톱에서는 고정하지 않고 요약 카드 안에 둔다.
// 아이폰 홈 바 같은 기기 아래 안전 영역은 피그마에 없어서 코드에서 더한다.
export function BottomCta({ price, secondary, primary, className }: BottomCtaProps) {
  return (
    <div
      className={cn(
        'flex items-center gap-2 border-t border-border-default bg-bg-surface px-4 pt-3 pb-[calc(--spacing(3)+env(safe-area-inset-bottom))]',
        className,
      )}
    >
      {price && (
        <div className="flex shrink-0 flex-col">
          <span className="text-caption text-text-tertiary">{price.label}</span>
          <span className="font-display text-display-s text-text-primary">{price.amount}</span>
        </div>
      )}
      {secondary && <div className="flex w-30 shrink-0 *:w-full">{secondary}</div>}
      <div className="flex min-w-0 flex-1 *:w-full">{primary}</div>
    </div>
  )
}
