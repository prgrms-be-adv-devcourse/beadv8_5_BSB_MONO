import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { Button } from '../button/Button'
import { BottomCta } from './BottomCta'

const meta = {
  title: 'Layout/BottomCta',
  component: BottomCta,
  parameters: { layout: 'fullscreen' },
  decorators: [
    (Story) => (
      <div className="mx-auto flex h-60 max-w-(--layout-app-max-width) flex-col justify-end">
        <Story />
      </div>
    ),
  ],
  args: { primary: <Button size="l">접수하기</Button> },
  argTypes: { primary: { control: false }, secondary: { control: false } },
} satisfies Meta<typeof BottomCta>

export default meta
type Story = StoryObj<typeof meta>

export const Single: Story = {}

export const Double: Story = {
  args: {
    secondary: (
      <Button size="l" variant="secondary">
        장바구니
      </Button>
    ),
    primary: <Button size="l">바로 구매</Button>,
  },
}

export const Price: Story = {
  args: {
    price: { label: '총 결제 금액', amount: '₩45,000' },
    primary: <Button size="l">결제하기</Button>,
  },
}
