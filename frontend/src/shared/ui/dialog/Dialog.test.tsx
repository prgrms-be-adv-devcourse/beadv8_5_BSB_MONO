import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { Dialog } from './Dialog'

const open = { open: true, onOpenChange: () => {}, confirmLabel: '확인', onConfirm: () => {} }

describe('Dialog', () => {
  it('제목과 글 본문은 가운데 정렬한다', () => {
    render(<Dialog {...open} title="취소할까요?" body="되돌릴 수 없어요." />)
    expect(screen.getByText('취소할까요?').className).toMatch(/\btext-center\b/)
    expect(screen.getByText('되돌릴 수 없어요.').className).toMatch(/\btext-center\b/)
  })

  it('본문에 입력 폼을 넣으면 폼과 그 위 어디에도 가운데 정렬이 걸리지 않는다', () => {
    render(<Dialog {...open} title="종목 추가" body={<form aria-label="종목 폼">이름</form>} />)
    let node: HTMLElement | null = screen.getByRole('form', { name: '종목 폼' })
    while (node && node.getAttribute('role') !== 'alertdialog') {
      expect(node.className).not.toMatch(/\btext-center\b/)
      node = node.parentElement
    }
  })
})
