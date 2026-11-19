import { zodResolver } from '@hookform/resolvers/zod'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { format, subYears } from 'date-fns'
import { useForm } from 'react-hook-form'
import { describe, expect, it, vi } from 'vitest'
import { z } from 'zod'

import { pickDate } from '@/shared/testing'

import { BirthDateField } from './BirthDateField'

// 오늘 기준 만 13세 생일(오늘로부터 13년 전)
const THIRTEEN = subYears(new Date(), 13)
const THIRTEEN_TEXT = format(THIRTEEN, 'yyyy-MM-dd')

describe('BirthDateField', () => {
  it('나이 제한을 끄면 오늘 기준 만 13세 생일도 고를 수 있다', async () => {
    const onChange = vi.fn()
    render(<BirthDateField label="생년월일" name="birth" onChange={onChange} restrictAge={false} />)

    await pickDate(userEvent.setup(), '생년월일', THIRTEEN_TEXT)

    expect(onChange).toHaveBeenCalledWith({ target: { name: 'birth', value: THIRTEEN_TEXT } })
  })

  it('기본값은 만 14세 미만을 막는다', async () => {
    const user = userEvent.setup()
    render(<BirthDateField label="생년월일" name="birth" onChange={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: /^생년월일/ }))
    const popup = await screen.findByRole('dialog', { name: '생년월일 고르기' })
    await user.click(within(popup).getByRole('button', { name: /연·월 고르기/ }))
    // 만 14세가 안 되는 해(만 13세 생일의 해)는 연·월 휠에 나오지 않는다
    expect(
      within(popup).queryByRole('option', { name: `${THIRTEEN.getFullYear()}년` }),
    ).not.toBeInTheDocument()
  })

  it('비운 채 제출하면 열기 버튼으로 포커스가 간다', async () => {
    const schema = z.object({ birth: z.string().min(1, '생년월일을 입력해 주세요.') })
    function Form() {
      const { register, handleSubmit, formState } = useForm({
        resolver: zodResolver(schema),
        defaultValues: { birth: '' },
      })
      return (
        <form onSubmit={handleSubmit(() => {})}>
          <BirthDateField
            label="생년월일"
            error={formState.errors.birth?.message}
            {...register('birth')}
          />
          <button type="submit">제출</button>
        </form>
      )
    }
    const user = userEvent.setup()
    render(<Form />)

    await user.click(screen.getByRole('button', { name: '제출' }))

    expect(await screen.findByText('생년월일을 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^생년월일/ })).toHaveFocus()
  })
})
