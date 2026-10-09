import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { Stepper } from './Stepper'

const meta = {
  title: 'Navigation/Stepper',
  component: Stepper,
  args: { steps: ['종목 선택', '참가자 정보', '기념품', '결제'], current: 1 },
  argTypes: { current: { control: { type: 'range', min: 0, max: 3 } } },
  decorators: [
    (Story) => (
      <div className="max-w-md">
        <Story />
      </div>
    ),
  ],
} satisfies Meta<typeof Stepper>

export default meta
type Story = StoryObj<typeof meta>

export const Playground: Story = {}

export const First: Story = { args: { current: 0 } }

export const Last: Story = { args: { current: 3 } }
