import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

export type KeyValueProps = {
  label: ReactNode
  value: ReactNode
  /** 값 정렬. 기본 left, right면 값을 오른쪽 끝에 붙인다 */
  align?: 'left' | 'right'
  className?: string
}

// 피그마 KeyValue: 고칠 수 없지만 읽어야 하는 값. 입력칸 모양을 쓰지 않아 입력할 곳이 아님을 드러낸다.
// v4: 글자 16, 라벨 칸 120 고정·왼쪽 정렬, 값은 보통 굵기. 라벨 칸 폭을 바꾸려면 className에 [&>span:first-child]:w-22 처럼 넘긴다.
export function KeyValue({ label, value, align = 'left', className }: KeyValueProps) {
  return (
    <div className={cn('flex items-center gap-4 text-body1-regular', className)}>
      <span className="w-30 shrink-0 text-text-secondary">{label}</span>
      <span className={cn('min-w-0 flex-1 text-text-primary', align === 'right' && 'text-right')}>
        {value}
      </span>
    </div>
  )
}
