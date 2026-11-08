'use client'

import { Tabs as BaseTabs } from '@base-ui/react/tabs'
import { createContext, useContext, type ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { FOCUS_RING } from '../focus-ring'

export const Tabs = BaseTabs.Root
export const TabPanel = BaseTabs.Panel

type TabBarVariant = 'segment' | 'underline'
const VariantContext = createContext<TabBarVariant>('segment')

export type TabBarProps = {
  /** fill: 모바일에서 탭이 폭을 똑같이 나눈다. hug: 태블릿·데스크톱에서 왼쪽부터 내용 폭만큼 나열한다. */
  layout?: 'fill' | 'hug'
  /** segment: 회색 판 안의 흰 알약(기본). underline: 글자 아래 밑줄(피그마 시나리오 37 알림 목록) */
  variant?: TabBarVariant
  children: ReactNode
  className?: string
}

// 탭 묶음 막대.
// segment: 회색(bg-segment) 판 안에 탭이 4 간격으로 놓인다. 높이 48 = 여백 4 + 탭 40 + 여백 4.
// underline: 흰 바탕 아래 선 위에 탭이 20 간격으로 왼쪽부터 놓인다.
export function TabBar({ layout, variant = 'segment', children, className }: TabBarProps) {
  const fill = (layout ?? (variant === 'segment' ? 'fill' : 'hug')) === 'fill'
  return (
    <VariantContext value={variant}>
      <BaseTabs.List
        className={cn(
          variant === 'segment'
            ? 'flex gap-1 rounded-md bg-bg-segment p-1'
            : 'flex gap-5 border-b border-border-default px-4 pt-2.5',
          fill && '*:flex-1',
          className,
        )}
      >
        {children}
      </BaseTabs.List>
    </VariantContext>
  )
}

export type TabProps = {
  value: string
  children: ReactNode
  /** 개수(예: 후기 128) */
  count?: ReactNode
  disabled?: boolean
}

// 탭 하나. 높이 40, 고른 탭만 흰 바탕으로 떠 있고 글자가 굵어진다(Label/L Strong).
// underline: 고른 탭만 굵은 글자와 아래 2px 밑줄(text-primary)이 붙는다.
export function Tab({ value, children, count, disabled }: TabProps) {
  const variant = useContext(VariantContext)
  return (
    <BaseTabs.Tab
      value={value}
      disabled={disabled}
      className={cn(
        'group flex min-w-0 items-center justify-center gap-1 text-label-l text-text-tertiary',
        'data-active:font-bold data-active:text-text-primary data-disabled:text-text-disabled',
        variant === 'segment'
          ? 'h-10 rounded-sm px-4 data-active:bg-bg-surface data-active:shadow-sm'
          : 'relative pb-2.5 after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:rounded-[1px] data-active:after:bg-text-primary',
        FOCUS_RING,
      )}
    >
      <span className="truncate whitespace-nowrap">{children}</span>
      {count !== undefined && (
        <span className="text-label-s group-data-active:text-text-brand group-data-disabled:text-text-disabled">
          {count}
        </span>
      )}
    </BaseTabs.Tab>
  )
}
