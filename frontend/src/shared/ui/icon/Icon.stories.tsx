import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { Icon, Spinner, type IconName } from './icons'

// icons.tsx의 PATHS 순서. 아이콘을 추가하면 여기에도 적는다.
const NAMES: IconName[] = [
  'ArrowRight',
  'X',
  'Plus',
  'Check',
  'Users',
  'ChevronDown',
  'ChevronUp',
  'ChevronLeft',
  'ChevronRight',
  'Minus',
  'Search',
  'Heart',
  'Share',
  'Menu',
  'Info',
  'CircleCheck',
  'CircleAlert',
  'TriangleAlert',
  'Calendar',
  'MapPin',
  'Cart',
  'User',
  'Bell',
  'Inbox',
  'Home',
  'Dashboard',
  'Package',
  'Orders',
  'Settings',
  'LogOut',
]

const meta = {
  title: 'Foundations/Icon',
  component: Icon,
  args: { name: 'Heart', className: 'size-6 text-text-primary' },
  argTypes: { name: { control: 'select', options: NAMES } },
} satisfies Meta<typeof Icon>

export default meta
type Story = StoryObj<typeof meta>

export const Playground: Story = {}

/** 24 격자, 선 두께 2. 색은 text-* 토큰, 크기는 size-*로 정한다. */
export const Gallery: Story = {
  render: () => (
    <div className="grid grid-cols-[repeat(auto-fill,minmax(96px,1fr))] gap-2">
      {NAMES.map((name) => (
        <div key={name} className="flex flex-col items-center gap-2 rounded-md bg-bg-surface p-3">
          <Icon name={name} className="text-text-primary" />
          <span className="text-caption text-text-tertiary">{name}</span>
        </div>
      ))}
      <div className="flex flex-col items-center gap-2 rounded-md bg-bg-surface p-3">
        <Spinner className="text-text-brand" />
        <span className="text-caption text-text-tertiary">Spinner</span>
      </div>
    </div>
  ),
}
