import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { LineField } from './parts'

describe('LineField', () => {
  it('조건 확인 줄(비밀번호 규칙 등)을 입력 칸의 설명으로 연결한다', () => {
    render(
      <LineField
        aria-label="비밀번호"
        hints={[
          { ok: true, text: '영문 포함' },
          { ok: false, text: '숫자 포함' },
        ]}
      />,
    )
    const input = screen.getByLabelText('비밀번호')
    expect(input).toHaveAccessibleDescription(/영문 포함/)
    expect(input).toHaveAccessibleDescription(/숫자 포함/)
  })

  it('지우기 버튼은 누르는 영역이 24px 이상이다', () => {
    render(<LineField aria-label="이메일" onClear={vi.fn()} />)
    expect(screen.getByRole('button', { name: '지우기' })).toHaveClass('size-6')
  })
})
