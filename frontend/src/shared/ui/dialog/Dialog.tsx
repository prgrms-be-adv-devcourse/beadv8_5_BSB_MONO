'use client'

import { AlertDialog } from '@base-ui/react/alert-dialog'
import type { ReactNode } from 'react'

import { Button } from '../button/Button'

export type DialogProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: ReactNode
  body?: ReactNode
  /** confirm(확인), danger(되돌릴 수 없는 행동), alert(버튼 하나) */
  type?: 'confirm' | 'danger' | 'alert'
  confirmLabel: ReactNode
  cancelLabel?: ReactNode
  onConfirm: () => void
  /** 요청 중이면 확인 버튼에 Spinner를 붙인다. */
  confirming?: boolean
}

// 화면 가운데 떠서 확인을 받는 창. 모바일은 폭 320에 버튼이 반씩, 태블릿 이상은 폭 480에 버튼 오른쪽 정렬.
export function Dialog({
  open,
  onOpenChange,
  title,
  body,
  type = 'confirm',
  confirmLabel,
  cancelLabel = '취소',
  onConfirm,
  confirming = false,
}: DialogProps) {
  const button = 'flex-1 tablet:flex-none'
  return (
    <AlertDialog.Root open={open} onOpenChange={onOpenChange}>
      <AlertDialog.Portal>
        <AlertDialog.Backdrop className="fixed inset-0 z-50 bg-navy-950/50 transition-opacity duration-150 data-ending-style:opacity-0 data-starting-style:opacity-0" />
        <AlertDialog.Popup className="fixed top-1/2 left-1/2 z-50 flex w-80 max-w-[calc(100vw-var(--layout-page-margin)*2)] -translate-1/2 flex-col gap-6 rounded-lg bg-bg-surface p-6 shadow-lg transition-opacity duration-150 outline-none data-ending-style:opacity-0 data-starting-style:opacity-0 tablet:w-120">
          <div className="flex flex-col gap-2">
            <AlertDialog.Title className="text-heading-m text-text-primary">
              {title}
            </AlertDialog.Title>
            {body && (
              <AlertDialog.Description className="text-body-m text-text-secondary">
                {body}
              </AlertDialog.Description>
            )}
          </div>
          <div className="flex gap-2 tablet:justify-end">
            {type !== 'alert' && (
              <AlertDialog.Close render={<Button variant="secondary" className={button} />}>
                {cancelLabel}
              </AlertDialog.Close>
            )}
            <Button
              variant={type === 'danger' ? 'danger' : 'primary'}
              loading={confirming}
              onClick={onConfirm}
              className={button}
            >
              {confirmLabel}
            </Button>
          </div>
        </AlertDialog.Popup>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  )
}
