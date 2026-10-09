import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { Badge, type BadgeTone } from './Badge'

const TONES: { tone: BadgeTone; text: string }[] = [
  { tone: 'brand', text: '접수 중' },
  { tone: 'success', text: '결제 완료' },
  { tone: 'warning', text: '마감 임박' },
  { tone: 'neutral', text: '마감' },
  { tone: 'info', text: '결제 대기' },
  { tone: 'danger', text: '실패' },
]

const meta = {
  title: 'Display/Badge',
  component: Badge,
  args: { children: '접수 중', tone: 'brand' },
  argTypes: {
    tone: {
      control: 'select',
      options: [
        'brand',
        'success',
        'warning',
        'danger',
        'info',
        'neutral',
        'accent',
        'live',
        'onHero',
      ],
    },
  },
} satisfies Meta<typeof Badge>

export default meta
type Story = StoryObj<typeof meta>

export const Playground: Story = {}

export const Tones: Story = {
  render: () => (
    <div className="flex flex-wrap gap-2">
      {TONES.map(({ tone, text }) => (
        <Badge key={tone} tone={tone}>
          {text}
        </Badge>
      ))}
    </div>
  ),
}

/** accent·live·onHero는 네이비·코발트 면 위에서 쓴다. */
export const OnHero: Story = {
  render: () => (
    <div className="flex w-fit flex-wrap gap-2 rounded-lg bg-bg-hero p-4">
      <Badge tone="accent">참가 확정</Badge>
      <Badge tone="live">LIVE</Badge>
      <Badge tone="onHero">D-12</Badge>
    </div>
  ),
}
