import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { Select } from './Select'
import { Textarea } from './Textarea'
import { TextField } from './TextField'

const meta = {
  title: 'Inputs/Field',
  decorators: [
    (Story) => (
      <div className="flex max-w-sm flex-col gap-6">
        <Story />
      </div>
    ),
  ],
} satisfies Meta

export default meta
type Story = StoryObj<typeof meta>

export const TextFieldStates: Story = {
  name: 'TextField',
  render: () => (
    <>
      <TextField label="이름" placeholder="홍길동" />
      <TextField
        label="휴대폰 번호"
        defaultValue="010-1234-5678"
        helper="결제 알림을 받을 번호예요."
      />
      <TextField label="이메일" defaultValue="runner@" error="이메일 형식이 아니에요." />
      <TextField label="비활성" defaultValue="수정할 수 없음" disabled />
    </>
  ),
}

export const TextareaStates: Story = {
  name: 'Textarea',
  render: () => (
    <>
      <Textarea label="크루 소개" placeholder="크루를 소개해 주세요" maxLength={200} showCounter />
      <Textarea
        label="요청 사항"
        defaultValue="배송 전에 연락 주세요"
        error="10자 이상 입력해 주세요."
      />
      <Textarea label="비활성" disabled />
    </>
  ),
}

const SIZES = [
  { value: 's', label: 'S (90)' },
  { value: 'm', label: 'M (95)' },
  { value: 'l', label: 'L (100)' },
  { value: 'xl', label: 'XL (105) · 품절', disabled: true },
]

export const SelectStates: Story = {
  name: 'Select',
  render: () => (
    <>
      <Select
        label="티셔츠 사이즈"
        placeholder="사이즈를 골라 주세요"
        items={SIZES}
        helper="기념품 티셔츠예요."
      />
      <Select label="고른 상태" items={SIZES} defaultValue="m" />
      <Select
        label="오류"
        items={SIZES}
        placeholder="사이즈를 골라 주세요"
        error="사이즈를 골라 주세요."
      />
      <Select label="비활성" items={SIZES} defaultValue="l" disabled />
    </>
  ),
}
