import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { StatTile } from './StatTile'

const meta = {
  title: 'Display/StatTile',
  component: StatTile,
  args: { label: '결제 완료', value: '5', unit: '명', caption: '전체 8명 중 62%', tone: 'surface' },
  argTypes: { tone: { control: 'inline-radio', options: ['surface', 'hero', 'accent'] } },
  decorators: [
    (Story) => (
      <div className="max-w-48">
        <Story />
      </div>
    ),
  ],
} satisfies Meta<typeof StatTile>

export default meta
type Story = StoryObj<typeof meta>

export const Playground: Story = {}

export const Tones: Story = {
  decorators: [
    (Story) => (
      <div className="rounded-lg bg-bg-hero p-4">
        <Story />
      </div>
    ),
  ],
  render: (args) => (
    <div className="grid grid-cols-3 gap-2">
      <StatTile {...args} tone="surface" />
      <StatTile {...args} tone="hero" />
      <StatTile {...args} tone="accent" label="할인" value="10" unit="%" caption="조건 달성" />
    </div>
  ),
}
