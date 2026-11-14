import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { Logo } from './Logo'

const meta = {
  title: 'Foundations/Logo',
  component: Logo,
  argTypes: {
    variant: {
      control: 'inline-radio',
      options: ['wordmark', 'wordmarkOnHero', 'navHome', 'symbol', 'wordmarkMuted'],
    },
  },
} satisfies Meta<typeof Logo>

export default meta
type Story = StoryObj<typeof meta>

export const Playground: Story = {}

/** 글자색이 정해진 파일이라 바탕에 맞는 것을 고른다. 네이비 바탕용은 Hero 위에서만 쓴다. */
export const Variants: Story = {
  render: () => (
    <div className="flex flex-wrap items-center gap-6">
      <Logo variant="wordmark" />
      <Logo variant="wordmarkMuted" />
      <Logo variant="navHome" />
      <div className="flex items-center gap-6 rounded-lg bg-bg-hero p-6">
        <Logo variant="wordmarkOnHero" />
        <Logo variant="symbol" />
      </div>
    </div>
  ),
}
