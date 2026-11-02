import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, onTestFinished, vi } from 'vitest'

import { dateTimeText, formatTimeTyping, padTime, rangeDateText } from './date-time'
import { DateTimeField } from './DateTimeField'

const day = (d: number, h = 0, m = 0) => new Date(2026, 9, d, h, m) // 2026년 10월

describe('시간 칸 글자', () => {
  it('숫자만 치면 두 자리 뒤에 콜론을 넣는다', () => {
    expect(formatTimeTyping('18')).toBe('18')
    expect(formatTimeTyping('183')).toBe('18:3')
    expect(formatTimeTyping('1830')).toBe('18:30')
    expect(formatTimeTyping('183045')).toBe('18:30')
  })

  it('콜론을 직접 치면 그대로 두고 숫자 아닌 글자는 뺀다', () => {
    expect(formatTimeTyping('9:30')).toBe('9:30')
    expect(formatTimeTyping('1a:3b0')).toBe('1:30')
  })

  it('칸을 떠날 때 한 자리 시를 두 자리로 채운다', () => {
    expect(padTime('9:30')).toBe('09:30')
    expect(padTime('18:30')).toBe('18:30')
  })

  it('폼 글자는 날짜와 시간을 띄어 쓴다', () => {
    expect(dateTimeText(day(31), '18:00')).toBe('2026-10-31 18:00')
    expect(dateTimeText(undefined, '18:00')).toBe(' 18:00')
    expect(dateTimeText(undefined, '')).toBe('')
  })
})

it('기간 글자는 M/d (요일) ~ M/d (요일)', () => {
  expect(rangeDateText({ from: day(14), to: day(31) })).toBe('10/14 (수) ~ 10/31 (토)')
})

describe('DateTimeField', () => {
  it('달력에서 날짜를 고르고 선택 완료를 누르면 시간을 붙여 알린다', async () => {
    const onValueChange = vi.fn()
    render(
      <DateTimeField
        mode="single"
        label="대회 일시"
        defaultValue={day(18, 8, 0)}
        onValueChange={onValueChange}
      />,
    )
    await userEvent.click(screen.getByRole('button', { name: /대회 일시 2026-10-18/ }))
    await userEvent.click(screen.getByRole('button', { name: /10월 20일/ }))
    await userEvent.click(screen.getByRole('button', { name: '선택 완료' }))

    expect(onValueChange).toHaveBeenLastCalledWith('2026-10-20 08:00')
    expect(screen.getByRole('button', { name: /2026-10-20 \(화\)/ })).toBeInTheDocument()
  })

  it('withTime={false}면 시간 칸 없이 날짜만 알린다', async () => {
    // 고른 값이 없으면 달력이 오늘이 있는 달로 열린다. 10월 날짜를 누르려고 오늘을 10월로 고정한다
    // (Date만 바꾸고 타이머는 그대로 둬 userEvent가 기다리는 데 영향이 없게 한다)
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(day(18, 12, 0))
    onTestFinished(() => {
      vi.useRealTimers()
    })
    const onValueChange = vi.fn()
    render(
      <DateTimeField
        mode="range"
        withTime={false}
        label="접수 기간"
        onValueChange={onValueChange}
      />,
    )
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /접수 기간/ }))
    await userEvent.click(screen.getByRole('button', { name: /10월 20일/ }))
    await userEvent.click(screen.getByRole('button', { name: /10월 24일/ }))
    await userEvent.click(screen.getByRole('button', { name: '선택 완료' }))

    expect(onValueChange).toHaveBeenLastCalledWith({ from: '2026-10-20', to: '2026-10-24' })
  })

  it('선택 완료 없이 닫으면 고른 날짜를 버린다', async () => {
    const onValueChange = vi.fn()
    render(
      <DateTimeField
        mode="single"
        label="대회 일시"
        defaultValue={day(18, 8, 0)}
        onValueChange={onValueChange}
      />,
    )
    await userEvent.click(screen.getByRole('button', { name: /대회 일시/ }))
    await userEvent.click(screen.getByRole('button', { name: /10월 20일/ }))
    await userEvent.keyboard('{Escape}')

    expect(onValueChange).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: /2026-10-18/ })).toBeInTheDocument()
  })

  it('기간은 시작·끝 시간 칸이 따로 있다', () => {
    render(
      <DateTimeField
        mode="range"
        label="접수 기간"
        defaultValue={{ from: day(14, 10, 0), to: day(31, 18, 0) }}
      />,
    )
    expect(screen.getByRole('textbox', { name: '접수 기간 시작 시간' })).toHaveValue('10:00')
    expect(screen.getByRole('textbox', { name: '접수 기간 끝 시간' })).toHaveValue('18:00')
  })

  it('시간 칸에 숫자를 치면 콜론을 넣어 알린다', async () => {
    const onValueChange = vi.fn()
    render(
      <DateTimeField
        mode="single"
        label="대회 일시"
        defaultValue={day(18, 8, 0)}
        onValueChange={onValueChange}
      />,
    )
    const time = screen.getByRole('textbox', { name: '대회 일시 시간' })
    await userEvent.clear(time)
    await userEvent.type(time, '1830')

    expect(time).toHaveValue('18:30')
    expect(time).toHaveAttribute('inputmode', 'numeric')
    expect(onValueChange).toHaveBeenLastCalledWith('2026-10-18 18:30')
  })
})
