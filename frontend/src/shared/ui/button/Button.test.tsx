import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { Button, buttonClassName, type ButtonSize, type ButtonVariant } from './Button'

const VARIANTS: ButtonVariant[] = ['primary', 'secondary', 'ghost', 'text', 'danger', 'accent']
const SIZES: ButtonSize[] = ['s', 'm', 'l']

describe('buttonClassName', () => {
  // 변형 클래스를 만드는 방식을 바꿔도 결과 className이 그대로여야 한다
  it.each(VARIANTS.flatMap((variant) => SIZES.map((size) => ({ variant, size }))))(
    '$variant · $size 의 className',
    ({ variant, size }) => {
      expect(buttonClassName({ variant, size })).toMatchSnapshot()
    },
  )

  it('기본값은 primary · m이고 넘긴 className이 뒤에 붙는다', () => {
    expect(buttonClassName()).toBe(buttonClassName({ variant: 'primary', size: 'm' }))
    expect(buttonClassName({ className: 'w-full' })).toMatch(/ w-full$/)
  })
})

describe('Button', () => {
  it('loading이면 눌러도 onClick을 부르지 않는다', () => {
    let clicks = 0
    render(
      <Button loading onClick={() => clicks++}>
        저장
      </Button>,
    )
    screen.getByRole('button', { name: '저장' }).click()
    expect(clicks).toBe(0)
  })
})
