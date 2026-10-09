import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { DatePicker } from './DatePicker'
import { dragRange, nextRange, selectionText, toggleDate } from './selection'

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

describe('dragRange (끌어서 기간 고르기)', () => {
  it('누른 날과 지금 손가락이 있는 날 중 앞이 시작, 뒤가 종료다', () => {
    expect(dragRange(day(12), day(16))).toEqual({ from: day(12), to: day(16) })
    expect(dragRange(day(12), day(9))).toEqual({ from: day(9), to: day(12) })
  })

  it('누른 날로 돌아오면 시작일만 남는다', () => {
    expect(dragRange(day(12), day(12))).toEqual({ from: day(12), to: undefined })
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

  describe('끌어서 기간 고르기', () => {
    // jsdom은 화면 배치를 계산하지 않아서, 손가락 아래 칸을 돌려주는 elementFromPoint를 흉내 낸다
    function pointAt(d: number) {
      document.elementFromPoint = () => cell(d)
    }
    afterEach(() => {
      // @ts-expect-error 흉내 낸 함수를 지운다
      delete document.elementFromPoint
    })

    function drag(from: number, ...through: number[]) {
      fireEvent.pointerDown(cell(from), { button: 0, pointerId: 1 })
      for (const d of through) {
        pointAt(d)
        fireEvent.pointerMove(window, { pointerId: 1 })
      }
      fireEvent.pointerUp(window, { pointerId: 1 })
      // 손을 뗀 칸에서 브라우저가 보내는 click
      fireEvent.click(cell(through.at(-1) ?? from))
    }

    it('누른 날에서 끌어 놓은 날까지가 기간이 된다', () => {
      const onSelect = vi.fn()
      render(<DatePicker mode="range" defaultMonth={day(1)} onSelect={onSelect} />)
      drag(12, 13, 14, 16)

      expect(onSelect).toHaveBeenCalledTimes(1)
      expect(onSelect).toHaveBeenCalledWith({ from: day(12), to: day(16) })
    })

    it('앞쪽으로 끌면 놓은 날이 시작일이 된다', () => {
      const onSelect = vi.fn()
      render(<DatePicker mode="range" defaultMonth={day(1)} onSelect={onSelect} />)
      drag(16, 14, 9)

      expect(onSelect).toHaveBeenCalledWith({ from: day(9), to: day(16) })
    })

    it('끄는 동안에는 기간을 미리 보여 주고 onSelect는 놓을 때 한 번만 부른다', () => {
      const onSelect = vi.fn()
      render(
        <DatePicker
          mode="range"
          defaultMonth={day(1)}
          onSelect={onSelect}
          footer={{ onConfirm: () => {} }}
        />,
      )
      fireEvent.pointerDown(cell(12), { button: 0, pointerId: 1 })
      pointAt(15)
      fireEvent.pointerMove(window, { pointerId: 1 })

      expect(screen.getByText('10월 12일 (월) ~ 10월 15일 (목)')).toBeInTheDocument()
      expect(onSelect).not.toHaveBeenCalled()
    })

    it('움직이지 않고 떼면 지금처럼 한 번 누른 것으로 친다', async () => {
      const onSelect = vi.fn()
      render(<DatePicker mode="range" defaultMonth={day(1)} onSelect={onSelect} />)
      drag(12)

      expect(onSelect).toHaveBeenCalledWith({ from: day(12), to: undefined })
    })

    it('끄는 중에 고를 수 없는 날은 건너뛰고 직전 날을 유지한다', () => {
      const onSelect = vi.fn()
      render(
        <DatePicker
          mode="range"
          defaultMonth={day(1)}
          isDateDisabled={(date) => date.getTime() === day(17).getTime()}
          onSelect={onSelect}
        />,
      )
      fireEvent.pointerDown(cell(12), { button: 0, pointerId: 1 })
      for (const d of [15, 17]) {
        pointAt(d)
        fireEvent.pointerMove(window, { pointerId: 1 })
      }
      fireEvent.pointerUp(window, { pointerId: 1 })

      expect(onSelect).toHaveBeenCalledWith({ from: day(12), to: day(15) })
    })

    it('읽기 전용이면 끌어도 바뀌지 않는다', () => {
      const onSelect = vi.fn()
      render(<DatePicker mode="range" defaultMonth={day(1)} readOnly onSelect={onSelect} />)
      drag(12, 16)

      expect(onSelect).not.toHaveBeenCalled()
    })
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

  describe('연·월 휠을 마우스로 끌기', () => {
    // 한 줄 높이 44. 위로 끌면 다음 값, 아래로 끌면 이전 값으로 넘어간다.
    function dragWheel(name: '연도' | '월', dy: number) {
      const wheel = screen.getByRole('listbox', { name })
      fireEvent.pointerDown(wheel, { button: 0, pointerId: 1, pointerType: 'mouse', clientY: 300 })
      fireEvent.pointerMove(window, { pointerId: 1, pointerType: 'mouse', clientY: 300 + dy })
      fireEvent.pointerUp(window, { pointerId: 1, pointerType: 'mouse', clientY: 300 + dy })
    }
    const header = (label: string) =>
      screen.getByRole('button', { name: new RegExp(`${label}, 연·월 고르기`) })

    it('위로 두 줄 끌면 두 달 뒤로 넘어간다', async () => {
      render(<DatePicker mode="single" defaultMonth={day(1)} />)
      await userEvent.click(header('2026년 10월'))
      dragWheel('월', -88)

      expect(header('2026년 12월')).toBeInTheDocument()
    })

    it('아래로 끌면 앞 연도로 넘어간다', async () => {
      render(<DatePicker mode="single" defaultMonth={day(1)} />)
      await userEvent.click(header('2026년 10월'))
      dragWheel('연도', 44)

      expect(header('2025년 10월')).toBeInTheDocument()
    })

    it('반 줄보다 적게 끌면 그대로다', async () => {
      render(<DatePicker mode="single" defaultMonth={day(1)} />)
      await userEvent.click(header('2026년 10월'))
      dragWheel('월', -15)

      expect(header('2026년 10월')).toBeInTheDocument()
    })

    it('끈 뒤 손을 뗀 줄의 click으로 값이 또 바뀌지 않는다', async () => {
      render(<DatePicker mode="single" defaultMonth={day(1)} />)
      await userEvent.click(header('2026년 10월'))
      dragWheel('월', -44)
      fireEvent.click(screen.getByRole('option', { name: '10월' }))

      expect(header('2026년 11월')).toBeInTheDocument()
    })
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
