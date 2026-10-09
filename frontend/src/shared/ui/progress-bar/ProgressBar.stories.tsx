import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { ProgressBar } from './ProgressBar'

const meta = {
  title: 'Display/ProgressBar',
  component: ProgressBar,
  args: { value: 62.5, label: '크루 결제', valueText: '5/8명', size: 'm', tone: 'brand' },
  argTypes: {
    value: { control: { type: 'range', min: 0, max: 100 } },
    size: { control: 'inline-radio', options: ['s', 'm'] },
    tone: { control: 'inline-radio', options: ['brand', 'onHero'] },
  },
  decorators: [
    (Story) => (
      <div className="max-w-sm">
        <Story />
      </div>
    ),
  ],
} satisfies Meta<typeof ProgressBar>

export default meta
type Story = StoryObj<typeof meta>

export const Playground: Story = {}

/** 100일 때만 초록으로 바뀐다. */
export const Complete: Story = { args: { value: 100, valueText: '8/8명' } }

export const Small: Story = { args: { size: 's', label: undefined, valueText: undefined } }

export const OnHero: Story = {
  args: { tone: 'onHero' },
  decorators: [
    (Story) => (
      <div className="rounded-lg bg-bg-hero p-4">
        <Story />
      </div>
    ),
  ],
}
