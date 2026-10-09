import type { Meta, StoryObj } from '@storybook/nextjs-vite'
import { fn } from 'storybook/test'

import { Icon } from '../icon/icons'
import { Button } from './Button'

const meta = {
  title: 'Actions/Button',
  component: Button,
  args: { children: '접수하기', onClick: fn() },
  argTypes: {
    variant: {
      control: 'inline-radio',
      options: ['primary', 'secondary', 'ghost', 'danger', 'accent'],
    },
    size: { control: 'inline-radio', options: ['s', 'm', 'l'] },
  },
} satisfies Meta<typeof Button>

export default meta
type Story = StoryObj<typeof meta>

export const Playground: Story = {}

export const Variants: Story = {
  render: (args) => (
    <div className="flex flex-wrap items-center gap-3">
      <Button {...args} variant="primary">
        Primary
      </Button>
      <Button {...args} variant="secondary">
        Secondary
      </Button>
      <Button {...args} variant="ghost">
        Ghost
      </Button>
      <Button {...args} variant="danger">
        Danger
      </Button>
    </div>
  ),
}

export const Sizes: Story = {
  render: (args) => (
    <div className="flex flex-wrap items-center gap-3">
      <Button {...args} size="s">
        Small
      </Button>
      <Button {...args} size="m">
        Medium
      </Button>
      <Button {...args} size="l">
        Large
      </Button>
    </div>
  ),
}

export const WithIcon: Story = {
  args: {
    children: (
      <>
        <Icon name="Cart" className="size-5" />
        장바구니 담기
      </>
    ),
  },
}

export const Disabled: Story = { args: { disabled: true } }

/** 요청 중에는 색을 유지한 채 Spinner를 붙이고 클릭을 막는다. */
export const Loading: Story = { args: { loading: true, children: '결제 중' } }

/** Accent(라임)는 네이비 Hero 안에서만 쓴다. */
export const AccentOnHero: Story = {
  args: { variant: 'accent', children: '크루로 신청하기' },
  decorators: [
    (Story) => (
      <div className="rounded-lg bg-bg-hero p-6">
        <Story />
      </div>
    ),
  ],
}
