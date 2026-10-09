import type { Meta, StoryObj } from '@storybook/nextjs-vite'
import { fn } from 'storybook/test'

import { Alert } from './Alert'

const meta = {
  title: 'Feedback/Alert',
  component: Alert,
  args: {
    tone: 'info',
    title: '접수 기간은 10월 31일까지입니다',
    description: '크루원 전원이 결제를 마쳐야 단체 접수가 확정됩니다.',
  },
  argTypes: {
    tone: { control: 'inline-radio', options: ['info', 'success', 'warning', 'danger'] },
  },
} satisfies Meta<typeof Alert>

export default meta
type Story = StoryObj<typeof meta>

export const Playground: Story = {}

export const Tones: Story = {
  render: (args) => (
    <div className="flex max-w-md flex-col gap-3">
      <Alert {...args} tone="info" />
      <Alert {...args} tone="success" title="결제가 완료되었습니다" />
      <Alert {...args} tone="warning" title="마감까지 2일 남았습니다" />
      <Alert {...args} tone="danger" title="결제에 실패했습니다" />
    </div>
  ),
}

/** onClose를 넘기면 닫기 버튼이 보인다. */
export const Closable: Story = { args: { onClose: fn() } }

export const TitleOnly: Story = { args: { description: undefined } }
