import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { Chip } from './Chip'

describe('Chip', () => {
  // 변형 클래스를 만드는 방식을 바꿔도 결과 className이 그대로여야 한다
  it.each([false, true])('selected=%s 일 때 className', (selected) => {
    render(<Chip selected={selected}>5K</Chip>)
    const chip = screen.getByRole('button', { name: '5K' })
    expect(chip.className).toMatchSnapshot()
    expect(chip).toHaveAttribute('aria-pressed', String(selected))
  })

  it('넘긴 className이 뒤에 붙는다', () => {
    render(<Chip className="w-full">5K</Chip>)
    expect(screen.getByRole('button').className).toMatch(/ w-full$/)
  })
})
