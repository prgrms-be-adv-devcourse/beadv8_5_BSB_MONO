import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { Badge } from '../badge/Badge'
import { ProgressBar } from '../progress-bar/ProgressBar'
import { BibCard } from './BibCard'

const meta = {
  title: 'Display/BibCard',
  component: BibCard,
  decorators: [
    (Story) => (
      <div className="max-w-sm">
        <Story />
      </div>
    ),
  ],
} satisfies Meta<typeof BibCard>

export default meta
type Story = StoryObj<typeof meta>

/** 내 참가권. 코발트 띠, 번호 앞에 No. */
export const Ticket: Story = {
  args: {
    type: 'ticket',
    eyebrow: 'SEOUL MARATHON 2026',
    status: <Badge tone="live">참가 확정</Badge>,
    unit: 'No.',
    number: '21047',
    title: '하프 코스 · 21.0975km',
    meta: '2026.11.15 (일) 07:00 · 광화문 광장',
  },
}

/** 크루 참가 현황. 네이비 띠, 진행 막대 */
export const Crew: Story = {
  args: {
    type: 'crew',
    eyebrow: 'CREW · 한강러닝크루',
    status: <Badge tone="live">결제 중</Badge>,
    number: '5',
    unit: '/ 8명',
    title: '크루원 결제 현황',
    progress: <ProgressBar size="s" value={62.5} />,
    meta: '마감까지 3일 남았습니다',
  },
}
