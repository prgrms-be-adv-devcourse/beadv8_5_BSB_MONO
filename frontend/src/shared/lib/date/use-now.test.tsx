import { renderHook } from '@testing-library/react'
import { renderToString } from 'react-dom/server'
import { describe, expect, it } from 'vitest'

import { useNow } from './use-now'

function Probe() {
  return <span>{String(useNow())}</span>
}

describe('useNow', () => {
  it('서버 렌더에서는 시각을 읽지 않아 undefined다 (hydration 값이 어긋나지 않는다)', () => {
    expect(renderToString(<Probe />)).toContain('undefined')
  })

  it('브라우저에서는 마운트 뒤 현재 시각을 돌려준다', () => {
    const before = Date.now()
    const { result } = renderHook(() => useNow())
    expect(result.current).toBeInstanceOf(Date)
    expect(result.current!.getTime()).toBeGreaterThanOrEqual(before)
  })
})
