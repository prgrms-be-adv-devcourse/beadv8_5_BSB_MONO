import { act, renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useSearchParamsState } from './use-search-params-state'

const replace = vi.fn()
let search = ''

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace }),
  usePathname: () => '/races',
  useSearchParams: () => new URLSearchParams(search),
}))

const defaults = { sort: 'DEADLINE', tab: 'all' }
const allowed = { sort: ['DEADLINE', 'PRICE'] }

describe('useSearchParamsState', () => {
  beforeEach(() => {
    replace.mockReset()
    search = ''
  })

  it('URL에 값이 없으면 기본값을 읽는다', () => {
    const { result } = renderHook(() => useSearchParamsState(defaults, allowed))
    expect(result.current[0]).toEqual(defaults)
  })

  it('URL의 값을 읽는다', () => {
    search = 'sort=PRICE&tab=done'
    const { result } = renderHook(() => useSearchParamsState(defaults, allowed))
    expect(result.current[0]).toEqual({ sort: 'PRICE', tab: 'done' })
  })

  it('허용 목록 밖의 값은 기본값으로 읽는다', () => {
    search = 'sort=HACKED'
    const { result } = renderHook(() => useSearchParamsState(defaults, allowed))
    expect(result.current[0].sort).toBe('DEADLINE')
  })

  it('값을 쓰면 스크롤 없이 URL을 바꾸고 다른 키는 그대로 둔다', () => {
    search = 'tab=done'
    const { result } = renderHook(() => useSearchParamsState(defaults, allowed))
    act(() => result.current[1]({ sort: 'PRICE' }))
    expect(replace).toHaveBeenCalledWith('/races?tab=done&sort=PRICE', { scroll: false })
  })

  it('기본값과 같으면 URL에서 키를 지운다', () => {
    search = 'sort=PRICE&tab=done'
    const { result } = renderHook(() => useSearchParamsState(defaults, allowed))
    act(() => result.current[1]({ sort: 'DEADLINE' }))
    expect(replace).toHaveBeenCalledWith('/races?tab=done', { scroll: false })
    act(() => result.current[1]({ tab: 'all' }))
    expect(replace).toHaveBeenLastCalledWith('/races?sort=PRICE', { scroll: false })
  })

  it('키가 모두 없어지면 물음표 없이 경로만 쓴다', () => {
    search = 'sort=PRICE'
    const { result } = renderHook(() => useSearchParamsState(defaults, allowed))
    act(() => result.current[1]({ sort: 'DEADLINE' }))
    expect(replace).toHaveBeenCalledWith('/races', { scroll: false })
  })
})
