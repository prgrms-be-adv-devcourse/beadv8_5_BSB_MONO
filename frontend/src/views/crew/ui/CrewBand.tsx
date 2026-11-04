import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'
import { Button, EmptyState } from '@/shared/ui'

// 크루 화면의 흰 띠. 머리글 아래로 기둥 폭을 가득 채우고, 띠 사이는 Divider(section)로 나눈다(11/1 배치 규칙).
export function CrewBand({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <section className={cn('flex flex-col gap-3 bg-bg-surface px-4 py-6', className)}>
      {children}
    </section>
  )
}

// 시안에 없는 상태: 네트워크 · 서버 오류로 불러오지 못했을 때. 디자인 담당에게 시안을 요청해 둔 자리다.
export function CrewLoadError({ onRetry }: { onRetry: () => void }) {
  return (
    <EmptyState
      icon="CircleAlert"
      title="크루 정보를 불러오지 못했어요"
      description="잠시 후 다시 시도해 주세요."
      action={
        <Button variant="secondary" size="m" onClick={onRetry}>
          다시 불러오기
        </Button>
      }
    />
  )
}
