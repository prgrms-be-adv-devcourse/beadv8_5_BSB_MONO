import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { LoadErrorBody, NotFoundBody, OfflineBody } from './StatusPages'

const refresh = vi.fn()
vi.mock('next/navigation', () => ({ useRouter: () => ({ refresh }) }))

describe('상태 화면', () => {
  it('연결 끊김: 문구와 다시 시도하기를 보여 주고, 누르면 화면을 다시 불러온다', async () => {
    render(<OfflineBody />)

    expect(screen.getByText('인터넷에 연결되어 있지 않아요')).toBeInTheDocument()
    expect(screen.getByText('연결을 확인한 뒤 다시 시도해 주세요')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '다시 시도하기' }))
    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('연결 끊김: 다시 시도 동작을 넘기면 그것을 부른다', async () => {
    const onRetry = vi.fn()
    render(<OfflineBody onRetry={onRetry} />)

    await userEvent.click(screen.getByRole('button', { name: '다시 시도하기' }))
    expect(onRetry).toHaveBeenCalledTimes(1)
  })

  it('불러오지 못함: 문구와 다시 시도하기를 보여 주고, 다시 불러오는 동안에는 누를 수 없다', async () => {
    const onRetry = vi.fn()
    const { rerender } = render(<LoadErrorBody onRetry={onRetry} />)

    expect(screen.getByRole('alert')).toHaveTextContent('정보를 불러오지 못했어요')
    expect(
      screen.getByText('연결이 잠시 끊겼어요. 네트워크를 확인한 뒤 다시 시도해 주세요.'),
    ).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '다시 시도하기' }))
    expect(onRetry).toHaveBeenCalledTimes(1)

    rerender(<LoadErrorBody onRetry={onRetry} retrying />)
    expect(screen.getByRole('button', { name: '다시 시도하기' })).toBeDisabled()
  })

  it('찾을 수 없음: 문구와 홈으로 가는 버튼을 보여 준다', () => {
    render(<NotFoundBody />)

    expect(screen.getByText('찾을 수 없는 페이지예요')).toBeInTheDocument()
    expect(screen.getByText('주소가 바뀌었거나 판매가 끝난 대회일 수 있어요.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '홈으로' })).toHaveAttribute('href', '/')
  })
})
