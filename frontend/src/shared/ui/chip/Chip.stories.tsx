import type { Meta, StoryObj } from '@storybook/nextjs-vite'
import { useState } from 'react'
import { fn } from 'storybook/test'

import { Chip } from './Chip'

const meta = {
  title: 'Actions/Chip',
  component: Chip,
  args: { children: '10K', onClick: fn() },
} satisfies Meta<typeof Chip>

export default meta
type Story = StoryObj<typeof meta>

export const Playground: Story = {}

export const States: Story = {
  render: () => (
    <div className="flex gap-2">
      <Chip>기본</Chip>
      <Chip selected>선택됨</Chip>
      <Chip disabled>비활성</Chip>
      <Chip selected disabled>
        선택·비활성
      </Chip>
    </div>
  ),
}

/** 종목처럼 하나만 고르는 예. 눌러서 바뀌는지 확인한다. */
export const SingleSelect: Story = {
  render: function Render() {
    const courses = ['5K', '10K', '하프', '풀']
    const [value, setValue] = useState('10K')
    return (
      <div className="flex flex-wrap gap-2">
        {courses.map((course) => (
          <Chip key={course} selected={value === course} onClick={() => setValue(course)}>
            {course}
          </Chip>
        ))}
      </div>
    )
  },
}
