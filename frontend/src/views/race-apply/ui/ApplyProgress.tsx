import { cn } from '@/shared/lib'
import { FOCUS_RING } from '@/shared/ui'

// 피그마 v2/Stepper · 가로 진행 막대. 지금 단계까지 막대가 차고, 끝난 단계 이름을 누르면 그 단계로 돌아간다.
export function ApplyProgress({
  steps,
  current,
  onStep,
}: {
  steps: string[]
  /** 지금 단계(0부터) */
  current: number
  onStep: (step: number) => void
}) {
  return (
    <ol className="flex gap-2">
      {steps.map((label, i) => (
        <li
          key={label}
          aria-current={i === current ? 'step' : undefined}
          className="flex flex-1 flex-col gap-2"
        >
          <div className={cn('h-1 rounded-full', i <= current ? 'bg-bg-brand' : 'bg-bg-track')} />
          {i < current ? (
            <button
              type="button"
              onClick={() => onStep(i)}
              className={cn(
                'self-start rounded-sm text-caption-medium text-text-secondary',
                FOCUS_RING,
              )}
            >
              {label}
            </button>
          ) : (
            <span
              className={cn(
                'text-caption-medium',
                i === current ? 'font-bold text-text-primary' : 'text-text-tertiary',
              )}
            >
              {label}
            </span>
          )}
        </li>
      ))}
    </ol>
  )
}
