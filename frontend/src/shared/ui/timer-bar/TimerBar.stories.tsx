import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { TimerBar } from './TimerBar'

const meta = {
  title: 'Display/TimerBar',
  component: TimerBar,
  args: { message: '차례가 됐어요. 시간 안에 신청과 결제를 마쳐 주세요', remainingSeconds: 583 },
  argTypes: {
    remainingSeconds: { control: { type: 'number', min: 0, max: 600 } },
  },
} satisfies Meta<typeof TimerBar>

export default meta
type Story = StoryObj<typeof meta>

export const Playground: Story = {}
