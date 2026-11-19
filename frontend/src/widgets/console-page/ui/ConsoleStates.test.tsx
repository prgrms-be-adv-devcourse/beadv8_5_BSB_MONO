import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { ConsoleLoading } from './ConsoleStates'

vi.mock('next/navigation', () => ({ useRouter: () => ({ refresh: vi.fn() }) }))

describe('ConsoleLoading', () => {
  it('제목 막대·칩 막대 3개·표 줄 6개를 그리고 불러오는 중으로 읽힌다', () => {
    const { container } = render(<ConsoleLoading />)
    expect(screen.getByRole('status', { name: '불러오는 중' })).toBeInTheDocument()
    expect(container.querySelectorAll('.rounded-full')).toHaveLength(3)
    expect(container.querySelectorAll('.border-b')).toHaveLength(6)
  })
})
