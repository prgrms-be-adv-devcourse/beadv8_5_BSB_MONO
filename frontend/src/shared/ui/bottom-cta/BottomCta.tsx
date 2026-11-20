import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

export type BottomCtaProps = {
  /** Price 형태의 금액 줄. 없으면 Single(버튼 하나) 또는 Double(보조+주요) 형태가 된다. */
  price?: { label: ReactNode; amount: ReactNode }
  /** 보조 버튼(Secondary L). 주요 버튼보다 좁다. */
  secondary?: ReactNode
  /** 주요 버튼(Primary L). 남은 폭을 채운다. */
  primary: ReactNode
  /** 위쪽 1px 선. 기본은 켬(마이 등). 선이 없는 인증 화면은 false */
  bordered?: boolean
  className?: string
}

// 모바일·태블릿의 주요 버튼 영역. 내용 바로 아래에 붙는다. 내용이 화면보다 길 때 화면 아래에 붙이려면 className에 sticky bottom-0을 넘긴다.
// 앞 형제가 남은 높이를 채우지 않게 하는 규칙은 globals.css의 data-bottom-cta에 있다(CRU-77).
// 아이폰 홈 바 같은 기기 아래 안전 영역은 피그마에 없어서 코드에서 더한다.
export function BottomCta({
  price,
  secondary,
  primary,
  bordered = true,
  className,
}: BottomCtaProps) {
  return (
    <div
      data-bottom-cta
      className={cn(
        'flex items-center gap-2 bg-bg-surface px-6 pt-3 pb-[calc(--spacing(3)+env(safe-area-inset-bottom))]',
        bordered && 'border-t border-border-default',
        className,
      )}
    >
      {price && (
        <div className="flex shrink-0 flex-col">
          <span className="text-caption-regular text-text-tertiary">{price.label}</span>
          <span className="text-title2 text-text-primary">{price.amount}</span>
        </div>
      )}
      {secondary && <div className="flex w-30 shrink-0 *:w-full">{secondary}</div>}
      <div className="flex min-w-0 flex-1 *:w-full">{primary}</div>
    </div>
  )
}
