'use client'

import { Tabs as BaseTabs } from '@base-ui/react/tabs'
import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { FOCUS_RING } from '../focus-ring'

export const Tabs = BaseTabs.Root
export const TabPanel = BaseTabs.Panel

export type TabBarProps = {
  /** fill: 탭이 폭을 똑같이 나눈다(기본). hug: 왼쪽부터 내용 폭만큼 20 간격으로 나열한다. */
  layout?: 'fill' | 'hug'
  children: ReactNode
  className?: string
}

// 피그마 v2/TabBar. 흰 바탕 아래 1px 선 위에 탭이 놓인다. 회색 판 모양은 Segmented를 쓴다.
export function TabBar({ layout = 'fill', children, className }: TabBarProps) {
  return (
    <BaseTabs.List
      className={cn(
        'flex border-b border-border-default bg-bg-surface',
        layout === 'fill' ? '*:flex-1' : 'gap-5 px-4',
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

// 피그마 v2/Tab. 높이 48, 고른 탭만 굵은 글자(Body1/Bold)와 아래 2px 밑줄(text-primary)이 붙는다.
export function Tab({ value, children, count, disabled }: TabProps) {
  return (
    <BaseTabs.Tab
      value={value}
      disabled={disabled}
      className={cn(
        'group relative flex min-w-0 items-center justify-center gap-1 py-3 text-body1-medium text-text-tertiary',
        'data-active:font-bold data-active:text-text-primary data-disabled:opacity-50',
        'after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:rounded-[1px] data-active:after:bg-text-primary',
        FOCUS_RING,
      )}
    >
      <span className="truncate whitespace-nowrap">{children}</span>
      {count !== undefined && (
        <span className="text-caption-medium group-data-active:text-text-brand">{count}</span>
      )}
    </BaseTabs.Tab>
  )
}
