import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { Checkbox } from './Checkbox'
import { Radio, RadioGroup } from './Radio'
import { Switch } from './Switch'

const meta = {
  title: 'Inputs/Choice',
  parameters: {
    docs: { description: { component: 'Checkbox·Radio·Switch. 눌러서 상태가 바뀌는지 확인한다.' } },
  },
} satisfies Meta

export default meta
type Story = StoryObj<typeof meta>

export const CheckboxStates: Story = {
  name: 'Checkbox',
  render: () => (
    <div className="flex flex-col gap-3">
      <Checkbox label="기본" />
      <Checkbox label="체크됨" defaultChecked />
      <Checkbox label="일부 선택(indeterminate)" indeterminate />
      <Checkbox label="비활성" disabled />
      <Checkbox label="체크·비활성" defaultChecked disabled />
    </div>
  ),
}

export const RadioStates: Story = {
  name: 'Radio',
  render: () => (
    <RadioGroup defaultValue="card">
      <Radio value="card" label="신용·체크카드" />
      <Radio value="transfer" label="계좌이체" />
      <Radio value="phone" label="휴대폰 결제(점검 중)" disabled />
    </RadioGroup>
  ),
}

export const SwitchStates: Story = {
  name: 'Switch',
  render: () => (
    <div className="flex flex-col items-start gap-3">
      <Switch label="알림 받기" />
      <Switch label="크루 공개" defaultChecked />
      <Switch label="비활성" disabled />
      <Switch label="켜짐·비활성" defaultChecked disabled />
    </div>
  ),
}
