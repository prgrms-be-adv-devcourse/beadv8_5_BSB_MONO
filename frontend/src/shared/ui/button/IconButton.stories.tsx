import type { Meta, StoryObj } from '@storybook/nextjs-vite'
import { fn } from 'storybook/test'

import { Icon } from '../icon/icons'
import { IconButton } from './IconButton'

const meta = {
  title: 'Actions/IconButton',
  component: IconButton,
  args: { 'aria-label': '찜하기', children: <Icon name="Heart" />, onClick: fn() },
  argTypes: {
    variant: { control: 'inline-radio', options: ['primary', 'secondary', 'ghost'] },
    size: { control: 'inline-radio', options: ['s', 'm'] },
    children: { control: false },
  },
} satisfies Meta<typeof IconButton>

export default meta
type Story = StoryObj<typeof meta>

export const Playground: Story = {}

export const Matrix: Story = {
  render: (args) => (
    <div className="flex flex-col gap-3">
      {(['m', 's'] as const).map((size) => (
        <div key={size} className="flex items-center gap-3">
          <IconButton {...args} size={size} variant="primary" />
          <IconButton {...args} size={size} variant="secondary" />
          <IconButton {...args} size={size} variant="ghost" />
          <IconButton {...args} size={size} variant="secondary" disabled />
        </div>
      ))}
    </div>
  ),
}
