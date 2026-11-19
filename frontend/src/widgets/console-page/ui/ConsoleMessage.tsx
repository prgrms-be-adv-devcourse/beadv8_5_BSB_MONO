import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'
import { Button } from '@/shared/ui'

import { CONSOLE_BOX } from './consoleBox'

export type ConsoleMessageProps = {
  /** 목록을 불러오지 못했으면 error(다시 시도 버튼이 붙는다), 비었으면 empty */
  kind: 'error' | 'empty'
  children: ReactNode
  onRetry?: () => void
  retrying?: boolean
}

// 피그마 v4 관리자 · 목록 상태: 흰 카드 가운데에 글 한 줄. 아이콘은 쓰지 않는다.
export function ConsoleMessage({ kind, children, onRetry, retrying }: ConsoleMessageProps) {
  return (
    <div
      role={kind === 'error' ? 'alert' : 'status'}
      className={cn(
        'flex flex-col items-center justify-center gap-3 rounded-md p-6 text-center shadow-sm',
        CONSOLE_BOX,
        kind === 'error' ? 'min-h-120' : 'min-h-54',
      )}
    >
      <p
        className={cn(
          'text-body1-regular',
          kind === 'error' ? 'text-text-secondary' : 'text-text-primary',
        )}
      >
        {children}
      </p>
      {kind === 'error' && onRetry && (
        <Button variant="secondary" size="m" loading={retrying} onClick={onRetry}>
          다시 시도하기
        </Button>
      )}
    </div>
  )
}
