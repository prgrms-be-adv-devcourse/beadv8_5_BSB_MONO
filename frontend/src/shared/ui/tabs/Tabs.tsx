'use client'

import { Tabs as BaseTabs } from '@base-ui/react/tabs'
import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { FOCUS_RING } from '../focus-ring'

export const Tabs = BaseTabs.Root
export const TabPanel = BaseTabs.Panel

export type TabBarProps = {
  /** fill: 모바일에서 탭이 폭을 똑같이 나눈다. hug: 태블릿·데스크톱에서 왼쪽부터 내용 폭만큼 나열한다. */
  layout?: 'fill' | 'hug'
  children: ReactNode
  className?: string
}

// 탭 묶음 막대. 아래 구분선은 끝까지 이어진다.
export function TabBar({ layout = 'fill', children, className }: TabBarProps) {
  return (
    <BaseTabs.List
      className={cn(
        'flex border-b border-border-default bg-bg-surface',
        layout === 'fill' ? '*:flex-1' : 'gap-2',
        className,
      )}
    >
      {children}
    </BaseTabs.List>
  )
}

export type TabProps = {
  value: string
  children: ReactNode
  /** 개수(예: 후기 128) */
  count?: ReactNode
  disabled?: boolean
}

// 탭 하나. 높이 48, 선택되면 진한 글자와 브랜드 색 밑줄(2).
export function Tab({ value, children, count, disabled }: TabProps) {
  return (
    <BaseTabs.Tab
      value={value}
      disabled={disabled}
      className={cn(
        'group flex h-12 flex-col items-stretch rounded-sm text-text-tertiary data-active:text-text-primary data-disabled:text-text-disabled',
        FOCUS_RING,
      )}
    >
      <span className="flex flex-1 items-center justify-center gap-1 px-4">
        <span className="text-label-l whitespace-nowrap">{children}</span>
        {count !== undefined && (
          <span className="text-label-s group-data-active:text-text-brand group-data-disabled:text-text-disabled">
            {count}
          </span>
        )}
      </span>
      <span className="h-0.5 group-data-active:bg-bg-brand group-data-disabled:bg-transparent" />
    </BaseTabs.Tab>
  )
}
