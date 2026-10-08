'use client'

import { Drawer } from '@base-ui/react/drawer'
import type { ButtonHTMLAttributes, ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { IconButton } from '../button/IconButton'
import { OPTION } from '../field/Select'
import { Icon } from '../icon/icons'

export type BottomSheetProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: ReactNode
  /** 선택지 목록이 기본이다(BottomSheetOption). */
  children: ReactNode
  /** 아래 주요 버튼 영역 */
  footer?: ReactNode
}

// 화면 아래에서 올라와 선택이나 추가 입력을 받는 창(사이즈·종목 선택, 필터). 아래로 밀어 닫을 수 있다.
// 기기 아래 안전 영역은 피그마에 없어서 코드에서 더한다.
export function BottomSheet({ open, onOpenChange, title, children, footer }: BottomSheetProps) {
  return (
    <Drawer.Root open={open} onOpenChange={onOpenChange} swipeDirection="down">
      <Drawer.Portal>
        <Drawer.Backdrop className="fixed inset-0 z-50 bg-navy-950/50 transition-opacity duration-300 data-ending-style:opacity-0 data-starting-style:opacity-0" />
        <Drawer.Viewport className="fixed inset-x-0 bottom-0 z-50 flex justify-center">
          <Drawer.Popup
            className={cn(
              'flex max-h-[90dvh] w-full max-w-(--layout-app-max-width) flex-col rounded-t-xl bg-bg-surface shadow-lg outline-none',
              'translate-y-(--drawer-swipe-movement-y) transition-transform duration-300 data-ending-style:translate-y-full data-starting-style:translate-y-full',
              !footer && 'pb-[env(safe-area-inset-bottom)]',
            )}
          >
            <div className="flex justify-center pt-2 pb-1">
              <div className="h-1 w-9 rounded-full bg-bg-control-off" />
            </div>
            <div className="flex items-center gap-2 py-1 pr-2 pl-5">
              <Drawer.Title className="min-w-0 flex-1 text-heading-m text-text-primary">
                {title}
              </Drawer.Title>
              <Drawer.Close
                render={
                  <IconButton aria-label="닫기">
                    <Icon name="X" />
                  </IconButton>
                }
              />
            </div>
            <Drawer.Content className="flex flex-col overflow-y-auto px-3 py-2">
              {children}
            </Drawer.Content>
            {footer && (
              <div className="flex flex-col px-5 pt-2 pb-[calc(--spacing(5)+env(safe-area-inset-bottom))] *:w-full">
                {footer}
              </div>
            )}
          </Drawer.Popup>
        </Drawer.Viewport>
      </Drawer.Portal>
    </Drawer.Root>
  )
}

export type BottomSheetOptionProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  selected?: boolean
}

// BottomSheet 안 선택지 한 줄. Select/Option과 같은 모양이다.
export function BottomSheetOption({
  selected = false,
  type = 'button',
  className,
  children,
  ...props
}: BottomSheetOptionProps) {
  return (
    <button
      type={type}
      aria-pressed={selected}
      className={cn(
        OPTION,
        'hover:bg-bg-subtle focus-visible:bg-bg-subtle disabled:cursor-not-allowed disabled:text-text-disabled',
        selected && 'bg-bg-brand-subtle text-text-brand',
        className,
      )}
      {...props}
    >
      <span className="min-w-0 flex-1 truncate">{children}</span>
      {selected && <Icon name="Check" className="size-5" />}
    </button>
  )
}
