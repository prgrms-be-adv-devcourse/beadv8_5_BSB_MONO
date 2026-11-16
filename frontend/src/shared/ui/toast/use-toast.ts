'use client'

import { Toast } from '@base-ui/react/toast'

import type { ShowToastOptions } from './Toast'

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
