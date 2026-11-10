import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

export type KeyValueProps = { label: ReactNode; value: ReactNode; className?: string }

// 피그마 KeyValue: 고칠 수 없지만 읽어야 하는 값. 입력칸 모양을 쓰지 않아 입력할 곳이 아님을 드러낸다.
// 라벨 text-tertiary, 값 text-primary Medium.
export function KeyValue({ label, value, className }: KeyValueProps) {
  return (
    <div className={cn('flex items-center justify-between gap-4 text-body2-regular', className)}>
      <span className="shrink-0 text-text-tertiary">{label}</span>
      <span className="min-w-0 text-right font-medium text-text-primary">{value}</span>
    </div>
  )
}
