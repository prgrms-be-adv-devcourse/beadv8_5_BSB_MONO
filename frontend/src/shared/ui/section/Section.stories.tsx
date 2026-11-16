import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { Badge } from '../badge/Badge'
import { KeyValue } from '../key-value/KeyValue'
import { Section } from './Section'

const meta = {
  title: 'Layout/Section',
  component: Section,
  args: {
    title: '참가 정보',
    children: (
      <>
        <KeyValue label="대회" value="서울 하프 마라톤" />
        <KeyValue label="코스" value="하프 · 21.0975km" />
      </>
    ),
  },
  decorators: [
    (Story) => (
      <div className="max-w-(--layout-app-max-width)">
        <Story />
      </div>
    ),
  ],
} satisfies Meta<typeof Section>

export default meta
type Story = StoryObj<typeof meta>

export const Playground: Story = {}

/** 제목 오른쪽에 배지나 바꾸기 링크를 둔다. */
export const WithAside: Story = {
  args: { aside: <Badge tone="success">결제 완료</Badge> },
}

export const WithoutTitle: Story = { args: { title: undefined } }
