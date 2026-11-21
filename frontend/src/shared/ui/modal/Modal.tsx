'use client'

import { Dialog } from '@base-ui/react/dialog'
import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { IconButton } from '../button/IconButton'
import { Icon } from '../icon/icons'

export type ModalProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: ReactNode
  /** 폭 클래스. 기본 640(w-160), 표를 담는 창은 넓게 준다(예: w-240) */
  widthClassName?: string
  children: ReactNode
}

// 화면 가운데 떠서 내용을 담는 창. 확인·취소 버튼이 있는 짧은 질문은 Dialog를 쓰고,
// 표·입력처럼 내용이 큰 창은 여기에 담는다. 닫으면 내용이 사라져 안의 상태도 처음으로 돌아간다.
export function Modal({
  open,
  onOpenChange,
  title,
  widthClassName = 'w-160',
  children,
}: ModalProps) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-bg-overlay/45 transition-opacity duration-150 data-ending-style:opacity-0 data-starting-style:opacity-0" />
        <Dialog.Popup
          className={cn(
            'fixed top-1/2 left-1/2 z-50 flex max-h-[calc(100dvh-32px)] max-w-[calc(100vw-var(--layout-page-margin)*2)] -translate-1/2 flex-col gap-5 overflow-y-auto rounded-lg bg-bg-surface p-6 shadow-lg transition-opacity duration-150 outline-none data-ending-style:opacity-0 data-starting-style:opacity-0',
            widthClassName,
          )}
        >
          <div className="flex items-start gap-4">
            <Dialog.Title className="flex-1 text-title3 text-text-primary">{title}</Dialog.Title>
            <Dialog.Close render={<IconButton size="s" aria-label="닫기" />}>
              <Icon name="X" />
            </Dialog.Close>
          </div>
          {children}
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
