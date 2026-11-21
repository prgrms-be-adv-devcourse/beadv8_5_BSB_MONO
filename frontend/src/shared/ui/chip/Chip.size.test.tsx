import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { Chip } from './Chip'

// 판매자 시안: 칩 높이 40, 좌우 16, 선택 안 한 칩은 그림자 없이 1px 선
describe('Chip 시안 값', () => {
  it('높이 40, 좌우 16', () => {
    render(<Chip>5K</Chip>)
    const cls = screen.getByRole('button').className
    expect(cls).toMatch(/\bh-10\b/)
    expect(cls).toMatch(/\bpx-4\b/)
  })

  it('선택 안 한 칩은 그림자 대신 1px 선을 쓴다', () => {
    render(<Chip>5K</Chip>)
    const cls = screen.getByRole('button').className
    expect(cls).not.toMatch(/shadow-field/)
    expect(cls).toMatch(/inset-ring-border-default/)
  })
})
