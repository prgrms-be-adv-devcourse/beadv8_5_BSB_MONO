import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { digitsOnly } from './input-filter'
import { TextField } from './TextField'

describe('입력 칸 filter', () => {
  it('커서 뒤에서 잘려 나간 글자는 커서 위치에 영향을 주지 않는다', async () => {
    const user = userEvent.setup()
    render(
      <TextField
        aria-label="생년월일"
        defaultValue="19950302"
        filter={(value) => digitsOnly(value).slice(0, 8)}
      />,
    )
    const input = screen.getByLabelText('생년월일') as HTMLInputElement
    await user.click(input)
    input.setSelectionRange(0, 0)
    await user.keyboard('9')
    expect(input).toHaveValue('91995030')
    expect(input.selectionStart).toBe(1)
  })

  it('커서 앞에서 빠진 글자 수만큼만 커서를 당긴다', async () => {
    const user = userEvent.setup()
    render(<TextField aria-label="번호" defaultValue="13" filter={digitsOnly} />)
    const input = screen.getByLabelText('번호') as HTMLInputElement
    await user.click(input)
    input.setSelectionRange(1, 1)
    await user.keyboard('a')
    expect(input).toHaveValue('13')
    expect(input.selectionStart).toBe(1)
  })

  it('한글 조합 중에는 거르지 않고, 조합이 끝나면 한 번 거르고 onChange로 알린다', () => {
    const seen: string[] = []
    const onChange = vi.fn((event: { target: { value: string } }) => seen.push(event.target.value))
    render(<TextField aria-label="번호" filter={digitsOnly} onChange={onChange} />)
    const input = screen.getByLabelText('번호') as HTMLInputElement

    fireEvent.compositionStart(input)
    fireEvent.input(input, { target: { value: '1ㅎ' }, isComposing: true })
    expect(input).toHaveValue('1ㅎ')

    fireEvent.input(input, { target: { value: '1한' }, isComposing: true })
    expect(input).toHaveValue('1한')

    fireEvent.compositionEnd(input)
    expect(input).toHaveValue('1')
    expect(seen.at(-1)).toBe('1')
    expect(screen.getAllByText('숫자만 입력할 수 있어요').length).toBeGreaterThan(0)
  })
})
