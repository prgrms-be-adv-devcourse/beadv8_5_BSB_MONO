'use client'

import { AlertDialog } from '@base-ui/react/alert-dialog'
import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

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
  /** 취소 버튼을 눌렀을 때만 부른다(Esc · 바깥 클릭으로 닫는 것은 onOpenChange만) */
  onCancel?: () => void
  onConfirm: () => void
  /** 요청 중이면 확인 버튼에 Spinner를 붙인다. */
  confirming?: boolean
}

// 화면 가운데 떠서 확인을 받는 창. 모바일 폭 320, 태블릿 이상 폭 400.
// 글은 가운데 정렬, 버튼 줄은 모달 폭을 채우고 두 개면 같은 폭으로 나눈다(DS-025·DS-028).
export function Dialog({
  open,
  onOpenChange,
  title,
  body,
  type = 'confirm',
  confirmLabel,
  cancelLabel = '취소',
  onCancel,
  onConfirm,
  confirming = false,
}: DialogProps) {
  return (
    <AlertDialog.Root open={open} onOpenChange={onOpenChange}>
      <AlertDialog.Portal>
        <AlertDialog.Backdrop className="fixed inset-0 z-50 bg-bg-overlay/40 transition-opacity duration-150 data-ending-style:opacity-0 data-starting-style:opacity-0" />
        <AlertDialog.Popup className="fixed top-1/2 left-1/2 z-50 flex w-80 max-w-[calc(100vw-var(--layout-page-margin)*2)] -translate-1/2 flex-col gap-5 rounded-xl bg-bg-surface px-5 pt-6 pb-5 shadow-lg transition-opacity duration-150 outline-none data-ending-style:opacity-0 data-starting-style:opacity-0 tablet:w-100">
          <div className="flex flex-col gap-1.5">
            <AlertDialog.Title className="text-center text-title3 text-text-primary">
              {title}
            </AlertDialog.Title>
            {body && (
              // 본문에 입력칸(반려 사유 등)을 넣을 수 있게 p 대신 div로 그린다
              <AlertDialog.Description
                render={<div />}
                className={cn(
                  'text-body2-regular text-text-secondary',
                  // 글 본문만 가운데. 입력 폼 같은 요소가 들어오면 왼쪽 정렬을 그대로 둔다
                  typeof body === 'string' && 'text-center',
                )}
              >
                {body}
              </AlertDialog.Description>
            )}
          </div>
          <div className="flex gap-2">
            {type !== 'alert' && (
              <AlertDialog.Close
                render={<Button variant="secondary" className="flex-1" />}
                onClick={onCancel}
              >
                {cancelLabel}
              </AlertDialog.Close>
            )}
            <Button
              variant={type === 'danger' ? 'danger' : 'primary'}
              loading={confirming}
              onClick={onConfirm}
              className="flex-1"
            >
              {confirmLabel}
            </Button>
          </div>
        </AlertDialog.Popup>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  )
}
