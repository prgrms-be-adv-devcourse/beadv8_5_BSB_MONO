'use client'

import { cn } from '@/shared/lib'
import { FOCUS_RING, Icon } from '@/shared/ui'

// 피그마 「검색 시작」의 최근 검색어: 하나씩 또는 전체 삭제.
// 인기 검색어 · 맞춤 추천 대회는 서버 API가 없어 그리지 않는다.
export function RecentSearches({
  terms,
  onPick,
  onRemove,
  onClear,
}: {
  terms: string[]
  onPick: (term: string) => void
  onRemove: (term: string) => void
  onClear: () => void
}) {
  if (terms.length === 0) return null

  return (
    <section
      aria-labelledby="recent-searches"
      // 누르는 동안 입력창이 포커스를 잃어 화면이 먼저 닫히지 않게 한다
      onMouseDown={(e) => e.preventDefault()}
      className="flex flex-col gap-3"
    >
      <div className="flex items-center justify-between">
        <h2 id="recent-searches" className="text-title3">
          최근 검색어
        </h2>
        <button
          type="button"
          onClick={onClear}
          className={cn('rounded-sm text-body2-regular text-text-tertiary', FOCUS_RING)}
        >
          전체 삭제
        </button>
      </div>
      <ul className="flex flex-wrap gap-2">
        {terms.map((term) => (
          <li
            key={term}
            className="flex items-center gap-1 rounded-full bg-bg-subtle py-1.5 pr-2 pl-3 text-body2-medium text-text-secondary"
          >
            <button
              type="button"
              onClick={() => onPick(term)}
              className={cn('rounded-sm', FOCUS_RING)}
            >
              {term}
            </button>
            <button
              type="button"
              aria-label={`${term} 삭제`}
              onClick={() => onRemove(term)}
              className={cn('rounded-full', FOCUS_RING)}
            >
              <Icon name="X" className="size-4" />
            </button>
          </li>
        ))}
      </ul>
    </section>
  )
}
