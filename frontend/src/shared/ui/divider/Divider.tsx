import { cn } from '@/shared/lib'

export type DividerProps = {
  /** line(기본) / strong(진한 선) / section(두께 8, 모바일 섹션 사이) */
  type?: 'line' | 'strong' | 'section'
  /** vertical은 같은 줄 정보 사이에 쓴다. */
  orientation?: 'horizontal' | 'vertical'
  className?: string
}

const COLOR = {
  line: 'bg-border-default',
  strong: 'bg-border-strong',
  section: 'bg-bg-subtle',
}

export function Divider({ type = 'line', orientation = 'horizontal', className }: DividerProps) {
  const thickness = type === 'section' ? 'h-2' : 'h-px'
  return (
    <div
      role="separator"
      aria-orientation={orientation}
      className={cn(
        'shrink-0',
        COLOR[type],
        orientation === 'horizontal' ? cn('w-full', thickness) : 'w-px self-stretch',
        className,
      )}
    />
  )
}
