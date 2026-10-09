import type { Meta, StoryObj } from '@storybook/nextjs-vite'

import { Button } from '../button/Button'
import { useToast } from './Toast'

// ToastProvider는 .storybook/preview.tsx에서 모든 스토리를 감싼다(앱의 app/_providers와 같음).
function ToastDemo() {
  const showToast = useToast()
  return (
    <div className="flex flex-wrap gap-2">
      <Button variant="secondary" onClick={() => showToast({ message: '장바구니에 담았어요' })}>
        info
      </Button>
      <Button
        variant="secondary"
        onClick={() => showToast({ message: '결제가 완료되었어요', tone: 'success' })}
      >
        success
      </Button>
      <Button
        variant="secondary"
        onClick={() => showToast({ message: '결제에 실패했어요', tone: 'error' })}
      >
        error
      </Button>
      <Button
        variant="secondary"
        onClick={() =>
          showToast({
            message: '찜 목록에서 뺐어요',
            action: { label: '되돌리기', onClick: () => showToast({ message: '다시 담았어요' }) },
          })
        }
      >
        행동 버튼
      </Button>
    </div>
  )
}

const meta = {
  title: 'Feedback/Toast',
  component: ToastDemo,
  parameters: { docs: { story: { inline: false, iframeHeight: 320 } } },
} satisfies Meta<typeof ToastDemo>

export default meta
type Story = StoryObj<typeof meta>

/** 버튼을 누르면 4초 보여 주고 사라진다. */
export const Tones: Story = {}
