import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { Avatar, AvatarGroup } from './Avatar'

const meta = {
  title: 'Display/Avatar',
  component: Avatar,
  args: { name: '김러너', size: 'm' },
  argTypes: { size: { control: 'inline-radio', options: ['s', 'm', 'l', 'xl'] } },
} satisfies Meta<typeof Avatar>

export default meta
type Story = StoryObj<typeof meta>

export const Playground: Story = {}

/** 사진을 못 불러오면 첫 글자, 이름도 없으면 사람 아이콘으로 대체한다. */
export const Fallbacks: Story = {
  render: () => (
    <div className="flex items-center gap-4">
      <Avatar size="l" name="깨진주소" src="/no-such-image.png" />
      <Avatar size="l" name="김러너" />
      <Avatar size="l" />
    </div>
  ),
}

export const Sizes: Story = {
  render: () => (
    <div className="flex items-end gap-4">
      {(['s', 'm', 'l', 'xl'] as const).map((size) => (
        <Avatar key={size} size={size} name="김러너" showStatus />
      ))}
    </div>
  ),
}

/** 4명까지 보여 주고 나머지는 +N */
export const Group: Story = {
  render: () => (
    <div className="flex flex-col gap-3">
      <AvatarGroup people={[{ name: '김' }, { name: '이' }, { name: '박' }]} />
      <AvatarGroup
        size="m"
        people={[
          { name: '김' },
          { name: '이' },
          { name: '박' },
          { name: '최' },
          { name: '정' },
          {},
          {},
        ]}
      />
    </div>
  ),
}
