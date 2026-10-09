import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { Button } from '../button/Button'
import { EmptyState } from './EmptyState'

const meta = {
  title: 'Feedback/EmptyState',
  component: EmptyState,
  args: {
    icon: 'Cart',
    title: '장바구니가 비어 있어요',
    description: '관심 있는 대회와 굿즈를 담아 보세요.',
    action: <Button variant="secondary">대회 둘러보기</Button>,
  },
  argTypes: { action: { control: false } },
} satisfies Meta<typeof EmptyState>

export default meta
type Story = StoryObj<typeof meta>

export const EmptyCart: Story = {}

export const NoSearchResult: Story = {
  args: {
    icon: 'Search',
    title: '검색 결과가 없어요',
    description: '다른 이름으로 찾아 보세요.',
    action: undefined,
  },
}

export const TitleOnly: Story = {
  args: { icon: undefined, description: undefined, action: undefined },
}
