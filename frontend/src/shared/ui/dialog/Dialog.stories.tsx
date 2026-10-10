import type { Meta, StoryObj } from '@storybook/nextjs-vite'
import { useState } from 'react'

import { Button } from '../button/Button'
import { Dialog, type DialogProps } from './Dialog'

const meta = {
  title: 'Overlay/Dialog',
  component: Dialog,
} satisfies Meta<typeof Dialog>

export default meta
type Story = Omit<StoryObj<typeof meta>, 'args'>

function Demo(
  props: Omit<DialogProps, 'open' | 'onOpenChange' | 'onConfirm'> & { defaultOpen?: boolean },
) {
  const { defaultOpen, ...rest } = props
  const [open, setOpen] = useState(defaultOpen ?? false)
  const [confirming, setConfirming] = useState(false)
  return (
    <>
      <Button variant="secondary" onClick={() => setOpen(true)}>
        창 열기
      </Button>
      <Dialog
        {...rest}
        open={open}
        onOpenChange={setOpen}
        confirming={confirming}
        onConfirm={() => {
          // 요청 중 Spinner를 1초 보여 준 뒤 닫는다
          setConfirming(true)
          setTimeout(() => {
            setConfirming(false)
            setOpen(false)
          }, 1000)
        }}
      />
    </>
  )
}

export const Confirm: Story = {
  render: () => (
    <Demo
      defaultOpen
      title="크루 접수를 시작할까요?"
      body="크루원에게 결제 링크가 보내집니다."
      confirmLabel="시작하기"
    />
  ),
}

export const Danger: Story = {
  render: () => (
    <Demo
      type="danger"
      title="접수를 취소할까요?"
      body="취소하면 되돌릴 수 없고, 환불은 3~5일 걸립니다."
      confirmLabel="접수 취소"
      cancelLabel="닫기"
    />
  ),
}

export const AlertType: Story = {
  name: 'Alert',
  render: () => <Demo type="alert" title="결제가 완료되었습니다" confirmLabel="확인" />,
}
