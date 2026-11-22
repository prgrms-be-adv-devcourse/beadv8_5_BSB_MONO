import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { DataTable } from './DataTable'
import { TableCell, TableHeaderCell } from './Table'

// 판매자·관리자 시안: 표 머리 높이 44, 줄 높이 56(화면마다 64도 있다)
describe('Table', () => {
  it('머리 칸 h-11, 내용 칸 h-14', () => {
    render(
      <table>
        <thead>
          <tr>
            <TableHeaderCell>제목</TableHeaderCell>
          </tr>
        </thead>
        <tbody>
          <tr>
            <TableCell>값</TableCell>
          </tr>
        </tbody>
      </table>,
    )
    expect(screen.getByText('제목').className).toMatch(/\bh-11\b/)
    expect(screen.getByText('값').className).toMatch(/\bh-14\b/)
  })

  it('DataTable은 기본 56, rowHeight="lg"면 64', () => {
    const props = {
      label: '표',
      rows: [{ id: 'a', v: '값' }],
      rowKey: (r: { id: string }) => r.id,
      columns: [{ key: 'v', header: '제목', render: (r: { v: string }) => r.v }],
    }
    const { rerender } = render(<DataTable {...props} />)
    expect(screen.getByText('값').className).toMatch(/\bh-14\b/)
    rerender(<DataTable {...props} rowHeight="lg" />)
    expect(screen.getByText('값').className).toMatch(/\bh-16\b/)
  })
})
