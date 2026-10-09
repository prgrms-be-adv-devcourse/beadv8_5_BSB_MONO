import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { DatePicker } from './DatePicker'
import { nextRange, selectionText, toggleDate } from './selection'

const day = (d: number) => new Date(2026, 9, d) // 2026년 10월

describe('nextRange (시안의 기간 고르기 규칙)', () => {
  it('처음 누른 날이 시작일이다', () => {
    expect(nextRange(undefined, day(12))).toEqual({ from: day(12), to: undefined })
  })

  it('시작일 뒤를 누르면 종료일이 된다', () => {
    expect(nextRange({ from: day(12) }, day(16))).toEqual({ from: day(12), to: day(16) })
  })

  it('시작일보다 앞을 누르면 그날이 새 시작일이 된다', () => {
    expect(nextRange({ from: day(12) }, day(9))).toEqual({ from: day(9), to: undefined })
  })

  it('기간이 정해진 뒤 다시 누르면 처음부터 고른다', () => {
    expect(nextRange({ from: day(12), to: day(16) }, day(14))).toEqual({
      from: day(14),
      to: undefined,
    })
  })
})

describe('toggleDate', () => {
  it('누를 때마다 켜고 끈다', () => {
    const once = toggleDate(undefined, day(3))
    expect(toggleDate(once, day(5))).toEqual([day(3), day(5)])
    expect(toggleDate([day(3), day(5)], day(3))).toEqual([day(5)])
  })
})

describe('selectionText', () => {
  it('시안의 값 문구 형식으로 보여 준다', () => {
    expect(selectionText({ mode: 'single', selected: day(18) })).toBe('10월 18일 (일)')
    expect(selectionText({ mode: 'range', selected: { from: day(12), to: day(16) } })).toBe(
      '10월 12일 (월) ~ 10월 16일 (금)',
    )
    expect(selectionText({ mode: 'multiple', selected: [day(1), day(2), day(3)] })).toBe('3일 선택')
  })
})

describe('DatePicker', () => {
  const cell = (d: number) => screen.getByRole('button', { name: new RegExp(`10월 ${d}일`) })

  it('하루를 누르면 onSelect로 알리고 하단 문구가 바뀐다', async () => {
    const onSelect = vi.fn()
    render(
      <DatePicker
        mode="single"
        defaultMonth={day(1)}
        onSelect={onSelect}
        footer={{ onConfirm: () => {} }}
      />,
    )
    await userEvent.click(cell(18))

    expect(onSelect).toHaveBeenCalledWith(day(18))
    expect(screen.getByText('10월 18일 (일)')).toBeInTheDocument()
  })

  it('기간은 시안 규칙대로 고른다(앞을 누르면 새 시작일)', async () => {
    const onSelect = vi.fn()
    render(<DatePicker mode="range" defaultMonth={day(1)} onSelect={onSelect} />)
    await userEvent.click(cell(12))
    await userEvent.click(cell(9))
    await userEvent.click(cell(16))

    expect(onSelect).toHaveBeenLastCalledWith({ from: day(9), to: day(16) })
  })

  it('고를 수 없는 날은 누를 수 없다', async () => {
    const onSelect = vi.fn()
    render(
      <DatePicker
        mode="single"
        defaultMonth={day(1)}
        isDateDisabled={(date) => date < day(5)}
        onSelect={onSelect}
      />,
    )

    expect(cell(3)).toBeDisabled()
  })

  it('읽기 전용이면 눌러도 값이 바뀌지 않는다', async () => {
    const onSelect = vi.fn()
    render(
      <DatePicker
        mode="single"
        defaultMonth={day(1)}
        selected={day(18)}
        readOnly
        onSelect={onSelect}
      />,
    )
    await userEvent.click(cell(20))

    expect(onSelect).not.toHaveBeenCalled()
  })

  it('고를 수 있는 첫 달에서는 이전 달 버튼을 막는다', () => {
    render(<DatePicker mode="single" defaultMonth={day(1)} startMonth={day(1)} />)

    expect(screen.getByRole('button', { name: '이전 달' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '다음 달' })).toBeEnabled()
  })

  it('한 주 보기에서 방향키로 주 밖으로 나가면 다음 주로 넘어간다', async () => {
    render(<DatePicker mode="single" view="week" defaultMonth={day(31)} />)
    // 10월 31일(토)이 있는 주에서 오른쪽으로 가면 11월 1일(일)이 있는 다음 주가 된다
    cell(31).focus()
    await userEvent.keyboard('{ArrowRight}')

    expect(screen.getByRole('button', { name: /11월 1일/ })).toHaveFocus()
    expect(screen.getByRole('button', { name: /2026년 11월, 연·월 고르기/ })).toBeInTheDocument()
  })

  it('연·월을 누르면 휠이 열리고 화살표로 달을 바꾼다', async () => {
    render(<DatePicker mode="single" defaultMonth={day(1)} />)
    await userEvent.click(screen.getByRole('button', { name: /2026년 10월, 연·월 고르기/ }))
    screen.getByRole('listbox', { name: '월' }).focus()
    await userEvent.keyboard('{ArrowDown}')

    expect(screen.getByRole('button', { name: /2026년 11월, 연·월 고르기/ })).toHaveAttribute(
      'aria-expanded',
      'true',
    )
  })
})
