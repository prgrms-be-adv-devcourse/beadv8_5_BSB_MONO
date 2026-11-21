import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import { ToastProvider } from './Toast'
import { useToast } from './use-toast'

function Trigger() {
  const showToast = useToast()
  return <button onClick={() => showToast({ message: '저장했어요' })}>띄우기</button>
}

describe('ToastProvider', () => {
  // 시안: 위 가운데, 폭 343
  it('태블릿 이상에서는 위 가운데에 폭 343으로 뜬다', async () => {
    render(
      <ToastProvider>
        <Trigger />
      </ToastProvider>,
    )
    await userEvent.click(screen.getByRole('button', { name: '띄우기' }))
    const viewport = (await screen.findByText('저장했어요')).closest(
      '[role="region"], div[tabindex]',
    )
    const classes = document.body.innerHTML.match(/class="[^"]*tablet:w-\[343px\][^"]*"/)?.[0] ?? ''
    expect(viewport).not.toBeNull()
    expect(classes).toMatch(/tablet:top-6/)
    expect(classes).toMatch(/tablet:left-1\/2/)
    // 판매자·관리자 화면은 사이드바를 뺀 본문 가운데로 옮긴다(globals.css가 --toast-shift를 정한다)
    expect(classes).toMatch(/--toast-shift/)
    expect(classes).not.toMatch(/tablet:right-6|tablet:bottom-6/)
  })
})
