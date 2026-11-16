import { useMemo, useSyncExternalStore } from 'react'
import { vi } from 'vitest'

// 주소의 쿼리를 들고 있는 가짜 next/navigation. router.replace로 바꾼 쿼리를 useSearchParams가 다시 읽는다.
// 화면을 지우고 다시 그려도(상세에 갔다 뒤로 오기) 값이 남는다. 쓰는 법:
//   vi.mock('next/navigation', async () => (await import('@/shared/testing')).navigationMock('/races'))
//   beforeEach(() => resetNavigationMock())
const state = { search: '', listeners: new Set<() => void>() }

export function resetNavigationMock(search = '') {
  state.search = search
}

export function currentSearch() {
  return state.search
}

export function navigationMock(pathname: string) {
  return {
    useRouter: () => ({
      push: vi.fn(),
      back: vi.fn(),
      refresh: vi.fn(),
      replace: (url: string) => {
        state.search = url.split('?')[1] ?? ''
        state.listeners.forEach((listen) => listen())
      },
    }),
    usePathname: () => pathname,
    useSearchParams: () => {
      const search = useSyncExternalStore(
        (listen) => {
          state.listeners.add(listen)
          return () => state.listeners.delete(listen)
        },
        () => state.search,
        () => state.search,
      )
      return useMemo(() => new URLSearchParams(search), [search])
    },
  }
}
