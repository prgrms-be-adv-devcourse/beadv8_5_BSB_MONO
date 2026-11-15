'use client'

import { Drawer } from '@base-ui/react/drawer'
import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

type MenuSheetProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** 화면 낭독기용 이름. 눈에는 보이지 않는다 */
  label: string
  children: ReactNode
}

// 피그마 M35-1·2·8·9 더보기 시트: 제목 줄 없이 손잡이 아래에 행동만 둔다. 아래로 밀어 닫는다.
export function MenuSheet({ open, onOpenChange, label, children }: MenuSheetProps) {
  return (
    <Drawer.Root open={open} onOpenChange={onOpenChange} swipeDirection="down">
      <Drawer.Portal>
        <Drawer.Backdrop className="fixed inset-0 z-50 bg-bg-overlay/40 transition-opacity duration-300 data-ending-style:opacity-0 data-starting-style:opacity-0" />
        <Drawer.Viewport className="fixed inset-x-0 bottom-0 z-50 flex justify-center">
          <Drawer.Popup
            className={cn(
              'flex w-full max-w-(--layout-app-max-width) flex-col rounded-t-xl bg-bg-surface px-5 pt-3 pb-[calc(--spacing(8)+env(safe-area-inset-bottom))] shadow-lg outline-none',
              'translate-y-(--drawer-swipe-movement-y) transition-transform duration-300 data-ending-style:translate-y-full data-starting-style:translate-y-full',
            )}
          >
            <Drawer.Title className="sr-only">{label}</Drawer.Title>
            <div className="mx-auto h-1 w-9 rounded-full bg-bg-control-off" />
            <Drawer.Content className="mt-2 flex flex-col gap-2">{children}</Drawer.Content>
          </Drawer.Popup>
        </Drawer.Viewport>
      </Drawer.Portal>
    </Drawer.Root>
  )
}

// 시트 안 행동 한 줄(높이 56). danger는 되돌릴 수 없는 행동이라 빨간 글자
export function MenuSheetAction({
  danger = false,
  onClick,
  children,
}: {
  danger?: boolean
  onClick: () => void
  children: ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'h-14 rounded-md text-left text-body1-medium hover:bg-bg-subtle focus-visible:bg-bg-subtle',
        danger ? 'text-text-danger' : 'text-text-primary',
      )}
    >
      {children}
    </button>
  )
}
