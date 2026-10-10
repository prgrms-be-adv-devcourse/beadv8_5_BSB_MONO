'use client'

import { Toast } from '@base-ui/react/toast'
import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { FOCUS_RING } from '../focus-ring'

export type ToastTone = 'info' | 'success' | 'error'

export type ShowToastOptions = {
  message: ReactNode
  tone?: ToastTone
  /** 행동은 하나까지 */
  action?: { label: ReactNode; onClick: () => void }
}

// 행동 결과를 3~4초 보여 주고 사라지는 알림. 앱 전체를 한 번 감싼다(app/_providers).
// 모바일은 화면 아래 가운데에서 좌우 16 여백에 맞춰 늘어나고, 태블릿 이상은 오른쪽 아래 24에 폭 360으로 고정한다.
export function ToastProvider({ children }: { children: ReactNode }) {
  return (
    <Toast.Provider timeout={4000}>
      {children}
      <Toast.Portal>
        <Toast.Viewport className="fixed inset-x-4 bottom-[calc(--spacing(4)+env(safe-area-inset-bottom))] z-50 flex flex-col gap-2 outline-none tablet:inset-x-auto tablet:right-6 tablet:bottom-6 tablet:w-90">
          <ToastList />
        </Toast.Viewport>
      </Toast.Portal>
    </Toast.Provider>
  )
}

function ToastList() {
  const { toasts } = Toast.useToastManager()
  return toasts.map((toast) => (
    <Toast.Root
      key={toast.id}
      toast={toast}
      className="transition-opacity duration-200 data-ending-style:opacity-0 data-starting-style:opacity-0"
    >
      {/* 피그마처럼 라이트·다크 모드 모두 어두운 카드라 dark 토큰으로 고정한다.
          모서리 8은 피그마 Radius 변수에 없는 값이라 그대로 적었다. */}
      <Toast.Content className="dark flex items-center gap-3 rounded-[8px] bg-bg-subtle px-4 py-3 shadow-lg inset-ring inset-ring-border-default">
        <Toast.Title className="min-w-0 flex-1 text-body-m text-text-primary" />
        {toast.actionProps && (
          <Toast.Action
            className={cn('shrink-0 rounded-sm text-label-m text-text-brand', FOCUS_RING)}
          />
        )}
      </Toast.Content>
    </Toast.Root>
  ))
}

export function useToast() {
  const manager = Toast.useToastManager()
  return function showToast({ message, tone = 'info', action }: ShowToastOptions) {
    manager.add({
      title: message,
      type: tone,
      // 오류는 화면 낭독기가 바로 읽도록 높은 우선순위로 보낸다.
      priority: tone === 'error' ? 'high' : 'low',
      actionProps: action ? { children: action.label, onClick: action.onClick } : undefined,
    })
  }
}
