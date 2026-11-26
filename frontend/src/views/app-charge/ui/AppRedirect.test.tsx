import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { AppRedirect } from './AppRedirect'

describe('AppRedirect', () => {
  it('받은 앱 주소로 이동하고, 이동이 막혀도 누를 수 있는 링크를 둔다', () => {
    const url = 'crewrun://deposit/charge-return?code=UNKNOWN'
    const replace = vi.fn()
    vi.stubGlobal('location', { ...window.location, replace })

    render(<AppRedirect url={url} />)

    expect(replace).toHaveBeenCalledExactlyOnceWith(url)
    expect(screen.getByRole('link', { name: '앱으로 돌아가기' })).toHaveAttribute('href', url)
    vi.unstubAllGlobals()
  })
})
