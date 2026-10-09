import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { Badge } from '../badge/Badge'
import { Button } from '../button/Button'
import { ProgressBar } from '../progress-bar/ProgressBar'
import { StatTile } from '../stat-tile/StatTile'
import { Hero } from './Hero'

const meta = {
  title: 'Layout/Hero',
  component: Hero,
  parameters: { layout: 'fullscreen' },
  argTypes: { badges: { control: false }, children: { control: false } },
} satisfies Meta<typeof Hero>

export default meta
type Story = StoryObj<typeof meta>

/** 대회 상세 맨 위. 큰 제목 + 뒤에 옅은 워터마크 */
export const Race: Story = {
  args: {
    type: 'race',
    eyebrow: 'SEOUL · 2026.11.15 SUN',
    title: '서울 하프 마라톤',
    subtitle: '광화문 출발 · 여의도 도착',
    watermark: '21K',
    badges: (
      <>
        <Badge tone="live">접수 중</Badge>
        <Badge tone="onHero">D-37</Badge>
      </>
    ),
    children: (
      <>
        <ProgressBar tone="onHero" label="접수율" valueText="72%" value={72} />
        <div>
          <Button variant="accent" size="l">
            크루로 신청하기
          </Button>
        </div>
      </>
    ),
  },
}

/** 크루 참가 현황. 작은 제목 + 숫자 타일 */
export const Crew: Story = {
  args: {
    type: 'crew',
    eyebrow: 'CREW STATUS',
    title: '한강러닝크루',
    subtitle: '서울 하프 마라톤 · 단체 접수',
    badges: <Badge tone="accent">할인 조건 달성</Badge>,
    children: (
      <>
        <ProgressBar tone="onHero" label="결제 완료" valueText="5/8명" value={62.5} />
        <div className="grid grid-cols-2 gap-2">
          <StatTile tone="hero" label="결제 완료" value="5" unit="명" />
          <StatTile tone="hero" label="남은 시간" value="3" unit="일" caption="11.01 마감" />
        </div>
      </>
    ),
  },
}
