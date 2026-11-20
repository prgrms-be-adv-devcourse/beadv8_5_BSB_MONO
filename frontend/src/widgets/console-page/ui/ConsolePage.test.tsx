import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { ConsolePage } from './ConsolePage'

describe('ConsolePage', () => {
  it('backLink와 status를 제목 위에 차례로 그린다', () => {
    render(
      <ConsolePage title="춘천 하프" backLink={<span>대회 관리</span>} status="판매 중">
        <p>본문</p>
      </ConsolePage>,
    )
    const heading = screen.getByRole('heading', { name: '춘천 하프' })
    const link = screen.getByText('대회 관리')
    const status = screen.getByText('판매 중')
    expect(link.compareDocumentPosition(status) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(status.compareDocumentPosition(heading) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })
})
