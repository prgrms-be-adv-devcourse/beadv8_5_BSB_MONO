import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { Divider } from './Divider'

const meta = {
  title: 'Layout/Divider',
  component: Divider,
  argTypes: { type: { control: 'inline-radio', options: ['line', 'strong', 'section'] } },
} satisfies Meta<typeof Divider>

export default meta
type Story = StoryObj<typeof meta>

export const Types: Story = {
  render: () => (
    <div className="flex max-w-md flex-col gap-4 bg-bg-surface p-4">
      <p className="text-label-m">line</p>
      <Divider />
      <p className="text-label-m">strong</p>
      <Divider type="strong" />
      <p className="text-label-m">section (모바일 섹션 사이)</p>
      <Divider type="section" />
    </div>
  ),
}

/** 같은 줄 정보 사이 */
export const Vertical: Story = {
  render: () => (
    <div className="flex h-5 items-center gap-2 text-body-s text-text-secondary">
      <span>하프</span>
      <Divider orientation="vertical" />
      <span>2026.11.15</span>
      <Divider orientation="vertical" />
      <span>서울</span>
    </div>
  ),
}
