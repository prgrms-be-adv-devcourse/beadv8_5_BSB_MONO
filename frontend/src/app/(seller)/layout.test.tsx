import { render } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import SellerLayout from './layout'

vi.mock('@/widgets/console-sidebar', () => ({ ConsoleSidebar: () => <nav /> }))

describe('SellerLayout', () => {
  // 폭 1280 이상 글자 확대(숫자 40·제목 22·굵은 본문 18)를 끄고 시안 값을 쓰게 한다
  it('판매자 글자 크기 고정 클래스를 붙인다', () => {
    const { container } = render(<SellerLayout>본문</SellerLayout>)
    expect(container.firstElementChild?.className).toMatch(/\bconsole-type\b/)
  })
})
