import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { KeyValue } from './KeyValue'

const meta = {
  title: 'Display/KeyValue',
  component: KeyValue,
  args: { label: '대회일', value: '2026년 11월 22일 (일)' },
  decorators: [
    (Story) => (
      <div className="max-w-sm">
        <Story />
      </div>
    ),
  ],
} satisfies Meta<typeof KeyValue>

export default meta
type Story = StoryObj<typeof meta>

export const Playground: Story = {}

/** 고칠 수 없는 값을 여러 줄로 보여 줄 때 */
export const List: Story = {
  render: () => (
    <div className="flex flex-col gap-3">
      <KeyValue label="대회" value="서울 하프 마라톤" />
      <KeyValue label="코스" value="하프 · 21.0975km" />
      <KeyValue label="결제 금액" value="45,000원" />
      <KeyValue label="배송지" value="서울특별시 종로구 세종대로 172, 광화문 러너스 빌딩 3층" />
    </div>
  ),
}
