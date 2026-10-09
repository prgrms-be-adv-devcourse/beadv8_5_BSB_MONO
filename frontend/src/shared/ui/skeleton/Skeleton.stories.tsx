import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { Skeleton } from './Skeleton'

const meta = {
  title: 'Feedback/Skeleton',
  component: Skeleton,
} satisfies Meta<typeof Skeleton>

export default meta
type Story = StoryObj<typeof meta>

export const Shapes: Story = {
  render: () => (
    <div className="flex items-center gap-4">
      <Skeleton shape="circle" className="size-10" />
      <Skeleton shape="rect" className="h-20 w-32" />
      <Skeleton shape="text" className="w-40" />
    </div>
  ),
}

/** 실제 화면 배치대로 조합한 대회 카드 자리 */
export const RaceCard: Story = {
  render: () => (
    <div className="flex max-w-xs flex-col gap-3 rounded-lg bg-bg-surface p-4">
      <Skeleton shape="rect" className="aspect-video w-full" />
      <Skeleton className="w-3/4" />
      <Skeleton className="w-1/2" />
      <div className="flex items-center gap-2">
        <Skeleton shape="circle" className="size-6" />
        <Skeleton className="w-20" />
      </div>
    </div>
  ),
}
