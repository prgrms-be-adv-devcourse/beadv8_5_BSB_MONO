import { Fragment, type ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { Icon } from '../icon/icons'

export type StepperProps = {
  steps: ReactNode[]
  /** 지금 단계(0부터). 앞은 끝난 단계, 뒤는 남은 단계로 그린다. */
  current: number
  className?: string
}

const MARKER = {
  done: 'bg-bg-brand text-text-on-brand',
  current: 'bg-bg-brand-subtle text-text-brand inset-ring-2 inset-ring-border-brand',
  upcoming: 'bg-bg-surface text-text-tertiary inset-ring inset-ring-border-strong',
}

const LABEL = {
  done: 'text-text-secondary',
  current: 'text-text-primary',
  upcoming: 'text-text-tertiary',
}

// 대회 접수 단계 표시. 앞 단계가 끝나면 사이 선이 브랜드 색이 된다.
export function Stepper({ steps, current, className }: StepperProps) {
  return (
    <ol className={cn('flex w-full items-start', className)}>
      {steps.map((label, i) => {
        const state = i < current ? 'done' : i === current ? 'current' : 'upcoming'
        return (
          <Fragment key={i}>
            {i > 0 && (
              <li aria-hidden className="flex-1 px-1 pt-3.25">
                <div className={cn('h-0.5', i <= current ? 'bg-bg-brand' : 'bg-border-default')} />
              </li>
            )}
            <li
              aria-current={state === 'current' ? 'step' : undefined}
              className="flex shrink-0 flex-col items-center gap-2"
            >
              <span
                className={cn(
                  'flex size-7 items-center justify-center rounded-full text-label-m',
                  MARKER[state],
                )}
              >
                {state === 'done' ? <Icon name="Check" className="size-4" /> : i + 1}
              </span>
              <span className={cn('text-label-s whitespace-nowrap', LABEL[state])}>{label}</span>
            </li>
          </Fragment>
        )
      })}
    </ol>
  )
}
