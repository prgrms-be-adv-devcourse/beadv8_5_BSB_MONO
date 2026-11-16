import type { Meta, StoryObj } from '@storybook/nextjs-vite'
import { useState } from 'react'

import { Segmented, type SegmentedProps } from './Segmented'

const meta = {
  title: 'Navigation/Segmented',
  component: Segmented,
} satisfies Meta<typeof Segmented>

export default meta
type Story = Omit<StoryObj<typeof meta>, 'args'>

type Tab = 'all' | 'paid' | 'canceled'

const ITEMS = [
  { value: 'all', label: '전체', count: 12 },
  { value: 'paid', label: '결제 완료', count: 9 },
  { value: 'canceled', label: '취소', count: 3 },
] satisfies SegmentedProps<Tab>['items']

function Demo(props: Pick<SegmentedProps<Tab>, 'layout' | 'size'>) {
  const [value, setValue] = useState<Tab>('all')
  return <Segmented {...props} label="주문 상태" items={ITEMS} value={value} onChange={setValue} />
}

/** 모바일: 칸이 너비를 똑같이 나눈다. */
export const Fill: Story = {
  render: () => (
    <div className="max-w-sm">
      <Demo />
    </div>
  ),
}

/** 데스크톱 탭: 글자만큼 넓어진다. */
export const Hug: Story = { render: () => <Demo layout="hug" /> }

export const Large: Story = {
  render: () => (
    <div className="max-w-sm">
      <Demo size="l" />
    </div>
  ),
}
