import type { Meta, StoryObj } from '@storybook/nextjs-vite'
import { useState } from 'react'

import { Button } from '../button/Button'
import { BottomSheet, BottomSheetOption } from './BottomSheet'

const meta = {
  title: 'Overlay/BottomSheet',
  component: BottomSheet,
  parameters: { layout: 'fullscreen' },
} satisfies Meta<typeof BottomSheet>

export default meta
type Story = Omit<StoryObj<typeof meta>, 'args'>

const SIZES = ['S (90)', 'M (95)', 'L (100)', 'XL (105)', '2XL (110)']

function SizeSheet({ withFooter, defaultOpen }: { withFooter?: boolean; defaultOpen?: boolean }) {
  const [open, setOpen] = useState(defaultOpen ?? false)
  const [size, setSize] = useState('M (95)')
  return (
    <div className="p-4">
      <Button onClick={() => setOpen(true)}>사이즈 고르기 · {size}</Button>
      <BottomSheet
        open={open}
        onOpenChange={setOpen}
        title="티셔츠 사이즈"
        footer={
          withFooter ? (
            <Button size="l" onClick={() => setOpen(false)}>
              선택 완료
            </Button>
          ) : undefined
        }
      >
        {SIZES.map((option) => (
          <BottomSheetOption
            key={option}
            selected={size === option}
            disabled={option.startsWith('2XL')}
            onClick={() => {
              setSize(option)
              if (!withFooter) {
                setOpen(false)
              }
            }}
          >
            {option}
          </BottomSheetOption>
        ))}
      </BottomSheet>
    </div>
  )
}

/** 고르면 바로 닫힌다. 아래로 밀어서 닫을 수도 있다. */
export const Options: Story = { render: () => <SizeSheet defaultOpen /> }

/** 하단 버튼으로 확정한다. */
export const WithFooter: Story = { render: () => <SizeSheet withFooter /> }
