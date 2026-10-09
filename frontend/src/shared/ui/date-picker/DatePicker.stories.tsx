import type { Meta, StoryObj } from '@storybook/nextjs-vite'
import { addDays, format, isBefore, startOfToday } from 'date-fns'
import { useState } from 'react'
import { fn } from 'storybook/test'

import { DatePicker } from './DatePicker'
import type { DateRange } from './selection'

// DatePickerProps는 mode별 유니온이라 args 대신 render로 적는다.
const meta = {
  title: 'Inputs/DatePicker',
  component: DatePicker,
  decorators: [
    (Story) => (
      <div className="max-w-sm">
        <Story />
      </div>
    ),
  ],
} satisfies Meta

export default meta
type Story = StoryObj

const today = startOfToday()
const isPast = (date: Date) => isBefore(date, today)
const onSelect = fn()

/** 화면 안(Inline)에 두고 누르는 즉시 반영한다. 연·월을 누르면 휠이 열린다. */
export const Single: Story = {
  render: () => (
    <DatePicker mode="single" defaultSelected={addDays(today, 3)} onSelect={onSelect} />
  ),
}

/** 기간 고르기. 첫 클릭이 시작, 두 번째 클릭이 끝이다. */
export const Range: Story = {
  render: () => (
    <DatePicker
      mode="range"
      defaultSelected={{ from: addDays(today, 2), to: addDays(today, 6) }}
      onSelect={onSelect}
    />
  ),
}

export const Multiple: Story = {
  render: () => (
    <DatePicker
      mode="multiple"
      defaultSelected={[addDays(today, 1), addDays(today, 4), addDays(today, 8)]}
      onSelect={onSelect}
    />
  ),
}

/** 지난 날은 흐린 글자에 취소선으로 막고, 이전 달 버튼도 막는다. */
export const DisabledPast: Story = {
  render: () => (
    <DatePicker mode="single" isDateDisabled={isPast} startMonth={today} onSelect={onSelect} />
  ),
}

/** 이미 확정된 값. 톤 낮춘 코발트로 보이고 눌러도 반응하지 않는다. */
export const ReadOnly: Story = {
  render: () => <DatePicker mode="single" defaultSelected={addDays(today, 5)} readOnly />,
}

/** 좁은 자리에 한 주만 보여 준다. 키보드 좌우·위아래로 옮겨진다. */
export const Week: Story = {
  render: () => (
    <DatePicker mode="single" view="week" defaultSelected={today} onSelect={onSelect} />
  ),
}

/** 팝오버·시트 안에서는 하단 '선택 완료'로 확정한다. */
export const WithFooter: Story = {
  render: function Render() {
    const [range, setRange] = useState<DateRange | undefined>()
    const [confirmed, setConfirmed] = useState('')
    const text = (date?: Date) => (date ? format(date, 'yyyy.MM.dd') : '')
    return (
      <div className="flex flex-col gap-2">
        <DatePicker
          mode="range"
          selected={range}
          onSelect={setRange}
          isDateDisabled={isPast}
          footer={{ onConfirm: () => setConfirmed(`${text(range?.from)} ~ ${text(range?.to)}`) }}
        />
        <p className="text-body-s text-text-secondary">확정한 값: {confirmed || '없음'}</p>
      </div>
    )
  },
}
