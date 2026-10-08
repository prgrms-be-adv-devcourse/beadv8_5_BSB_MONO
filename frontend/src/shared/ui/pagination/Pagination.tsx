'use client'

import type { ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { Button } from '../button/Button'
import { FOCUS_RING } from '../focus-ring'
import { Icon } from '../icon/icons'

export type PaginationProps = {
  /** 1부터 */
  page: number
  totalPages: number
  onPageChange: (page: number) => void
  className?: string
}

const MAX_SLOTS = 7

// 번호는 최대 7칸이고 나머지는 '…'로 줄인다. 예: 1 … 4 5 6 … 20
export function pageSlots(page: number, totalPages: number): (number | 'ellipsis')[] {
  if (totalPages <= MAX_SLOTS) {
    return Array.from({ length: totalPages }, (_, i) => i + 1)
  }
  if (page <= 4) {
    return [1, 2, 3, 4, 5, 'ellipsis', totalPages]
  }
  if (page >= totalPages - 3) {
    return [1, 'ellipsis', ...Array.from({ length: 5 }, (_, i) => totalPages - 4 + i)]
  }
  return [1, 'ellipsis', page - 1, page, page + 1, 'ellipsis', totalPages]
}

const ITEM = cn('flex size-9 items-center justify-center rounded-sm text-label-m', FOCUS_RING)

export function Pagination({ page, totalPages, onPageChange, className }: PaginationProps) {
  return (
    <nav aria-label="페이지" className={cn('flex items-center gap-1', className)}>
      <button
        type="button"
        aria-label="이전 페이지"
        disabled={page <= 1}
        onClick={() => onPageChange(page - 1)}
        className={cn(
          ITEM,
          'text-text-secondary disabled:cursor-not-allowed disabled:text-text-disabled',
        )}
      >
        <Icon name="ChevronLeft" className="size-5" />
      </button>
      {pageSlots(page, totalPages).map((slot, i) =>
        slot === 'ellipsis' ? (
          <span key={`ellipsis-${i}`} aria-hidden className={cn(ITEM, 'text-text-secondary')}>
            …
          </span>
        ) : (
          <button
            key={slot}
            type="button"
            aria-current={slot === page ? 'page' : undefined}
            onClick={() => onPageChange(slot)}
            className={cn(
              ITEM,
              slot === page ? 'bg-bg-brand text-text-on-brand' : 'text-text-secondary',
            )}
          >
            {slot}
          </button>
        ),
      )}
      <button
        type="button"
        aria-label="다음 페이지"
        disabled={page >= totalPages}
        onClick={() => onPageChange(page + 1)}
        className={cn(
          ITEM,
          'text-text-secondary disabled:cursor-not-allowed disabled:text-text-disabled',
        )}
      >
        <Icon name="ChevronRight" className="size-5" />
      </button>
    </nav>
  )
}

export type LoadMoreProps = {
  /** 예: 12 / 48개 */
  countText: ReactNode
  onLoadMore: () => void
  loading?: boolean
  /** 다 불러왔으면 false로 숨긴다. */
  hasMore?: boolean
  label?: ReactNode
  className?: string
}

// 모바일 목록에서 페이지 번호 대신 쓰는 더보기 버튼.
export function LoadMore({
  countText,
  onLoadMore,
  loading = false,
  hasMore = true,
  label = '더보기',
  className,
}: LoadMoreProps) {
  if (!hasMore) {
    return null
  }
  return (
    <div className={cn('flex w-full flex-col items-center gap-2', className)}>
      <Button variant="secondary" loading={loading} onClick={onLoadMore} className="w-full">
        {label}
      </Button>
      <span className="text-caption text-text-tertiary">{countText}</span>
    </div>
  )
}
