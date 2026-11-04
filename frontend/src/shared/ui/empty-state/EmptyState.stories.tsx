import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { Button } from '../button/Button'
import { EmptyState } from './EmptyState'

const meta = {
  title: 'Feedback/EmptyState',
  component: EmptyState,
  args: {
    title: '장바구니가 비어 있어요',
    description: '관심 있는 대회와 굿즈를 담아 보세요.',
    action: <Button variant="secondary">대회 둘러보기</Button>,
  },
  decorators: [
    (Story) => (
      <div className="max-w-md rounded-lg bg-bg-surface">
        <Story />
      </div>
    ),
  ],
  argTypes: { action: { control: false } },
} satisfies Meta<typeof EmptyState>

export default meta
type Story = StoryObj<typeof meta>

export const EmptyCart: Story = {}

export const NoSearchResult: Story = {
  args: {
    title: '검색 결과가 없어요',
    description: '다른 이름으로 찾아 보세요.',
    action: undefined,
  },
}

export const LoadFailed: Story = {
  args: {
    role: 'alert',
    title: '구매 단계를 불러오지 못했어요',
    description: '잠시 뒤 다시 시도해 주세요. 다른 영역은 그대로 볼 수 있어요.',
    action: <Button variant="secondary">다시 시도</Button>,
  },
}

export const TitleOnly: Story = {
  args: { description: undefined, action: undefined },
}
