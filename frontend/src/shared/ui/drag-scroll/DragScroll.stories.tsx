import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { DragScroll } from './DragScroll'

const meta = {
  title: 'Layout/DragScroll',
  component: DragScroll,
} satisfies Meta<typeof DragScroll>

export default meta
type Story = Omit<StoryObj<typeof meta>, 'args'>

const RACES = [
  '서울 하프 마라톤',
  '한강 나이트런 10K',
  '춘천 마라톤',
  '부산 바다 마라톤',
  '제주 트레일런',
]

/** 마우스로 잡고 끌면 넘어가고, 끈 뒤에 손을 떼도 카드가 눌리지 않는다. */
export const Cards: Story = {
  render: () => (
    <div className="max-w-sm">
      <DragScroll className="gap-3">
        {RACES.map((name) => (
          <a
            key={name}
            href="#"
            onClick={(event) => event.preventDefault()}
            className="flex h-32 w-48 shrink-0 snap-start items-end rounded-lg bg-bg-subtle p-4 text-body1-medium text-text-primary"
          >
            {name}
          </a>
        ))}
      </DragScroll>
    </div>
  ),
}
